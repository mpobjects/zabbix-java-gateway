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

package com.zabbix.security;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests for SecurityUtils
 * @author Ryan Rupp
 *
 */
public class SecurityUtilsTest {
	
	private static File keyfile;
	
	@BeforeClass
	public static void setup() throws Exception {
		// Generate a private key to use for testing
		keyfile = File.createTempFile("SecurityUtilsTest", ".test");
		KeyGenerator.generatePrivateKey(keyfile, "myprivatekeypass");
	}
	
	@AfterClass
	public static void teardown() {
		keyfile.delete();
	}

	@Test
	public void testEncodeDecode() throws Exception {
		final String actualPassword = "myremotejmxclientpass";
		SecurityUtils utils = new SecurityUtils(keyfile);
		String encryptedKey = utils.encrypt(actualPassword);
		String plainTextPassword = utils.decrypt(encryptedKey);
		assertFalse(encryptedKey.equals(actualPassword));
		assertEquals(actualPassword, plainTextPassword);
	}
	
	@Test
	public void testUnencryptedPassword() throws Exception {
		// Test a password that isn't encrypted, nothing should be done to it
		final String actualPassword = "unencryptedpassword";
		SecurityUtils utils = new SecurityUtils(keyfile);
		String password = utils.decrypt(actualPassword);
		assertEquals(actualPassword, password);
	}

}
