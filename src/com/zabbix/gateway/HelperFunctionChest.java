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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.regex.Pattern;

class HelperFunctionChest
{
	// Equivalent regex without unicode values: -?\\d.\\d+E[-+]?\\d+ 
    private static final Pattern DBL_SCI_NOTATION = Pattern.compile("\u002D?\\d\u002E\\d+\u0045[\u002D\u002B]?\\d+");
    
    
	public static <T> boolean arrayContains(T[] array, T key)
	{
		for (T element : array)
			if (key.equals(element))
				return true;

		return false;
	}

	public static int separatorIndex(String input)
	{
		byte[] inputByteArray = input.getBytes();
		int i, inputLength = inputByteArray.length;

		for (i = 0; i < inputLength; i++)
		{
			if ('\\' == inputByteArray[i])
			{
				if (i + 1 < inputLength &&
						('\\' == inputByteArray[i + 1] || '.' == inputByteArray[i + 1]))
					i++;
			}
			else if ('.' == inputByteArray[i])
				return i;
		}

		return -1;
	}

	public static String unescapeUserInput(String input)
	{
		byte[] inputByteArray = input.getBytes(), outputByteArray;
		ArrayList<Byte> outputByteList = new ArrayList<Byte>();
		int i, inputLength = inputByteArray.length;

		for (i = 0; i < inputLength; i++)
		{
			if ('\\' == inputByteArray[i] && i + 1 < inputLength &&
					('\\' == inputByteArray[i + 1] || '.' == inputByteArray[i + 1]))
			{
				i++;
			}

			outputByteList.add(inputByteArray[i]);
		}

		outputByteArray = new byte[outputByteList.size()];

		i = 0;
		for (Byte b : outputByteList)
		{
			outputByteArray[i] = b;
			i++;
		}

		return new String(outputByteArray);
	}
	
    /**
     * Tests if the String value is in the Java scientific format.
     * That format is described as follows (from Double.toString(double d) javadoc):
     * If m is less than 10^-3 or greater than or equal to 10^7, then it is represented in so-called
     * "computerized scientific notation." Let n be the unique integer such that 10^n <= m < 10^n+1;
     * then let a be the mathematically exact quotient of m and 10^n so that 1 <= a < 10.
     *  The magnitude is then represented as the integer part of a, as a single decimal digit,
     *  followed by '.' ('\u002E'), followed by decimal digits representing the fractional part of a,
     *  followed by the letter 'E' ('\u0045'), followed by a representation of n as a decimal integer,
     *  as produced by the method Integer.toString(int).
     */
    public static boolean isScientificNotation(String value) {
        return DBL_SCI_NOTATION.matcher(value).matches();
    }
    
    /**
     * Expands a string in scientific notation to
     * it's equivalent value removing the shorthand for
     * exponential.
     * @param value A string representing a number in scientific notation
     * @return The expanded string representation without the exponential
     * @throws NumberFormatException When a non-numeric value was passed in
     */
    public static String scientificToPlain(String value) throws NumberFormatException {
        return new BigDecimal(value).toPlainString();
    }
    
    /**
     * Gets the message of the root cause exception
     * @param ex
     * @return
     */
    public static String getRootCauseMessage(Throwable ex) {
    	Throwable root = ex;
    	while (root.getCause() != null) {
    		root = root.getCause();
    	}
    	
    	return root.getMessage();
    }
}
