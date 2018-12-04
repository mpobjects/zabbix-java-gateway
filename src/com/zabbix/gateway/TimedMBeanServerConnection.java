package com.zabbix.gateway;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.ReflectionException;

/**
 * A wrapper class for MBeanServerConnection that tracks the amount of time spent
 * making remote requests. This wrapper also hides methods that modify the remote 
 * server as this is meant for read only monitoring for the most part (other than executing operations).
 * @author rrupp
 *
 */
class TimedMBeanServerConnection {
	
	private final MBeanServerConnection delegate;
	private final AtomicLong totalTime;
	
	public TimedMBeanServerConnection(MBeanServerConnection internalConnection) {
		this.delegate = internalConnection;
		this.totalTime = new AtomicLong();
	}
	
	/**
	 * Returns the total amount of time spent making requests over the network
	 * @return The total time in nanoseconds
	 */
	public long getTotalNetworkTime() {
		return totalTime.get();
	}

	public Object getAttribute(ObjectName name, String attribute)
			throws MBeanException, AttributeNotFoundException,
			InstanceNotFoundException, ReflectionException, IOException {
		long startTime = startTime();
		try {
			return delegate.getAttribute(name, attribute);
		}
		finally {
			endTime(startTime);
		}
	}

	public AttributeList getAttributes(ObjectName name, String[] attributes)
			throws InstanceNotFoundException, ReflectionException, IOException {
		long startTime = startTime();
		try {
			return delegate.getAttributes(name, attributes);
		}
		finally {
			endTime(startTime);
		}
	}

	public String getDefaultDomain() throws IOException {
		return delegate.getDefaultDomain();
	}

	public String[] getDomains() throws IOException {
		return delegate.getDomains();
	}

	public Integer getMBeanCount() throws IOException {
		return delegate.getMBeanCount();
	}

	public MBeanInfo getMBeanInfo(ObjectName name)
			throws InstanceNotFoundException, IntrospectionException,
			ReflectionException, IOException {
		long startTime = startTime();
		try {
			return delegate.getMBeanInfo(name);
		}
		finally {
			endTime(startTime);
		}
	}

	public ObjectInstance getObjectInstance(ObjectName name)
			throws InstanceNotFoundException, IOException {
		long startTime = startTime();
		try {
			return delegate.getObjectInstance(name);
		}
		finally {
			endTime(startTime);
		}
	}

	public Object invoke(ObjectName name, String operationName,
			Object[] params, String[] signature)
			throws InstanceNotFoundException, MBeanException,
			ReflectionException, IOException {
		long startTime = startTime();
		try {
			return delegate.invoke(name, operationName, params, signature);
		}
		finally {
			endTime(startTime);
		}
	}

	public boolean isInstanceOf(ObjectName name, String className)
			throws InstanceNotFoundException, IOException {
		return delegate.isInstanceOf(name, className);
	}

	public boolean isRegistered(ObjectName name) throws IOException {
		return delegate.isRegistered(name);
	}

	public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query)
			throws IOException {
		long startTime = startTime();
		try {
			return delegate.queryMBeans(name, query);
		}
		finally {
			endTime(startTime);
		}
	}

	public Set<ObjectName> queryNames(ObjectName name, QueryExp query)
			throws IOException {
		long startTime = startTime();
		try {
		    return delegate.queryNames(name, query);
		}
		finally {
			endTime(startTime);
		}
	}
	
	private long startTime() {
		return System.nanoTime();
	}

	private void endTime(long startTime) {
		totalTime.addAndGet(System.nanoTime() - startTime);
	}
}
