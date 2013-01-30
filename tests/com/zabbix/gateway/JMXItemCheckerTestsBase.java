package com.zabbix.gateway;

import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.LogManager;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * A base test class for JMX retrieval classes
 * so that they can all subclass this but still run
 * the same functional tests with a different underlying
 * ItemChecker
 * @author Ryan Rupp
 *
 */
public abstract class JMXItemCheckerTestsBase {
	
	protected static final String TEST_MBEAN_NAME = "test:type=test";
	private static final String TEST_CONN = "localhost";
    private static final String USER = "admin";
    private static final String PASS = "password";
	
    /**
     * Gets a new ItemChecker instance given the request, this should
     * return the particular ItemChecker implementation you're testing
     * @param request
     * @return
     * @throws ZabbixException
     */
	public abstract ItemChecker getItemChecker(JSONObject request) throws ZabbixException;
	
	/**
	 * Gets the port which the tests are communicating with.
	 * @return
	 */
	public abstract int getTestPort();
	
	@BeforeClass
	public static void setupJMXItemCheckerTestsBase() throws IOException, InstanceAlreadyExistsException,
	        MBeanRegistrationException, NotCompliantMBeanException, MalformedObjectNameException, NullPointerException {
		// Setup a test MBean
		MBeanServer beanServer = ManagementFactory.getPlatformMBeanServer();
		beanServer.registerMBean(new TestMXBeanImpl(), new ObjectName(TEST_MBEAN_NAME));
	}
	
	@AfterClass
	public static void tearDownJMXItemCheckerTestsBase() throws MBeanRegistrationException, InstanceNotFoundException,
	        MalformedObjectNameException, NullPointerException, IOException {
		MBeanServer beanServer = ManagementFactory.getPlatformMBeanServer();
		beanServer.unregisterMBean(new ObjectName(TEST_MBEAN_NAME));
	}
	
	@Test
    public void testArrayAttributeRead() throws JSONException, ZabbixException {
        List<String> actualLoggers = LogManager.getLoggingMXBean()
            .getLoggerNames();
        JSONObject request = getNewRequestObject();
        JSONArray keys = new JSONArray();
        keys.put("jmx[java.util.logging:type=Logging,LoggerNames]");
        request.put("keys", keys);

        ItemChecker itemChecker = getItemChecker(request);
        JSONArray values = itemChecker.getValues();
        String managerNames = values.getJSONObject(0).getString("value");
        List<String> foundLoggers = new ArrayList<String>();
        Collections.addAll(foundLoggers, managerNames.split("\\n"));
        assertTrue(actualLoggers.size() > 0);
    }
	
	@Test
    public void testPrimitiveArrayRead() throws JSONException, ZabbixException {
        JSONObject request = getNewRequestObject();
        JSONArray keys = new JSONArray();
        keys.put("jmx[" + TEST_MBEAN_NAME + ",LongArray]");
        request.put("keys", keys);

        ItemChecker itemChecker = getItemChecker(request);
        JSONArray values = itemChecker.getValues();
        String foundValues = values.getJSONObject(0).getString("value");
        TestMXBean testBean = new TestMXBeanImpl();
        for (long value : testBean.getLongArray()) {
        	assertTrue(foundValues.contains(String.valueOf(value)));
        }
    }
	
	@Test
    public void testEmptyArrayRead() throws JSONException, ZabbixException {
        JSONObject request = getNewRequestObject();
        JSONArray keys = new JSONArray();
        keys.put("jmx[" + TEST_MBEAN_NAME + ",EmptyArray]");
        request.put("keys", keys);

        ItemChecker itemChecker = getItemChecker(request);
        JSONArray values = itemChecker.getValues();
        assertEquals("", values.getJSONObject(0).getString("value"));
    }

