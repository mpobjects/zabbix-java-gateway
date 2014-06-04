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

import java.io.File;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yammer.metrics.Metrics;
import com.zabbix.security.KeyGenerator;
import com.zabbix.security.SecurityUtils;

public class JavaGateway
{
	private static final Logger logger = LoggerFactory.getLogger(JavaGateway.class);

	public static void main(String[] args)
	{
		if (1 == args.length && (args[0].equals("-V") || args[0].equals("--version")))
		{
			GeneralInformation.printVersion();
			System.exit(0);
		}
		else if (0 != args.length)
		{
			System.out.println("unsupported command line options");
			System.exit(1);
		}

		logger.info("Zabbix Java Gateway {} (revision {}) has started", GeneralInformation.VERSION, GeneralInformation.REVISION);

		Thread shutdownHook = new Thread()
		{
			public void run()
			{
				logger.info("Zabbix Java Gateway {} (revision {}) has stopped", GeneralInformation.VERSION, GeneralInformation.REVISION);
			}
		};

		Runtime.getRuntime().addShutdownHook(shutdownHook);

		try
		{
			ConfigurationManager.parseConfiguration();

			InetAddress listenIP = (InetAddress)ConfigurationManager.getParameter(ConfigurationManager.LISTEN_IP).getValue();
			int listenPort = ConfigurationManager.getIntegerParameterValue(ConfigurationManager.LISTEN_PORT);

			ServerSocket socket = new ServerSocket(listenPort, 0, listenIP);
			socket.setReuseAddress(true);
			logger.info("listening on {}:{}", socket.getInetAddress(), socket.getLocalPort());

			int startPollers = ConfigurationManager.getIntegerParameterValue(ConfigurationManager.START_POLLERS);
			ExecutorService threadPool = null;
			if (startPollers == 0) {
				// Mimic a cached thread pool (unbounded)
				threadPool = new InstrumentedThreadPoolExecutor(
						startPollers,
						Integer.MAX_VALUE,
						10L, TimeUnit.MINUTES,
						new SynchronousQueue<Runnable>(),
						new ThreadPoolExecutor.CallerRunsPolicy(),
						Metrics.defaultRegistry());
			}
			else {
			    threadPool = new InstrumentedThreadPoolExecutor(
						startPollers,
						startPollers,
						60L, TimeUnit.SECONDS,
						new ArrayBlockingQueue<Runnable>(startPollers),
						new ThreadPoolExecutor.CallerRunsPolicy(),
						Metrics.defaultRegistry());
			}
			logger.debug("created a thread pool of {} pollers", startPollers == 0 ? "unlimited" : startPollers);

			// Setup encryption using the private key if it exists
			File privateKeyFile = KeyGenerator.getPrivateKeyFile();
			SecurityUtils securityUtils = null;
			String apiPassword = ConfigurationManager.getStringParameterValue(ConfigurationManager.API_PASSWORD);
			// We'll decode the API password right away as well if we're using encryption
			if (privateKeyFile != null && privateKeyFile.exists()) {
				securityUtils = new SecurityUtils(privateKeyFile);
				apiPassword = securityUtils.decrypt(apiPassword);
			}
			else {
				logger.info("Encryption file " + KeyGenerator.PRIVATE_FILENAME + 
						" not found on the classpath so encrypted passwords will not be supported." +
						" If this is unintended verify the file exists under <zabbix_java>/bin and that" +
						" <zabbix_java>/bin is on your CLASSPATH");
			}
			
			// Setup the JmxConfigurationManager which will handle retrieving the proper JmxConfiguration
			// to support additional properties such as specifying the JMX protocol and endpoint
			JmxConfigurationManager jmxManager = new JmxConfigurationManager(
					ConfigurationManager.getStringParameterValue(ConfigurationManager.ZABBIX_URL),
	    			ConfigurationManager.getStringParameterValue(ConfigurationManager.API_USER),
	    	        apiPassword);
			
			while (true)
				threadPool.execute(new SocketProcessor(socket.accept(), jmxManager, securityUtils));
		}
		catch (Exception e)
		{
			logger.error("caught fatal exception", e);
		}
	}
}
