/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package com.zabbix.gateway;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.client.J4pClient;
import org.jolokia.client.J4pClientBuilder;
import org.jolokia.client.exception.J4pBulkRemoteException;
import org.jolokia.client.exception.J4pException;
import org.jolokia.client.exception.J4pRemoteException;
import org.jolokia.client.request.J4pExecRequest;
import org.jolokia.client.request.J4pExecResponse;
import org.jolokia.client.request.J4pReadRequest;
import org.jolokia.client.request.J4pReadResponse;
import org.jolokia.client.request.J4pRequest;
import org.jolokia.client.request.J4pResponse;
import org.jolokia.client.request.J4pSearchRequest;
import org.jolokia.client.request.J4pSearchResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Histogram;
import com.yammer.metrics.core.Timer;
import com.yammer.metrics.core.TimerContext;

public class JolokiaChecker extends ItemChecker {

    private static final String JMX_OPERATION = "jmx.operation";

    private static final String JMX_READ = "jmx";

    private static final Logger logger = LoggerFactory
        .getLogger(JolokiaChecker.class);

    private J4pClient _j4pClient;

    private Map<String, String> _foundKeys = new HashMap<String, String>();
    private Map<String, String> _errorKeys = new HashMap<String, String>();
    
    // Performance Metrics
    private static final Histogram _requestSizes = Metrics.newHistogram(JolokiaChecker.class, "request-sizes");
    private static final Timer _requestTime = Metrics.newTimer(JolokiaChecker.class, "request-time", TimeUnit.MILLISECONDS, TimeUnit.MINUTES);

    protected JolokiaChecker(JSONObject request) throws ZabbixException {
        this(request, null);
    }
    
    protected JolokiaChecker(JSONObject request, JmxConfiguration config) throws ZabbixException {
    	super(request);

        try {
            String conn = request.getString(JSON_TAG_CONN);
            int port = request.getInt(JSON_TAG_PORT);

            String username = request.optString(JSON_TAG_USERNAME, null);
            String password = request.optString(JSON_TAG_PASSWORD, null);

            if (null != username && null == password || null == username
                    && null != password)
                throw new IllegalArgumentException(
                    "invalid username and password nullness combination");

            String jolokiaUrl = null;
            if (config == null) {
            	jolokiaUrl = buildJolokiaUrl(JmxConfigurationManager.getConfig(conn, port));
            }
            else {
            	jolokiaUrl = buildJolokiaUrl(config);
            }

            logger.debug("Jolokia URL is: " + jolokiaUrl);
            J4pClientBuilder builder = J4pClient.url(jolokiaUrl)
                .connectionTimeout(5000);

            if (null != username) {
                builder.user(username).password(password);
            }

            _j4pClient = builder.build();
        }
        catch (Exception e) {
            throw new ZabbixException(e);
        }
    }

    @Override
    public JSONArray getValues() throws ZabbixException {
        JSONArray values = new JSONArray();
        try {
            jolokiaRead();

            for (String key : keys)
                values.put(getJSONValue(key));
        }
        catch (Exception e) {
            throw new ZabbixException(e);
        }

        return values;
    }

    @Override
    protected String getStringValue(String key) throws Exception {
        String value = _foundKeys.get(key);
        if (value != null) return value;

        value = _errorKeys.get(key);
        if (value != null) throw new ZabbixException(value);

        throw new ZabbixException("Invalid configuration: %s", key);
    }

