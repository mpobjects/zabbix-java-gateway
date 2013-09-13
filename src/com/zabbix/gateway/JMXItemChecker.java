/*
** Zabbix
** Copyright (C) 2000-2011 Zabbix SIA
**
** This program is free software; you can redistribute it and/or modify
** it under the terms of the GNU General Public License as published by
** the Free Software Foundation; either version 2 of the License, or
** (at your option) any later version.
**
** This program is distributed in the hope that it will be useful,
** but WITHOUT ANY WARRANTY; without even the implied warranty of
** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
** GNU General Public License for more details.
**
** You should have received a copy of the GNU General Public License
** along with this program; if not, write to the Free Software
** Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
**/

package com.zabbix.gateway;

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanAttributeInfo;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.json.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Timer;
import com.zabbix.security.SecurityUtils;

class JMXItemChecker extends ItemChecker
{
	private static final Logger logger = LoggerFactory.getLogger(JMXItemChecker.class);
	
	// Timer to track time spent making remote requests to the remote JVM
    private static final Timer _requestTime = Metrics.newTimer(JMXItemChecker.class, "remote-request-time", TimeUnit.MILLISECONDS, TimeUnit.MINUTES);

	private final JMXServiceURL url;
	private JMXConnector jmxc;
	private TimedMBeanServerConnection mbsc;

	private final String username;
	private final String password;

	protected JMXItemChecker(JSONObject request, JmxConfiguration config) throws ZabbixException {
		this(request, config, null);
	}
	
	protected JMXItemChecker(JSONObject request, JmxConfiguration config,
			SecurityUtils securityUtils) throws ZabbixException {
		super(request);

		try
		{
			url = new JMXServiceURL(config.getUrl());
			
			jmxc = null;
			mbsc = null;

			username = request.optString(JSON_TAG_USERNAME, null);
			if (securityUtils != null) {
            	password = securityUtils.decrypt(request.optString(JSON_TAG_PASSWORD, null));
            }
			else {
				password = request.optString(JSON_TAG_PASSWORD, null);
			}

			if (null != username && null == password || null == username && null != password)
				throw new IllegalArgumentException("invalid username and password nullness combination");
		}
		catch (Exception e)
		{
			throw new ZabbixException(e);
		}
	}

	@Override
	public JSONArray getValues() throws ZabbixException
	{
		JSONArray values = new JSONArray();

		try
		{
			HashMap<String, String[]> env = null;

			if (null != username && null != password)
			{
				env = new HashMap<String, String[]>();
				env.put(JMXConnector.CREDENTIALS, new String[] {username, password});
			}

			logger.debug("connecting to JMX agent at {}", url);
			jmxc = JMXConnectorFactory.connect(url, env);
			// Wrap the MBeanServerConnection so we can track the performance
			mbsc = new TimedMBeanServerConnection(jmxc.getMBeanServerConnection());

			for (String key : keys) {
				values.put(getJSONValue(key));
			}
			
			_requestTime.update(mbsc.getTotalNetworkTime(), TimeUnit.NANOSECONDS);
		}
		catch (Exception e)
		{
			throw new ZabbixException(e);
		}
		finally
		{
			try { if (null != jmxc) jmxc.close(); } catch (java.io.IOException exception) { }

			jmxc = null;
			mbsc = null;
		}

		return values;
	}

	@Override
	protected String getStringValue(String key) throws Exception
	{
		ZabbixItem item = new ZabbixItem(key);

		if (item.getKeyId().equals("jmx"))
		{
			if (2 != item.getArgumentCount())
				throw new ZabbixException("required key format: jmx[<object name>,<attribute name>]");

			ObjectName objectName = new ObjectName(item.getArgument(1));
			String attributeName = item.getArgument(2);
			String realAttributeName;
			String fieldNames = "";
			int sep;

			//
			// Attribute name and composite data field names are separated by dots. On the other hand the
			// name may contain a dot too. In this case user needs to escape it with a backslash. Also the
			// backslash symbols in the name must be escaped. So a real separator is unescaped dot and
			// separatorIndex() is used to locate it.
			//

			sep = HelperFunctionChest.separatorIndex(attributeName);

			if (-1 != sep)
			{
				logger.trace("'{}' contains composite data", attributeName);

				realAttributeName = attributeName.substring(0, sep);
				fieldNames = attributeName.substring(sep + 1);
			}
			else
				realAttributeName = attributeName;

			// unescape possible dots or backslashes that were escaped by user
			realAttributeName = HelperFunctionChest.unescapeUserInput(realAttributeName);

			logger.trace("attributeName:'{}'", realAttributeName);
			logger.trace("fieldNames:'{}'", fieldNames);

			return getPrimitiveAttributeValue(mbsc.getAttribute(objectName, realAttributeName), fieldNames);
		}
		else if (item.getKeyId().equals("jmx.discovery"))
		{
			if (item.getArgumentCount() > 1)
				throw new ZabbixException("required key format: jmx.discovery or jmx.discovery[<ObjectNameWildcard>]");

			JSONArray counters = new JSONArray();

			if (item.getArgumentCount() == 0) {
				for (ObjectName name : mbsc.queryNames(null, null))
				{
					logger.trace("discovered object '{}'", name);
		
					for (MBeanAttributeInfo attrInfo : mbsc.getMBeanInfo(name).getAttributes())
					{
						logger.trace("discovered attribute '{}'", attrInfo.getName());
		
						if (!attrInfo.isReadable())
						{
							logger.trace("attribute not readable, skipping");
							continue;
						}
		
						try
						{
							logger.trace("looking for attributes of primitive types");
							String descr = (attrInfo.getName().equals(attrInfo.getDescription()) ? null : attrInfo.getDescription());
							findPrimitiveAttributes(counters, name, descr, attrInfo.getName(), mbsc.getAttribute(name, attrInfo.getName()));
						}
						catch (Exception e)
						{
							Object[] logInfo = {name, attrInfo.getName(), e};
							logger.trace("processing '{},{}' failed", logInfo);
						}
					}
				}
			}
			else {
				String objNameWildcard = item.getArgument(1);
				Set<ObjectName> objectNames = mbsc.queryNames(new ObjectName(objNameWildcard), null);
				buildDiscoveryOutput(counters, objectNames);
			}

			JSONObject mapping = new JSONObject();
			mapping.put(ItemChecker.JSON_TAG_DATA, counters);
			return mapping.toString();
		}
		else
			throw new ZabbixException("key ID '%s' is not supported", item.getKeyId());
	}

