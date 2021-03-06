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

import java.io.Console;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.Key;
import java.security.spec.KeySpec;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * This utility class is intended to support encryption/decryption by doing the following:
 * 1) Allowing a private key to be generated by the user specifying a key phrase
 * 2) Encrypting passwords for remote JMX clients using the private key so that passwords
 * are not stored in plaintext.
 * @author Ryan Rupp
 *
 */
public class KeyGenerator {
	
	public static final String PRIVATE_FILENAME = ".zbx_privatekey";

	/**
	 * Main method used to create the private key and generate encrypted client passwords
	 * @param args No arguments are expected/supported currently
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		Console console = System.console();
		
		// Check that the private key has been generated, if not prompt the user to create it
		// The private key should be on the classpath typically <zabbix_java>/bin/.zbx_privatekey
		File privateKeyFile = getPrivateKeyFile();
		if (privateKeyFile == null || !privateKeyFile.exists()) {
			String privatePass = new String(
					console.readPassword("%s", "Private key doesn't exist, enter the desired key password: "));
			if (!privatePass.equals(new String(console.readPassword("%s", "Confirm key password: ")))) {
				System.err.println("Passwords do not match!");
				System.exit(1);
			}

			// Generate the new private key and prompt the user to lock this file down via
			// OS permissions - the file should only be readable by the zabbix_java service user
			privateKeyFile = new File(PRIVATE_FILENAME);
			generatePrivateKey(privateKeyFile, privatePass);
			PrintWriter w = console.writer();
			w.println("Private key file output to: " + privateKeyFile.getAbsolutePath());
			w.println("Move the file <zabbix_java>/bin");
			w.println("Restrict access to the file with the following commands: ");
			w.println("chown <zabbix_java_service_user> " + PRIVATE_FILENAME);
			w.println("chmod 400 " + PRIVATE_FILENAME);
			System.exit(0);
		}
		
		// Prompt the user to provide the JMX client password that they wish to encrypt
		String passToEncode = new String(
				console.readPassword("%s", "Enter the remote client's password to encrypt: "));
		if (!passToEncode.equals(new String(console.readPassword("%s", "Confirm password: ")))) {
			System.err.println("Passwords do not match!");
			System.exit(1);
		}
		
		// Provide the user with the encrypted password using the private key
		SecurityUtils utils = new SecurityUtils(privateKeyFile);
		String encrypted = utils.encrypt(passToEncode);
		System.out.println("Using the private key located at - " + privateKeyFile.getAbsolutePath() +
				           " - to encrypt the client password");
		System.out.println("Use the following encrypted password: " + encrypted);
    }
	
	/**
	 * Gets the private key file via the classpath or returns null 
	 * if it doesn't exist on the classpath
	 * @return The private key File or null if not found
	 */
	public static File getPrivateKeyFile() {
		URL url = KeyGenerator.class.getClassLoader().getResource(PRIVATE_FILENAME);
		if (url == null) return null;
		
		try {
			return new File(url.toURI());
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Generates the private key file given the private key password
	 * @param file The private key file to create
	 * @param privatePass The private key password provided in plain text
	 * @throws Exception
	 */
	protected static void generatePrivateKey(File file, String privatePass) throws Exception {
		final byte[] salt = {
	        (byte) 0x19, (byte) 0x34, (byte) 0x50, (byte) 0xad,
	        (byte) 0xde, (byte) 0x93, (byte) 0xea, (byte) 0x12,
	    };
		SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
		KeySpec spec = new PBEKeySpec(privatePass.toCharArray(), salt, 65536, 128);
		SecretKey tmp = factory.generateSecret(spec);
		serializeKey(file, tmp);
	}
	
	private static void serializeKey(File file, Key key) throws IOException {
		FileOutputStream fStream = new FileOutputStream(file);
		try {
		    fStream.write(key.getEncoded());
		}
		finally {
		    fStream.close();
		}
	}
}
