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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZabbixApi {
	
	private static final Logger logger = LoggerFactory.getLogger(ZabbixApi.class);
	
	final static String JSON_RPC = "jsonrpc";
    final static String JSON_RPC_VER = "2.0";
    final static String METHOD = "method";
    final static String CALL_AUTHENTICATE = "user.authenticate";
    final static String CALL_MACRO_GET = "usermacro.get";
    final static String CALL_HOST_GET = "host.get";
    final static String PARAMS = "params";
    final static String USER = "user";
    final static String PASSWORD = "password";
    final static String ID = "id";
    final static String RESULT = "result";
    final static String CONTENT_TYPE = "Content-Type";
    final static String APP_JRPC = "application/json-rpc";
    final static String FILTER = "filter";
    final static String IP = "ip";
    final static String DNS = "dns";
    final static String PORT = "port";
    final static String INTERFACE_TYPE = "type";
    final static String TYPE_JMX = "4";
    final static String OUTPUT = "output";
    final static String OUTPUT_SHORTEN = "shorten";
    final static String AUTH = "auth";
    final static String HOST_ID = "hostid";
    final static String TEMPLATE_ID = "templateid";
    final static String HOST_IDS = "hostids";
    final static String SELECT_TEMPLATES = "selectParentTemplates";
    final static String OUTPUT_REFER = "refer";
    final static String OUTPUT_EXTEND = "extend";
    final static String PARENT_TEMPLATES = "parentTemplates";
    final static String MACRO = "macro";
    final static String VALUE = "value";
    
    final static int SOCKET_TIMEOUT = 1000; // 1 second
    final static int READ_TIMEOUT = 2000; // 2 seconds

    private String _zabbixConn;
    private String _authKey;
    private int _requestId;
    
    /**
     * Creates a new Zabbix API connection and logs it in.
     * @param host The Zabbix API frontend host or IP address
     * @param port The Zabbix API frontend port
     * @param username The Zabbix API username (must be a valid user)
     * @param password The Zabbix API password for the user
     */
    public ZabbixApi(InetAddress host, Integer port, String username, String password) {
    	_zabbixConn =  host.getHostAddress() + ":" + port;
    	logger.debug("Initializing Zabbix API with connection: " + _zabbixConn);
    	_requestId = 1;
    	_authKey = login(username, password, _requestId);
    }
    
    protected List<String> getHostIdsForConnection(String ip, int port) throws JSONException, IOException {
    	StringWriter writer = new java.io.StringWriter();
        JSONWriter w = new JSONWriter(writer);
        w.object()
            .key(JSON_RPC).value(JSON_RPC_VER)
            .key(METHOD).value(CALL_HOST_GET)
            .key(PARAMS).object()
                .key(FILTER).object()
                    .key(IP).value(ip)
                    .key(PORT).value(port)
                    .key(INTERFACE_TYPE).value(TYPE_JMX)
                .endObject()
                .key(OUTPUT).value(OUTPUT_SHORTEN)
                .key(SELECT_TEMPLATES).value(OUTPUT_REFER)
            .endObject()
            .key(AUTH).value(_authKey)
            .key(ID).value(_requestId)
        .endObject();

        JSONArray results = zabbixApiCall(writer.toString()).getJSONArray(RESULT);
        // Try DNS instead if no results were returned
        if (results.length() == 0) {
        	writer = new java.io.StringWriter();
        	w = new JSONWriter(writer);
            w.object()
                .key(JSON_RPC).value(JSON_RPC_VER)
                .key(METHOD).value(CALL_HOST_GET)
                .key(PARAMS).object()
                    .key(FILTER).object()
                        .key(DNS).value(ip)
                        .key(PORT).value(port)
                        .key(INTERFACE_TYPE).value(TYPE_JMX)
                    .endObject()
                    .key(OUTPUT).value(OUTPUT_SHORTEN)
                    .key(SELECT_TEMPLATES).value(OUTPUT_REFER)
                .endObject()
                .key(AUTH).value(_authKey)
                .key(ID).value(_requestId)
            .endObject();
            
            results = zabbixApiCall(writer.toString()).getJSONArray(RESULT);
        }
        
        JSONObject result = results.getJSONObject(0);
        List<String> hostIds = new ArrayList<String>();
        hostIds.add(result.getString(HOST_ID));
        if (result.has(PARENT_TEMPLATES)) {
        	JSONArray parentTemplates = result.getJSONArray(PARENT_TEMPLATES);
        	for (int i = 0; i < parentTemplates.length(); i++) {
        		JSONObject parentObj = parentTemplates.getJSONObject(i);
        		// Zabbix 2.2 changed the API here to return "template_id"
        		// instead of "host_id" for the parent templates
        		if (parentObj.has(TEMPLATE_ID)) {
        			hostIds.add(parentObj.getString(TEMPLATE_ID));
        		}
        		else if (parentObj.has(HOST_ID)) {
        			hostIds.add(parentObj.getString(HOST_ID));
        		}
        	}
        }
        
        return hostIds;
    }
    
    protected String resolveHostMacro(List<String> hostIds, String macro) throws JSONException, IOException {
    	
    	JSONArray jsonHostIds = new JSONArray(hostIds);
    	StringWriter writer = new java.io.StringWriter();
        JSONWriter w = new JSONWriter(writer);
        w.object()
            .key(JSON_RPC).value(JSON_RPC_VER)
            .key(METHOD).value(CALL_MACRO_GET)
            .key(PARAMS).object()
                .key(HOST_IDS).value(jsonHostIds)
                .key(FILTER).object()
                    .key(MACRO).value(macro)
                .endObject()
                .key(OUTPUT).value(OUTPUT_EXTEND)
            .endObject()
            .key(AUTH).value(_authKey)
            .key(ID).value(_requestId)
        .endObject();
        
        JSONArray results = zabbixApiCall(writer.toString()).getJSONArray(RESULT);
        // Handle macro precedence
        String resolvedMacro = null;
        int resolvedMacroPrecedence = hostIds.size() + 1;
        for (int i = 0; i < results.length(); i++) {
        	JSONObject result = results.getJSONObject(i);
        	int precedence = hostIds.indexOf(result.get(HOST_ID));
        	if (precedence < resolvedMacroPrecedence) {
        		resolvedMacro = result.getString(VALUE);
        	}
        }
        
        return resolvedMacro;
    }
    
	
	/**
     * Logs you into the Zabbix server. Returns an authentication string which must be used
     * for other API calls to succeed.
     * @param user Username you want to log in with.
     * @param pass Password you want to log in with.
     * @param requestId An integer ID that will be present in the response to this request. Allows
     * a way to keep track of messages.
     * @return Authentication key that must be used with all subsequent messages.
     * @throws IOException
     * @throws JSONException
     */
    private String login(String user, String pass, int requestId) {
    	logger.debug("Logging into the Zabbix API");
    	String authKey = null;
    	try {
            String call = buildLoginString(user, pass, requestId);
            JSONObject results = zabbixApiCall(call);
            if (results != null && results.has(RESULT)) {
            	authKey = results.getString(RESULT);
            }
            else {
            	logger.error(String.format("Unable to login through the Zabbix API with username: %s and password: %s", user, pass));
            }
    	}
    	catch (Exception ex) {
    		logger.error(String.format("Unable to login through the Zabbix API with username: %s and password: %s", user, pass), ex);
    	}
    	
    	if (authKey == null) {
    		logger.error("Exiting the Zabbix Java Gateway due to failed API authentication");
    		System.exit(1);
    	}
    	
    	return authKey;
    }

    /**
     * Build a JSON string that can be used to call Zabbix API to perform a login, allowing further actions.
     * @param username Existing username in the system.
     * @param password Corresponding password in the system.
     * @param id Id that will be returned in response corresponding to this request.
     * @return String in JSON format conforming to Zabbix API.
     * @throws JSONException
     */
    private String buildLoginString(String username, String password, int id) throws JSONException {
        StringWriter writer = new java.io.StringWriter();
        JSONWriter w = new JSONWriter(writer);
        w.object()
            .key(JSON_RPC).value(JSON_RPC_VER)
            .key(METHOD).value(CALL_AUTHENTICATE)
            .key(PARAMS).object()
                .key(USER).value(username)
                .key(PASSWORD).value(password)
            .endObject()
            .key(ID).value(id)
        .endObject();
        return writer.toString();
    }
    
    /**
     * A content-agnostic method to make a JSON-rpc call to the Zabbix server.
     * @param jrpc A JSON formatted string containing the JSON-rpc call information.
     * @return Zabbix server's response to the call, in JSON format.
     * @throws IOException
     * @throws JSONException
     */
    private JSONObject zabbixApiCall(String jrpc) throws IOException, JSONException {
        StringBuilder builder;
        BufferedReader rd;
        OutputStreamWriter wr;
        HttpURLConnection conn;
        URL zabbixApiUrl = new URL("http://" + _zabbixConn + "/zabbix/api_jsonrpc.php");
        
        conn = (HttpURLConnection)zabbixApiUrl.openConnection();
        conn.setRequestProperty(CONTENT_TYPE, APP_JRPC);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setDoInput(true);
        // Set timeouts so we don't hang for an unnecessary time
        conn.setConnectTimeout(SOCKET_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        wr = new OutputStreamWriter(conn.getOutputStream());
        
        /*Request*/
        logger.debug("Request=" + jrpc);
        wr.write(jrpc);
        wr.flush();
        
        /*Response*/
        String part = "";
        builder = new StringBuilder();
        rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        while((part = rd.readLine()) != null){
            builder.append(part);
        }
        String response = builder.toString();
        logger.debug("Response=" + response);
        JSONObject resp = new JSONObject(response);

        /*Clean up*/
        conn.disconnect();
        wr.close();
        rd.close();
        return resp;
    }
}
