package com.zabbix.gateway;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.rmi.registry.LocateRegistry;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockito.Mockito;

public class JMXItemCheckerTest extends JMXItemCheckerTestsBase {
	
	private static int rmiPort;
	private static JMXConnectorServer testServer;
	private static String fullProtocol = "service:jmx:rmi:///jndi/rmi://";
	private static String fullServiceUrl;
	
	@BeforeClass
	public static void setupJMXItemCheckerTest() throws IOException, InstanceAlreadyExistsException,
	        MBeanRegistrationException, NotCompliantMBeanException, MalformedObjectNameException, NullPointerException {
		rmiPort = getFreePort();
		
		LocateRegistry.createRegistry(rmiPort);
		fullServiceUrl = fullProtocol + "localhost:" + rmiPort + "/jmxrmi";
        JMXServiceURL url = new JMXServiceURL(fullServiceUrl);
        
        testServer = 
            JMXConnectorServerFactory.newJMXConnectorServer(url, null, 
                    ManagementFactory.getPlatformMBeanServer());
        testServer.start();
	}
	
	@AfterClass
	public static void tearDownJMXItemCheckerTest() throws IOException {
		testServer.stop();
	}

	@Override
	public ItemChecker getItemChecker(JSONObject request)
			throws ZabbixException {
		return new JMXItemChecker(request, getMockConfig());
	}

	public JmxConfiguration getMockConfig() {
		JmxConfiguration mockConfig = Mockito.mock(JmxConfiguration.class);
    	Mockito.when(mockConfig.getUrl()).thenReturn(fullServiceUrl);
    	return mockConfig;
	}

	@Override
	public int getTestPort() {
		return rmiPort;
	}
}
