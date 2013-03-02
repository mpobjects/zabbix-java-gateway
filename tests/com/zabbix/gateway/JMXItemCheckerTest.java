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

import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.ServerSocket;
import java.rmi.registry.LocateRegistry;
import java.util.Arrays;
import java.util.List;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MXBean;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.zabbix.gateway.JMXItemCheckerTest.TestMXBeanImpl.TestCompositeObject;

/**
 * Verify the behavior of JMXItemChecker by registering the MBean server of the
 * JVM this test is running on to validate values.
 * 
 * @author Ryan Rupp
 * 
 */
public class JMXItemCheckerTest {

    private static final String TEST_MBEAN_NAME = "test:type=dot.in.name";
    private static final TestMXBean TEST_BEAN = new TestMXBeanImpl();
    private static final String TEST_CONN = "localhost";
    private static final String USER = "admin";
    private static final String PASS = "password";
    private static final String PROTOCOL = "service:jmx:rmi:///jndi/rmi://";

    private static int rmiPort;
    private static JMXConnectorServer testServer;
    private static String fullServiceUrl;

    @BeforeClass
    public static void setup() throws IOException,
            InstanceAlreadyExistsException, MBeanRegistrationException,
            NotCompliantMBeanException, MalformedObjectNameException,
            NullPointerException {
        // Setup a test MBean
        MBeanServer beanServer = ManagementFactory.getPlatformMBeanServer();
        beanServer.registerMBean(TEST_BEAN, new ObjectName(TEST_MBEAN_NAME));

        // Find a port for RMI to register in this server on
        rmiPort = getFreePort();
        LocateRegistry.createRegistry(rmiPort);
        fullServiceUrl = PROTOCOL + "localhost:" + rmiPort + "/jmxrmi";
        JMXServiceURL url = new JMXServiceURL(fullServiceUrl);

        testServer = JMXConnectorServerFactory.newJMXConnectorServer(url, null,
                ManagementFactory.getPlatformMBeanServer());
        testServer.start();
    }

    @AfterClass
    public static void tearDown() throws MBeanRegistrationException,
            InstanceNotFoundException, MalformedObjectNameException,
            NullPointerException, IOException {
        MBeanServer beanServer = ManagementFactory.getPlatformMBeanServer();
        beanServer.unregisterMBean(new ObjectName(TEST_MBEAN_NAME));
        testServer.stop();
    }

    public ItemChecker getItemChecker(JSONObject request)
            throws ZabbixException {
        return new JMXItemChecker(request);
    }

    @Test
    public void testHostUnreachable() throws JSONException, ZabbixException {
        JSONObject request = getNewRequestObject(9999);
        JSONArray keys = new JSONArray();
        keys.put("jmx[java.lang:type=Threading]");
        request.put("keys", keys);
        ItemChecker itemChecker = getItemChecker(request);
        try {
            itemChecker.getValues();
            fail("An IOException should have occurred due to an invalid port being used.");
        } catch (ZabbixException expected) {
            assertEquals(IOException.class, expected.getCause().getClass());
        }
    }

    @Test
    public void testBadKeys() throws JSONException, ZabbixException {
        JSONObject request = getNewRequestObject();
        JSONArray keys = new JSONArray();
        // First test a missing attribute key
        keys.put("jmx[java.lang:type=Threading]");
        // Next test an incorrect first key
        String badKey = "jmxbadkey";
        keys.put(badKey + "[java.lang:type=OperatingSystem,Name]");
        // Also test a bad discovery key format
        keys.put("jmx.discovery[java.lang:type=OperatingSystem,Name]");
        request.put("keys", keys);
        ItemChecker itemChecker = getItemChecker(request);
        JSONArray values = itemChecker.getValues();
        assertFalse(values.getJSONObject(0).get("error").toString().isEmpty());
        assertFalse(values.getJSONObject(1).get("error").toString().isEmpty());
        assertTrue(values.getJSONObject(2).has("error"));
    }

    @Test
    public void testTopLevelAttributeRead() throws JSONException,
            ZabbixException {
        JSONArray values = runRequests(Arrays.asList(
                "java.lang:type=OperatingSystem,Arch",
                "java.lang:type=OperatingSystem,Name"));

        assertEquals(System.getProperty("os.arch"), values.getJSONObject(0)
                .get("value").toString());
        assertEquals(System.getProperty("os.name"), values.getJSONObject(1)
                .get("value").toString());
    }

    @Test
    public void testInvalidAttribute() throws JSONException, ZabbixException {
        JSONArray values = runRequests(Arrays.asList(
                "java.lang:type=OperatingSystem,Arch",
                "java.lang:type=OperatingSystem,foobar",
                "java.lang:type=OperatingSystem,Name"));

        assertEquals(System.getProperty("os.arch"), values.getJSONObject(0)
                .get("value").toString());
        assertFalse(values.getJSONObject(1).get("error").toString().isEmpty());
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
        JSONArray values = runRequests(Arrays.asList(
                "\"java.lang:type=MemoryManager,name=CodeCacheManager\",Name",
                "java.lang:type=MemoryManager,name=CodeCacheManager,name=PS Eden Space,Name"));

        assertEquals("CodeCacheManager", values.getJSONObject(0).get("value")
                .toString());
        assertFalse(values.getJSONObject(1).get("error").toString().isEmpty());
    }