	private String getPrimitiveAttributeValue(Object dataObject, String fieldNames) throws ZabbixException
	{
		logger.trace("drilling down with data object '{}' and field names '{}'", dataObject, fieldNames);

		if (null == dataObject)
			throw new ZabbixException("data object is null");

		if (fieldNames.equals(""))
		{
			if (isPrimitiveAttributeType(dataObject.getClass())) {
				String value = dataObject.toString();
				// Zabbix doesn't like scientific notation so see if
				// we need to translate it back to numeric.
				if (HelperFunctionChest.isScientificNotation(value)) {
					value = HelperFunctionChest.scientificToPlain(value);
				}
				return value;
			}
			else if (dataObject.getClass().isArray()) {
				return handleArray(dataObject);
			}
			else
				throw new ZabbixException("data object type is not primitive: %s" + dataObject.getClass());
		}

		if (dataObject instanceof CompositeData)
		{
			logger.trace("'{}' contains composite data", dataObject);

			CompositeData comp = (CompositeData)dataObject;

			String dataObjectName;
			String newFieldNames = "";

			int sep = HelperFunctionChest.separatorIndex(fieldNames);

			if (-1 != sep)
			{
				dataObjectName = fieldNames.substring(0, sep);
				newFieldNames = fieldNames.substring(sep + 1);
			}
			else
				dataObjectName = fieldNames;

			// unescape possible dots or backslashes that were escaped by user
			dataObjectName = HelperFunctionChest.unescapeUserInput(dataObjectName);

			return getPrimitiveAttributeValue(comp.get(dataObjectName), newFieldNames);
		}
		else
			throw new ZabbixException("unsupported data object type along the path: %s", dataObject.getClass());
	}

	private void findPrimitiveAttributes(JSONArray counters, ObjectName name, String descr, String attrPath, Object attribute) throws JSONException
	{
		logger.trace("drilling down with attribute path '{}'", attrPath);

		if (isPrimitiveAttributeType(attribute.getClass()))
		{
			logger.trace("found attribute of a primitive type: {}", attribute.getClass());

			JSONObject counter = new JSONObject();

			counter.put("{#JMXDESC}", null == descr ? name + "," + attrPath : descr);
			counter.put("{#JMXOBJ}", name);
			counter.put("{#JMXATTR}", attrPath);
			counter.put("{#JMXTYPE}", attribute.getClass().getName());
			counter.put("{#JMXVALUE}", attribute.toString());

			counters.put(counter);
		}
		else if (attribute instanceof CompositeData)
		{
			logger.trace("found attribute of a composite type: {}", attribute.getClass());

			CompositeData comp = (CompositeData)attribute;

			for (String key : comp.getCompositeType().keySet())
				findPrimitiveAttributes(counters, name, descr, attrPath + "." + key, comp.get(key));
		}
		else if (attribute instanceof TabularDataSupport || attribute.getClass().isArray())
		{
			logger.trace("found attribute of a known, unsupported type: {}", attribute.getClass());
		}
		else
			logger.trace("found attribute of an unknown, unsupported type: {}", attribute.getClass());
	}

	private boolean isPrimitiveAttributeType(Class<?> clazz)
	{
		Class<?>[] clazzez = {Boolean.class, Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class, String.class};

		return HelperFunctionChest.arrayContains(clazzez, clazz);
	}
	
	private void buildDiscoveryOutput(JSONArray counters, Set<ObjectName> objectNames) throws JSONException {
		for (ObjectName objName : objectNames) {
        	// Add the full JMX Object Name as a macro
        	// in the return string
            JSONObject taskObj = new JSONObject();
            taskObj.put("{#JMXOBJ}", objName.getCanonicalName());
            
        	// Add each property of the Object Name as returned macros
			Hashtable<String, String> props = objName.getKeyPropertyList();
			for (Map.Entry<String, String> propEntry : props.entrySet()) {
				taskObj.put(String.format("{#%s}", propEntry.getKey().toUpperCase()),
						propEntry.getValue());
			}
            counters.put(taskObj);
        }
	}
	
	private String handleArray(Object dataArray) {
		StringBuilder builder = new StringBuilder();
		
		Object[] castArray;
		if (dataArray.getClass().getComponentType().isPrimitive()) {
			int count = Array.getLength(dataArray);
			castArray = new Object[count];

		    for(int i = 0; i < count; i++){
		        castArray[i] = Array.get(dataArray, i);          
		    }   
		}
		else {
			castArray = (Object[]) dataArray;
		}
		
		if (castArray.length == 0) return "";

        for (Object entry : castArray) {
        	if (entry != null) {
                builder.append(String.valueOf(entry)).append("\n");
        	}
        }
        
        // Remove the last newline
        return builder.substring(0, builder.length() - 1);
    }
}
