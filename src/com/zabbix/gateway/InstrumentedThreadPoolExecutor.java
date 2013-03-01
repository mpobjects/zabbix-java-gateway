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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.MetricsRegistry;

/**
 * A thread pool that is instrumented to expose metrics about
 * the state of the thread pool. Currently, this assumes one thread pool
 * per JVM/process, to allow for multiple we would need a unique ID of some sort.
 * @author rrupp
 *
 */
public class InstrumentedThreadPoolExecutor extends ThreadPoolExecutor {
	
	private static final RejectedExecutionHandler defaultHandler =
	        new AbortPolicy();
	
	public InstrumentedThreadPoolExecutor(int corePoolsize,
			int maxPoolSize,
			long keepAliveTime,
			TimeUnit unit,
			BlockingQueue<Runnable> workQueue,
			MetricsRegistry registry) {
		this(corePoolsize, maxPoolSize, keepAliveTime, unit, workQueue, defaultHandler, registry);
	}
	
	public InstrumentedThreadPoolExecutor(int corePoolSize,
			int maxPoolSize, 
			long keepAliveTime,
			TimeUnit unit,
			BlockingQueue<Runnable> workQueue,
			RejectedExecutionHandler handler,
			MetricsRegistry registry) {
		super(corePoolSize, maxPoolSize, keepAliveTime, unit, workQueue, handler);
		
		// Expose metrics about this thread pool
		registry.newGauge(InstrumentedThreadPoolExecutor.class, "active-count", new Gauge<Integer>() {

			@Override
			public Integer getValue() {
				return getActiveCount();
			}
			
		});
		
		registry.newGauge(InstrumentedThreadPoolExecutor.class, "peak-size", new Gauge<Integer>() {

			@Override
			public Integer getValue() {
				return getLargestPoolSize();
			}
			
		});
		
		registry.newGauge(InstrumentedThreadPoolExecutor.class, "current-size", new Gauge<Integer>() {

			@Override
			public Integer getValue() {
				return getPoolSize();
			}
			
		});
		
		registry.newGauge(InstrumentedThreadPoolExecutor.class, "max-size", new Gauge<Integer>() {

			@Override
			public Integer getValue() {
				return getMaximumPoolSize();
			}
			
		});
	}

}
