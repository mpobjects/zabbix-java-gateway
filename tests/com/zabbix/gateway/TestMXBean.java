package com.zabbix.gateway;

import javax.management.MXBean;

import com.zabbix.gateway.TestMXBeanImpl.TestCompositeObject;

@MXBean
public interface TestMXBean {

	public Double getSciNotationDouble();
	
	public TestCompositeObject getCompositeObject();
	
	public long[] getLongArray();
	
	public int[] getEmptyArray();
}