    @SuppressWarnings("unchecked")
    private void jolokiaRead() throws J4pException {
        _requestSizes.update(keys.size());
        List<J4pRequest> allRequests = new ArrayList<J4pRequest>();
        List<ZabbixItem> standardRequestKeys = new ArrayList<ZabbixItem>();
        List<J4pReadRequest> compositeRequests = new ArrayList<J4pReadRequest>();
        Map<String, ArrayList<ZabbixItem>> compositeReads = new LinkedHashMap<String, ArrayList<ZabbixItem>>();

        for (String key : keys) {
            ZabbixItem item = new ZabbixItem(key);
            try {
                if (item.getKeyId().equals(JMX_READ)) {

                    buildReadRequest(item, allRequests, standardRequestKeys,
                        compositeRequests, compositeReads);
                }
                else if (item.getKeyId().equals(JMX_OPERATION)) {
                    buildOperationRequest(item, allRequests,
                        standardRequestKeys);
                }
                else if (item.getKeyId().equals("jmx.discovery") && item.getArgumentCount() == 1) {
                	J4pSearchRequest sRequest = new J4pSearchRequest(item.getArgument(1));
                	allRequests.add(sRequest);
                	standardRequestKeys.add(item);
                }
                else {
                    _errorKeys.put(
                        key,
                        String.format("key ID '%s' is not supported",
                            item.getKeyId()));
                }
            }
            catch (MalformedObjectNameException e) {
                _errorKeys.put(item.getKey(), e.toString());
            }

        }

        allRequests.addAll(compositeRequests);

        if (allRequests.size() > 0) {
            List<J4pResponse<J4pRequest>> responseList = null;
            
            // Only include times that DO NOT timeout due
            // to the server being down. Also, a J4pBulkRemoteException
            // occurs when an exception happens when retrieving one of the
            // values in the bulk request (the return results will contain
            // a mix of successful J4pResponses and errors represented as
            // J4pRemoteExceptions).
            TimerContext context = _requestTime.time();
            try {
                responseList = _j4pClient.execute(allRequests);
            }
            catch (J4pBulkRemoteException ex) {
                // Suppressing this for now but this seems to be a limitation
                // of the API when extracting the results from the exception
                // as the List is not typed and in this case will contain
                // both J4pResponses and J4pRemoteExceptions
                responseList = ex.getResults();
            }
            context.stop();

            int responseIndex = 0;
            for (; responseIndex < standardRequestKeys.size(); responseIndex++) {
                String key = standardRequestKeys.get(responseIndex).getKey();
                Object response = responseList.get(responseIndex);
                if (response instanceof J4pReadResponse || response instanceof J4pExecResponse) {
                    Object value = responseList.get(responseIndex).getValue();
                    _foundKeys.put(key, getValueToString(value));
                }
                else if (response instanceof J4pRemoteException){
                    J4pRemoteException exception = (J4pRemoteException) response;
                    _errorKeys.put(key, exception.getMessage());
                }
                else {
                	J4pSearchResponse sResponse = (J4pSearchResponse) response;
					try {
						String discoveryOutput = buildDiscoveryOutput(sResponse.getMBeanNames());
						_foundKeys.put(key, discoveryOutput);
					} catch (JSONException e) {
						_errorKeys.put(key, e.getMessage());
					}
                	
                }
            }

            for (Map.Entry<String, ArrayList<ZabbixItem>> entry : compositeReads.entrySet()) {
                String firstAttribute = entry.getValue().get(0).getArgument(2);
                int dot = firstAttribute.indexOf(".");
                Object response = responseList.get(responseIndex);
                org.json.simple.JSONObject compositeObj = null;
                J4pException ex = null;
                if (response instanceof J4pResponse) {
                    compositeObj = responseList.get(responseIndex).getValue();
                }
                else {
                    ex = (J4pException) response;
                }

                for (ZabbixItem item : entry.getValue()) {
                    if (compositeObj == null) {
                        _errorKeys.put(item.getKey(), ex.getMessage());
                        continue;
                    }

                    String subAttr = item.getArgument(2).substring(dot + 1);
                    // TODO: need recursive part here to find subkeys
                    Object value = compositeObj.get(subAttr);
                    if (value == null) {
                        _errorKeys.put(item.getKey(), String.format(
                            "Argument key=\"%s\" is not an existing item " +
                            "name for this CompositeData instance.",
                            subAttr));
                    }
                    else {
                        _foundKeys.put(item.getKey(), getValueToString(value));
                    }
                }

                responseIndex++;
            }
        }
    }
    
    private String getValueToString(Object value) {
        String result = null;
        // Special array handling
        if (value instanceof org.json.simple.JSONArray) {
            result = handleArray((org.json.simple.JSONArray) value);
        }
        else {
            result = value != null ? value.toString() : "";
            // Special handling if the value is a
            // Double/Floating point in scientific notation
            // This can be removed once Zabbix is fixed to properly
            // handle decimals in scientific notation.
            if (HelperFunctionChest.isScientificNotation(result)) {
                result = HelperFunctionChest.scientificToPlain(result);
            }
        }
        
        return result;
    }

