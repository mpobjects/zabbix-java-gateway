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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the JMX Configuration data for a host.
 * This is defined via Host level macros to specify the
 * JMX protocol and endpoint to be used. By default the protcol
 * is - service:jmx:rmi:///jndi/rmi - and the default JMX
 * endpoint is - /jmxrmi
 * @author Ryan Rupp
 *
 */
public class JmxConfiguration {
	
	public static final String DEFAULT_JMX_PROTOCOL = "service:jmx:rmi:///jndi/rmi";
    public static final String DEFAULT_JMX_ENDPOINT = "/jmxrmi";
    public static final String JMX_SHORTHAND = "jmx";

    final static String MACRO_JMX_PROTOCOL = "{$JMX_PROTOCOL}";
    final static String MACRO_JMX_ENDPOINT = "{$JMX_ENDPOINT}";

    private static final Logger logger = LoggerFactory
        .getLogger(JmxConfiguration.class);
    private static final ConcurrentMap<String, JmxConfiguration> _jmxConfigs = new ConcurrentHashMap<String, JmxConfiguration>();

    private static final long STALE_THRESHOLD = 1000 * 60 * 5; // 5 minute
                                                               // threshold
    private static final ZabbixApi _zabbixApi;
    
    // Instance variables
    private final String _protocol;
    private final String _ip;
    private final int _port;
    private final String _endpoint;
    private final long _dateCreated;
    
    static {
    	_zabbixApi = new ZabbixApi("localhost",
    			ConfigurationManager.getStringParameterValue(ConfigurationManager.API_USER),
    	        ConfigurationManager.getStringParameterValue(ConfigurationManager.API_PASSWORD));
    }

    /**
     * Gets the JMX Configuration for the Hosts JMX interface
     * using the given IP and port.
     * @param ip The Host's IP
     * @param port The Host's JMX interface port
     * @return The Host's JMX Configuration
     */
    public static JmxConfiguration getConfig(String ip, int port) {
        // See if we can use the cached value
        String configKey = buildMapKey(ip, port);
        if (_jmxConfigs.containsKey(configKey)) {
            JmxConfiguration jmxConfig = _jmxConfigs.get(configKey);
            if (!jmxConfig.isStale()) {
                return jmxConfig;
            }
            logger.debug(String.format(
                        "Configuration is stale for connection - %s - refetching the configuration",
                        configKey));
        }

        JmxConfiguration config = retrieveConfig(ip, port);
        _jmxConfigs.put(configKey, config);

        return config;
    }

    // Private constructor, use getConfig
    private JmxConfiguration(String ip, int port) {
        this(null, ip, port, null);
    }

    // Private constructor, use getConfig
    private JmxConfiguration(String protocol, String ip, int port,
            String endpoint) {
        // Allow users to specify just "jmx" instead of the entire protocol
        if (protocol == null 
        		|| protocol.isEmpty()
        		|| protocol.equalsIgnoreCase(JMX_SHORTHAND)) {
            protocol = DEFAULT_JMX_PROTOCOL;
        }

        if (endpoint == null || endpoint.isEmpty()) {
            endpoint = DEFAULT_JMX_ENDPOINT;
        }
        // Make sure the endpoint starts with a /
        else if (!endpoint.startsWith("/")) {
            endpoint = "/" + endpoint;
        }

        _protocol = protocol;
        _endpoint = endpoint;
        _ip = ip;
        _port = port;
        _dateCreated = System.currentTimeMillis();
    }

    /**
     * Gets the Hosts configured JMX protocol i.e.
     * service:jmx:rmi:///jndi/rmi or http (Jolokia)
     * @return The JMX protocol
     */
    public String getProtocol() {
        return _protocol;
    }

    /**
     * Gets the Host's IP
     * @return The Host's IP
     */
    public String getIp() {
        return _ip;
    }

    /**
     * Gets the Host's JMX port
     * @return The Host's JMX port
     */
    public int getPort() {
        return _port;
    }

    /**
     * Gets the JMX interfaces endpoint i.e.
     * "/jmxrmi"
     * @return
     */
    public String getEndpoint() {
        return _endpoint;
    }

    /**
     * Gets the full JMX URL for the host
     * @return
     */
    public String getUrl() {
        return _protocol + "://" + _ip + ":" + _port + _endpoint;
    }

    private static final JmxConfiguration retrieveConfig(String ip, int port) {
        JmxConfiguration config = null;
        try {
        	// Try to resolve the protocol and endpoint
        	// macros for the host using the Zabbix API
            List<String> hostIds = _zabbixApi.getHostIdsForConnection(ip, port);
            String protocol = _zabbixApi.resolveHostMacro(hostIds, MACRO_JMX_PROTOCOL);
            String endpoint = _zabbixApi.resolveHostMacro(hostIds, MACRO_JMX_ENDPOINT);
            config = new JmxConfiguration(protocol, ip, port, endpoint);
        }
        catch (IOException ignore) {
            config = new JmxConfiguration(ip, port);
        }
        catch (JSONException ignore) {
            config = new JmxConfiguration(ip, port);
        }

        logger.debug("JMX Configuration is: " + config.getUrl());
        return config;
    }

    private static final String buildMapKey(String ip, int port) {
        return ip + ":" + port;
    }

    private boolean isStale() {
        if (_dateCreated + STALE_THRESHOLD < System.currentTimeMillis()) {
            return true;
        }

        return false;
    }
}