    @Test
    public void testBadKeys() throws JSONException, ZabbixException {

        JSONObject request = getNewRequestObject();
        JSONArray keys = new JSONArray();
        // First test a missing attribute key
        keys.put("jmx[java.lang:type=Threading]");
        // Next test an incorrect first key
        final String badKey = "jmxbadkey";
        keys.put(badKey + "[java.lang:type=OperatingSystem,Name]");
        // Also test a bad discovery key format
        keys.put("jmx.discovery[java.lang:type=OperatingSystem,Name]");
        request.put("keys", keys);
        ItemChecker itemChecker = getItemChecker(request);
        JSONArray values = itemChecker.getValues();
        assertTrue(!values.getJSONObject(0)
            .get("error").toString().isEmpty());
        assertTrue(!values.getJSONObject(1).get("error").toString().isEmpty());
        assertTrue(values.getJSONObject(2).has("error"));
    }

    @Test
    public void testTopLevelAttributeRead() throws JSONException,
            ZabbixException {
        JSONObject request = getNewRequestObject();
        JSONArray keys = new JSONArray();
        keys.put("jmx[java.lang:type=OperatingSystem,Arch]");
        keys.put("jmx[java.lang:type=OperatingSystem,Name]");

        request.put("keys", keys);
        ItemChecker itemChecker = getItemChecker(request);
        JSONArray values = itemChecker.getValues();

        assertEquals(System.getProperty("os.arch"), values.getJSONObject(0)
            .get("value").toString());
        assertEquals(System.getProperty("os.name"), values.getJSONObject(1)
            .get("value").toString());
    }

    @Test
    public void testInvalidAttribute() throws JSONException, ZabbixException {
        JSONObject request = getNewRequestObject();
        JSONArray keys = new JSONArray();
        keys.put("jmx[java.lang:type=OperatingSystem,Arch]");
        keys.put("jmx[java.lang:type=OperatingSystem,blahblah]");
        keys.put("jmx[java.lang:type=OperatingSystem,Name]");

        request.put("keys", keys);
        ItemChecker itemChecker = getItemChecker(request);
        JSONArray values = itemChecker.getValues();

        assertEquals(System.getProperty("os.arch"), values.getJSONObject(0)
            .get("value").toString());
        assertTrue(values.getJSONObject(1).get("error").toString() != "");
        assertEquals(System.getProperty("os.name"), values.getJSONObject(2)
            .get("value").toString());
    }

    /**
     * Test shows you need quotes around mbean names that contain commas Test
     * with both the good format with commas around the mbean name and also the
     * bad format without commas which should error
     */
    @Test
    public void testCommaInMBean() throws JSONException, ZabbixException {
        JSONObject request = getNewRequestObject();
        JSONArray keys = new JSONArray();
        keys.put("jmx[\"java.lang:type=MemoryManager,name=CodeCacheManager\",Name]");
        keys.put("jmx[java.lang:type=MemoryManager,name=CodeCacheManager,name=PS Eden Space,Name]");

        request.put("keys", keys);
        ItemChecker itemChecker = getItemChecker(request);
        JSONArray values = itemChecker.getValues();

        assertEquals("CodeCacheManager", values.getJSONObject(0).get("value")
            .toString());
        assertTrue(!values.getJSONObject(1).get("error").toString().isEmpty());
    }

    @Test
    public void testCompositeDataRead() throws JSONException, ZabbixException {
        JSONObject request = getNewRequestObject();
        JSONArray keys = new JSONArray();
        keys.put("jmx[java.lang:type=Memory,HeapMemoryUsage.used]");
        keys.put("jmx[java.lang:type=Memory,HeapMemoryUsage.max]");
        // Test an invalid value too
        keys.put("jmx[java.lang:type=Memory,HeapMemoryUsage.invalidvalue]");
        request.put("keys", keys);

        ItemChecker itemChecker = getItemChecker(request);
        JSONArray values = itemChecker.getValues();
        assertTrue(Long.decode(values.getJSONObject(0).getString("value")) > 0);
        assertTrue(Long.decode(values.getJSONObject(1).getString("value")) > 0);
        // Test invalid values expected error message
        assertTrue(!values.getJSONObject(2).get("error").toString().isEmpty());

    }

