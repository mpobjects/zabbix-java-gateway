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
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retrieves JMXConfigurations for a Host interface
 * given the ip/port for the JMX interface.
 *
 */
public class JmxConfigurationManager {

    final static String MACRO_JMX_PROTOCOL = "{$JMX_PROTOCOL}";
    final static String MACRO_JMX_ENDPOINT = "{$JMX_ENDPOINT}";

    private static final Logger logger = LoggerFactory
        .getLogger(JmxConfigurationManager.class);

    private static final long STALE_THRESHOLD = 1000 * 60 * 5; // 5 minute
                                                               // threshold
    private final ZabbixApi _zabbixApi;
    private final ConcurrentMap<String, JmxConfiguration> _jmxConfigs =
    		new ConcurrentHashMap<String, JmxConfiguration>();
    
    public JmxConfigurationManager(InetAddress apiHost, Integer apiPort, String apiUser, String apiPassword) {
    	_zabbixApi = new ZabbixApi(apiHost, apiPort,
    			apiUser, apiPassword);
    }

    /**
     * Gets the JMX Configuration for the Hosts JMX interface
     * using the given IP and port.
     * @param ip The Host's IP
     * @param port The Host's JMX interface port
     * @return The Host's JMX Configuration
     */
    public JmxConfiguration getConfig(String ip, int port) {
        // See if we can use the cached value
        String configKey = buildMapKey(ip, port);
        JmxConfiguration oldConfig = _jmxConfigs.get(configKey);
        if (oldConfig != null) {
            if (!isStale(oldConfig)) {
                return oldConfig;
            }
            logger.debug(String.format(
                        "Configuration is stale for connection - %s - refetching the configuration",
                        configKey));
        }

        // Try to retrieve the configuration. If we're just refreshing the configuration
        // and we're unable to access the frontend, use the old configuration
        JmxConfiguration config = oldConfig;
		try {
			config = retrieveConfig(ip, port);
		} catch (IOException e) {
			logger.error("Unable to connect to the frontend to update interface - {}:{} - {}",
					new Object[]{ip, port, e.getMessage()});
			if (config == null) {
				config = new JmxConfiguration(ip, port);
			}
		} catch (JSONException e) {
			logger.error("A parsing exception occurred when updating interface - {}:{} - {}",
					new Object[]{ip, port, e.getMessage()});
			if (config == null) {
				config = new JmxConfiguration(ip, port);
			}
		}
        _jmxConfigs.put(configKey, config);

        return config;
    }

    private JmxConfiguration retrieveConfig(String ip, int port) 
            throws IOException, JSONException {
        JmxConfiguration config = null;
    	// Try to resolve the protocol and endpoint
    	// macros for the host using the Zabbix API
        List<String> hostIds = _zabbixApi.getHostIdsForConnection(ip, port);
        String protocol = _zabbixApi.resolveHostMacro(hostIds, MACRO_JMX_PROTOCOL);
        String endpoint = _zabbixApi.resolveHostMacro(hostIds, MACRO_JMX_ENDPOINT);
        config = new JmxConfiguration(protocol, ip, port, endpoint);

        logger.debug("JMX Configuration is: " + config.getUrl());
        return config;
    }

    private String buildMapKey(String ip, int port) {
        return ip + ":" + port;
    }

    private boolean isStale(JmxConfiguration config) {
        if (config.getDateCreated() + STALE_THRESHOLD < System.currentTimeMillis()) {
            return true;
        }

        return false;
    }
}
