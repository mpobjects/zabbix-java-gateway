package com.zabbix.gateway;

import static org.junit.Assert.*;

import java.io.IOException;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;

import org.jolokia.jvmagent.JolokiaServer;
import org.jolokia.jvmagent.ServerConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

public class JolokiaCheckerTest extends JMXItemCheckerTestsBase {
	
	private static JolokiaServer jServer;
	private static int jolokiaPort;
	
	@BeforeClass
	public static void setupJolokiaCheckerTest() throws IOException, InstanceAlreadyExistsException,
	        MBeanRegistrationException, NotCompliantMBeanException, MalformedObjectNameException,
	        NullPointerException {
		jolokiaPort = getFreePort();
		
		// Setup Jolokia
		jServer = new JolokiaServer(new ServerConfig("host=localhost,port=" + jolokiaPort), false);
		jServer.start();
	}
	
	@AfterClass
	public static void tearDownJolokiaCheckerTest() throws MBeanRegistrationException, InstanceNotFoundException,
	        MalformedObjectNameException, NullPointerException {
		jServer.stop();
	}
	
	public JmxConfiguration getMockConfig() {
    	JmxConfiguration mockConfig = Mockito.mock(JmxConfiguration.class);
    	Mockito.when(mockConfig.getUrl()).thenReturn("http://localhost:" + jolokiaPort + "/jolokia");
    	return mockConfig;
    }
	
	@Override
	public ItemChecker getItemChecker(JSONObject request)
			throws ZabbixException {
		return new JolokiaChecker(request, getMockConfig());
	}

	@Override
	public int getTestPort() {
		return jolokiaPort;
	}
	
    @Test
    public void testOperationInline() throws JSONException, ZabbixException {
        JSONObject request = getNewRequestObject();
        JSONArray keys = new JSONArray();
        keys.put("jmx.operation[\"java.lang:type=Threading\",\"findDeadlockedThreads()\"]");
        request.put("keys", keys);

        ItemChecker itemChecker = new JolokiaChecker(request, getMockConfig());
        JSONArray values = itemChecker.getValues();
        assertTrue(values.getJSONObject(0).has("value"));
    }
    
    @Test
    public void testOperationNoParentheses() throws JSONException, ZabbixException {
        JSONObject request = getNewRequestObject();
        JSONArray keys = new JSONArray();
        // We should still accept operation calls when the
        // parentheses are not explicitly provided (empty parameters)
        keys.put("jmx.operation[\"java.lang:type=Threading\",\"findDeadlockedThreads\"]");
        request.put("keys", keys);

        ItemChecker itemChecker = new JolokiaChecker(request, getMockConfig());
        JSONArray values = itemChecker.getValues();
        assertTrue(values.getJSONObject(0).has("value"));
    }
    
    @Test
    public void testOperationWithArgs() throws JSONException, ZabbixException {
        JSONObject request = getNewRequestObject();
        JSONArray keys = new JSONArray();
        keys.put("jmx.operation[\"java.lang:type=Threading\",\"dumpAllThreads(true,true)\"]");
        request.put("keys", keys);

        ItemChecker itemChecker = new JolokiaChecker(request, getMockConfig());
        JSONArray values = itemChecker.getValues();
        assertTrue(values.getJSONObject(0).has("value"));
    }

    @Test
    public void testOperationWithSignature() throws JSONException, ZabbixException {
        JSONObject request = getNewRequestObject();
        JSONArray keys = new JSONArray();
        keys.put("jmx.operation[\"java.lang:type=Threading\",\"dumpAllThreads(boolean,boolean)\",\"true,true\"]");
        request.put("keys", keys);

        ItemChecker itemChecker = new JolokiaChecker(request, getMockConfig());
        JSONArray values = itemChecker.getValues();
        assertTrue(values.getJSONObject(0).has("value"));
    }
}