    @Test
    public void testInvalidUserPassCombo() throws JSONException,
            ZabbixException {
        JSONObject request = getNewRequestObject(true, false);
        // Test provided username but missing password
        request.put(ItemChecker.JSON_TAG_USERNAME, "admin");
        JSONArray keys = new JSONArray();
        keys.put("jmx[java.lang:type=Memory,HeapMemoryUsage.used]");
        request.put("keys", keys);
        try {
            // This line should throw an exception due to missing password
        	getItemChecker(request);
            fail("IllegalArgumentException expected because username was provided but password is missing");
        }
        catch (ZabbixException expected) {
            // Expecting a ZabbixException with the inner exception being an
            // IllegalArgumentException
            assertEquals(IllegalArgumentException.class, expected.getCause()
                .getClass());
        }

        request = getNewRequestObject(false, true);
        // Test provided password but missing username
        request.put(ItemChecker.JSON_TAG_PASSWORD, "mypassword");
        request.put("keys", keys);
        try {
            // This line should throw an exception due to missing password
        	getItemChecker(request);
            fail("IllegalArgumentException expected because password was provided but username is missing");
        }
        catch (ZabbixException expected) {
            // Expecting a ZabbixException with the inner exception being an
            // IllegalArgumentException
            assertEquals(IllegalArgumentException.class, expected.getCause()
                .getClass());
        }
    }
    
    /**
     * Specify a wildcard in the object name to find all the Garbage Collector object names.
     * Then verify this against the actual garbage collectors that are registered.
     * @throws JSONException
     * @throws ZabbixException
     */
    @Test
    public void testObjectNameDiscovery() throws JSONException, ZabbixException {
    	JSONObject request = getNewRequestObject();
        JSONArray keys = new JSONArray();
        keys.put("jmx.discovery[\"java.lang:type=GarbageCollector,name=*\"]");
        request.put("keys", keys);

        ItemChecker itemChecker = getItemChecker(request);
        JSONArray values = itemChecker.getValues();
        String foundGcs = values.getJSONObject(0).get("value").toString();
        List<GarbageCollectorMXBean> actualGcs = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean actualCollector : actualGcs) {
        	// Valid full object name exists
        	assertTrue(foundGcs.contains(actualCollector.getName()));
        	
        	// Validate object name properties exist
        	assertTrue(foundGcs.contains("\"{#TYPE}\":\"GarbageCollector\""));
        	assertTrue(foundGcs.contains(String.format("\"{#NAME}\":\"%s\"", actualCollector.getName())));
        }
    }
    
    @Test
    public void testDiscoveryTooManyArguments() throws JSONException, ZabbixException {
    	JSONObject request = getNewRequestObject();
        JSONArray keys = new JSONArray();
        keys.put("jmx.discovery[\"java.lang:type=GarbageCollector,name=*\", InvalidSecondArg]");
        request.put("keys", keys);

        ItemChecker itemChecker = getItemChecker(request);
        JSONArray values = itemChecker.getValues();
        assertTrue(values.getJSONObject(0).has("error"));
    }
    
    @Test
    public void testScientificNotation() throws JSONException, ZabbixException {
        JSONObject request = getNewRequestObject();
        JSONArray keys = new JSONArray();
        keys.put(String.format("jmx[\"%s\",SciNotationDouble]", TEST_MBEAN_NAME));
        request.put("keys", keys);

        ItemChecker itemChecker = getItemChecker(request);
        JSONArray values = itemChecker.getValues();
        assertEquals(new TestMXBeanImpl().getSciNotationDouble(), Double.valueOf(values.getJSONObject(0).get("value").toString()));
        // Validate Exponential doesn't exist in string representation
        assertFalse(values.getJSONObject(0).get("value").toString().contains("E"));
    }
    
    protected static int getFreePort() throws IOException {
    	ServerSocket socket = new ServerSocket(0);
		int freePort = socket.getLocalPort();
		socket.close();
		return freePort;
    }
    
    protected JSONObject getNewRequestObject() throws JSONException {
        return getNewRequestObject(true, true);
    }
    
    protected JSONObject getNewRequestObject(boolean withUser, boolean withPass) throws JSONException {
        JSONObject request = new JSONObject();
        request.put("conn", TEST_CONN);
        request.put("port", getTestPort());
        
        if (withUser) request.put(ItemChecker.JSON_TAG_USERNAME, USER);
        if (withPass) request.put(ItemChecker.JSON_TAG_PASSWORD, PASS);
        
        return request;
    }
}
