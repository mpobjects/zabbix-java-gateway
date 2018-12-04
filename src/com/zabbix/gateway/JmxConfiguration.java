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
    
    private final String _protocol;
    private final String _ip;
    private final int _port;
    private final String _endpoint;
    private final long _dateCreated;

    protected JmxConfiguration(String ip, int port) {
        this(null, ip, port, null);
    }

    protected JmxConfiguration(String protocol, String ip, int port,
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
    
    /**
     * Gets the timestamp when the configuration was generated
     * @return
     */
    protected long getDateCreated() {
    	return _dateCreated;
    }
}