    private void buildReadRequest(ZabbixItem item,
                                  List<J4pRequest> allRequests,
                                  List<ZabbixItem> standardRequestKeys,
                                  List<J4pReadRequest> compositeRequests,
                                  Map<String, ArrayList<ZabbixItem>> compositeReads)
            throws MalformedObjectNameException {
        if (item.getArgumentCount() != 2) {
            _errorKeys.put(item.getKey(), "required key format: jmx[<object name>,<attribute name>]");
            return;
        }
        
        String attribute = item.getArgument(2);
        int dot = attribute.indexOf(".");
        if (dot != -1) {
        	// Handle composite reads here
            attribute = attribute.substring(0, dot);
            String uniqueKey = item.getArgument(1) + "||" + attribute;
            if (compositeReads.containsKey(uniqueKey)) {
                List<ZabbixItem> subAttributes = compositeReads
                    .get(uniqueKey);
                subAttributes.add(item);
            }
            else {
                ArrayList<ZabbixItem> subItems = new ArrayList<ZabbixItem>();
                subItems.add(item);
                compositeReads.put(uniqueKey, subItems);
                J4pReadRequest request = new J4pReadRequest(
                    item.getArgument(1), attribute);
                compositeRequests.add(request);
            }
        }
        else {
        	// Standard read here
            allRequests.add(new J4pReadRequest(item.getArgument(1), item
                .getArgument(2)));
            standardRequestKeys.add(item);
        }
    }

    private void buildOperationRequest(ZabbixItem item,
                                       List<J4pRequest> allRequests,
                                       List<ZabbixItem> standardRequestKeys)
            throws MalformedObjectNameException {

        String function = null;
        String params[] = null;
        // Arguments inline with no method signature
        if (item.getArgumentCount() == 2) {
            String arg2 = item.getArgument(2);
            int paramsStart = arg2.indexOf("(");
            // Parentheses not found so we'll
            // treat this as an empty method call
            if (paramsStart == -1) {
                function = arg2;
            }
            else {
                // Parse out the function up to the parentheses
                // Get the comma separated arguments from the remaining values
                function = arg2.substring(0, paramsStart);
                String args = arg2.substring(paramsStart + 1, arg2.indexOf(")"));
                
                // Possible the arguments are left empty
                if (!args.isEmpty()) {
                    params = args.split(",");
                    trimArray(params);
                }
            }
        }
        // Arguments with method signature
        else if (item.getArgumentCount() == 3) {
            function = item.getArgument(2);
            params = item.getArgument(3).split(",");
            trimArray(params);  
        }
        // Invalid arguments
        else {
            _errorKeys.put(item.getKey(), "Operation name must be specified");
        }
        
        J4pExecRequest exec = null;
        if (params == null) {
            exec = new J4pExecRequest(item.getArgument(1), function);
        }
        else {
            exec = new J4pExecRequest(item.getArgument(1), function,
                (Object[]) params);
        }
        
        allRequests.add(exec);
        standardRequestKeys.add(item);
    }

    // Trims the whitespace for all entries of the array
    private void trimArray(String[] params) {
        for (int i = 0; i < params.length; i++) {
            params[i] = params[i].trim();
        }
    }

    // Handles array attributes by concatenating them together
    // between newlines
    private String handleArray(org.json.simple.JSONArray jsonArray) {
        if (jsonArray.size() == 0) return "";

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < jsonArray.size(); i++) {
            builder.append(jsonArray.get(i)).append("\n");
        }

        // Remove the last newline
        return builder.substring(0, builder.length() - 1);
    }

    private String buildJolokiaUrl(JmxConfiguration config) {
        String url = config.getUrl();

        // Having issues with Jolokia if the endpoint doesn't have an ending
        // slash
        if (!url.endsWith("/")) {
            url += "/";
        }

        return url;
    }
    
    private String buildDiscoveryOutput(List<String> foundMbeans) throws JSONException {
    	JSONObject jsonObj = new JSONObject();
        JSONArray jsonArray = new JSONArray();

        String output;
        try {
	        for (String objNameString : foundMbeans) {
	        	// Add the full JMX Object Name as a macro
	        	// in the return string
	            JSONObject taskObj = new JSONObject();
	            taskObj.put("{#JMXOBJ}", objNameString);
	            
	            try {
	            	// Add each property of the Object Name as returned macros
					ObjectName objName = new ObjectName(objNameString);
					Hashtable<String, String> props = objName.getKeyPropertyList();
					for (Map.Entry<String, String> propEntry : props.entrySet()) {
						taskObj.put(String.format("{#%s}", propEntry.getKey().toUpperCase()),
								propEntry.getValue());
					}
				} 
	            catch (MalformedObjectNameException e) {
					// This will never happen
	            	logger.error(e.getMessage());
				} 
	            jsonArray.put(taskObj);
	        }
	        
	        jsonObj.put("data", jsonArray);
	        output = jsonObj.toString();
        }
        catch (JSONException e) {
        	logger.warn("JSON error while building discovery output", e);
        	throw e;
        }

        return output;
    }
}
