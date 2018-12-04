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

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.AlgorithmParameters;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Manages encrypting and decrypting keys with the use
 * of a private key.
 * @author Ryan Rupp
 *
 */
public class SecurityUtils {
	
	private static final String ENCRYPTION_MODE = "AES";
	private static final String ENCRYPTION_TRANS = "AES/CBC/PKCS5Padding";
	
	private final byte[] _privateKeyEncoded;
	
	/**
	 * Constructs SecurityUtils using the given private key file name/path
	 * @param privateKeyFileName The private file name absolute or relative path
	 * @throws IOException Unable to read the file
	 */
	public SecurityUtils(String privateKeyFileName) throws IOException {
		this(new File(privateKeyFileName));
	}
	
	/**
	 * Constructs SecurityUtils using the given private key File
	 * @param privateKeyFile The private key file
	 * @throws IOException Unable to read the file
	 */
	public SecurityUtils(File privateKeyFile) throws IOException {
		_privateKeyEncoded = new byte[(int)privateKeyFile.length()];
		DataInputStream dis = new DataInputStream((new FileInputStream(privateKeyFile)));
		try {
		    dis.readFully(_privateKeyEncoded);
		}
		finally {
			dis.close();
		}
	}
	
	/**
	 * Decrypts the base64 encoded key using the private key
	 * If the key/password is not encrypted then the key that was
	 * given is returned.
	 * @param base64key The base64 encoded key
	 * @return The decrypted plain text password
	 * @throws Exception Unable to decrypt the password
	 */
	public String decrypt(String base64key) throws Exception {
		// If the key isn't encrypted don't do anything
		if (!EncryptedKey.isEncryptedKey(base64key)) return base64key;
		
		EncryptedKey key = new EncryptedKey(base64key);
		SecretKey secret = new SecretKeySpec(_privateKeyEncoded, ENCRYPTION_MODE);
		Cipher cipher = Cipher.getInstance(ENCRYPTION_TRANS);
		cipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(key.getIv()));
		return new String(cipher.doFinal(key.getCiphertext()));
	}
	
	/**
	 * Encrypts the plain text password using the private key
	 * @param password The plain text password
	 * @return The encrypted password - see EncryptedKey
	 * @throws Exception Unable to encrypt the password
	 */
	protected String encrypt(String password) throws Exception {
		SecretKey secret = new SecretKeySpec(_privateKeyEncoded, ENCRYPTION_MODE);
		Cipher cipher = Cipher.getInstance(ENCRYPTION_TRANS);
		cipher.init(Cipher.ENCRYPT_MODE, secret);
		byte[] ciphertext = cipher.doFinal(password.getBytes("UTF-8"));
		AlgorithmParameters params = cipher.getParameters();
		EncryptedKey key = new EncryptedKey(params.getParameterSpec(IvParameterSpec.class).getIV(), ciphertext);
		return key.toString();
	}
}