    @Test
    public void testCompositeDataRead() throws JSONException, ZabbixException {
        JSONArray values = runRequests(Arrays.asList(
                "java.lang:type=Memory,HeapMemoryUsage.used",
                "java.lang:type=Memory,HeapMemoryUsage.max",
                "java.lang:type=Memory,HeapMemoryUsage.invalidvalue"));
        assertTrue(Long.decode(values.getJSONObject(0).getString("value")) > 0);
        assertTrue(Long.decode(values.getJSONObject(1).getString("value")) > 0);
        // Test invalid values has an error message
        assertFalse(values.getJSONObject(2).get("error").toString().isEmpty());

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
        } catch (ZabbixException expected) {
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
        } catch (ZabbixException expected) {
            // Expecting a ZabbixException with the inner exception being an
            // IllegalArgumentException
            assertEquals(IllegalArgumentException.class, expected.getCause()
                    .getClass());
        }
    }

    @Test
    public void testDiscoveryTooManyArguments() throws JSONException,
            ZabbixException {
        JSONArray values = runRequests(Arrays.asList(
                "\"java.lang:type=GarbageCollector,name=*\", InvalidSecondArg"));
        assertTrue(values.getJSONObject(0).has("error"));
    }

    @Test
    public void testDotInNameWithQuotes() throws JSONException,
            ZabbixException, InstanceAlreadyExistsException,
            MBeanRegistrationException, NotCompliantMBeanException,
            MalformedObjectNameException, NullPointerException {
        JSONArray values = runRequests(Arrays.asList(
                "\"" + TEST_MBEAN_NAME + "\",SingleInt"));
        assertEquals(TEST_BEAN.getSingleInt(), Integer.valueOf(values
                .getJSONObject(0).get("value").toString()));
    }

    @Test
    public void testDotInNameWithoutQuotes() throws JSONException,
            ZabbixException, InstanceAlreadyExistsException,
            MBeanRegistrationException, NotCompliantMBeanException,
            MalformedObjectNameException, NullPointerException {
        // This should still work without quotes around the object name as there
        // isn't
        // a comma in the name.
        JSONArray values = runRequests(Arrays.asList(
                TEST_MBEAN_NAME + ",SingleInt"));
        assertEquals(TEST_BEAN.getSingleInt(), Integer.valueOf(values
                .getJSONObject(0).get("value").toString()));
    }

    @Test
    public void testTwoLevelCompositeRead() throws JSONException,
            ZabbixException {
        JSONArray values = runRequests(Arrays.asList(
                "\"" + TEST_MBEAN_NAME + "\",CompositeObject.subComposite.subValueOne"));

        assertEquals(TEST_BEAN.getCompositeObject().getSubComposite()
                .getSubValueOne(), values.getJSONObject(0).get("value")
                .toString());
    }

    private static int getFreePort() throws IOException {
        ServerSocket socket = new ServerSocket(0);
        int freePort = socket.getLocalPort();
        socket.close();
        return freePort;
    }

    // Takes a list of <objectName>,<attribute> and makes a 
    // request using the JMXItemChecker
    private JSONArray runRequests(List<String> keys) throws JSONException,
            ZabbixException {
        JSONObject request = getNewRequestObject();
        JSONArray jsonKeys = new JSONArray();

        for (String key : keys) {
            jsonKeys.put("jmx[" + key + "]");
        }

        request.put("keys", jsonKeys);
        ItemChecker itemChecker = getItemChecker(request);
        return itemChecker.getValues();
    }

    private JSONObject getNewRequestObject() throws JSONException {
        return getNewRequestObject(true, true);
    }

    private JSONObject getNewRequestObject(int port) throws JSONException {
        JSONObject request = new JSONObject();
        request.put("conn", TEST_CONN);
        request.put("port", port);

        return request;
    }

    private JSONObject getNewRequestObject(boolean withUser, boolean withPass)
            throws JSONException {
        JSONObject request = new JSONObject();
        request.put("conn", TEST_CONN);
        request.put("port", rmiPort);

        if (withUser)
            request.put(ItemChecker.JSON_TAG_USERNAME, USER);
        if (withPass)
            request.put(ItemChecker.JSON_TAG_PASSWORD, PASS);

        return request;
    }

    // Test classes and MXBean below

    @MXBean
    public interface TestMXBean {

        public TestCompositeObject getCompositeObject();

        public Integer getSingleInt();
    }

    public static class TestMXBeanImpl implements TestMXBean {

        @Override
        public TestCompositeObject getCompositeObject() {
            return new TestCompositeObject();
        }

        @Override
        public Integer getSingleInt() {
            return 5;
        }

        public class TestCompositeObject {

            public String getValueOne() {
                return "ValueOne";
            }

            public String getValueTwo() {
                return "ValueTwo";
            }

            public TestSubCompositeObject getSubComposite() {
                return new TestSubCompositeObject();
            }
        }

        public class TestSubCompositeObject {

            public String getSubValueOne() {
                return "SubValueOne";
            }

            public String getSubValueTwo() {
                return "SubValueTwo";
            }

        }
    }
}