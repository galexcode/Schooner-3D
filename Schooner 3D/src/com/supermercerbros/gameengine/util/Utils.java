/*
 * Copyright 2012 Dan Mercer
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.supermercerbros.gameengine.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;

public class Utils {
	/**
	 * Returns the length of a vector given the vector's three coordinates. This
	 * uses the Pythagorean theorem (hence it's name).
	 * 
	 * @see <a
	 *      href="http://en.wikipedia.org/wiki/Pythagorean_theorem">Pythagorean
	 *      Theorem</a> (Wikipedia)
	 * 
	 * @param x
	 *            The x-coordinate.
	 * @param y
	 *            The y-coordinate.
	 * @param z
	 *            The z-coordinate.
	 * @return The length of the vector.
	 */
	public static float pythagF(float x, float y, float z) {
		return (float) android.util.FloatMath.sqrt((x * x) + (y * y) + (z * z));
	}

	/**
	 * Returns the length of a vector given the vector's three coordinates. This
	 * uses the Pythagorean theorem (hence it's name).
	 * 
	 * @see <a
	 *      href="http://en.wikipedia.org/wiki/Pythagorean_theorem">Pythagorean
	 *      Theorem</a> (Wikipedia)
	 * 
	 * @param x
	 *            The x-coordinate.
	 * @param y
	 *            The y-coordinate.
	 * @param z
	 *            The z-coordinate.
	 * @return The length of the vector.
	 */
	public static double pythagD(double x, double y, double z) {
		return Math.sqrt(x * x + y * y + z * z);
	}

	/**
	 * Creates a perspective projection matrix.
	 * 
	 * @param m
	 *            The float array to write the matrix to
	 * @param offset
	 *            The offset into array m where the matrix is written
	 * @param fov
	 *            The field-of-view angle, in degrees.
	 * @param aspect
	 *            The aspect ratio
	 * @param near
	 *            The near clip plane
	 * @param far
	 *            The far clip plane
	 */
	public static void perspectiveM(float[] m, int offset, float fov,
			float aspect, float near, float far) {
		fov = (float) Math.toRadians(fov);
		float f = (float) Math.tan(0.5 * (Math.PI - fov));
		float range = near - far;

		m[0] = f / aspect;
		m[1] = 0;
		m[2] = 0;
		m[3] = 0;

		m[4] = 0;
		m[5] = f;
		m[6] = 0;
		m[7] = 0;

		m[8] = 0;
		m[9] = 0;
		m[10] = far / range;
		m[11] = -1;

		m[12] = 0;
		m[13] = 0;
		m[14] = near * far / range;
		m[15] = 0;
	}

	public static String readAssetAsString(AssetManager assets, String filename)
			throws IOException {
		StringBuilder sb = new StringBuilder();
		Scanner reader = new Scanner(assets.open(filename));
		while (reader.hasNextLine()) {
			sb.append(reader.nextLine() + "\n");
		}
		return sb.toString();
	}

	public static String readResourceAsString(Resources res, int resId)
			throws NotFoundException {
		StringBuilder sb = new StringBuilder();
		Scanner reader = new Scanner(res.openRawResource(resId));
		while (reader.hasNextLine()) {
			sb.append(reader.nextLine() + "\n");
		}
		return sb.toString();
	}

	/**
	 * Reads the contents of the given InputStream. Uses the default charset.
	 * NOTE: This method does not close the InputStream.
	 * @param is
	 * @return
	 */
	public static String readInputStreamAsString(InputStream is) {
		StringBuilder sb = new StringBuilder();
		Scanner reader = new Scanner(is);
		while (reader.hasNextLine()) {
			sb.append(reader.nextLine() + "\n");
		}
		return sb.toString();
	}

	public static boolean checkBit(byte flags, int place) {
		return (flags & (1 << place)) != 0;
	}
	
	public static boolean[] checkBits(byte flags, int count) {
		if (count > 8) {
			throw new IllegalArgumentException("count is greater than 8");
		}
		boolean[] result = new boolean[count];
		for (int i = 0; i < count; i++) {
			result[i] = (flags & (1 << i)) != 0;
		}
		return result;
	}

	public static int search(short[] array, int begin, int end, int value) {
		if (array[begin] == value) {
			return begin;
		} else if (array[end] == value) {
			return end;
		} else if (begin + 1 == end) {
			return -1;
		} else {
			int middle = (begin + end) / 2;
			if (array[middle] > value) {
				return search(array, begin, middle, value);
			} else if (array[middle] < value) {
				return search(array, middle, end, value);
			} else {
				return middle;
			}
		}
	}
	
	public static String vboToString(int[] vbo, int offset, int length) {
		StringBuilder sb = new StringBuilder("[");
		
		if (offset > 0) {
			sb.append(" ... ");
		}
		
		for (int i = offset; i < offset + length; i++) {
			sb.append(Float.intBitsToFloat(vbo[i]));
			if (i < vbo.length - 1) {
				sb.append(", ");
			}
		}
		
		if (offset + length < vbo.length) {
			sb.append(" ... ");
		}
		sb.append("]");
		
		if (offset > 0 || offset + length < vbo.length) {
			sb.append(" (" + length + " floats)");
		}
		return sb.toString();
	}

	public static String iboToString(short[] ibo, int offset, int length) {
		StringBuilder sb = new StringBuilder("[");
		
		if (offset > 0) {
			sb.append(" ... ");
		}
		
		for (int i = offset; i < offset + length; i++) {
			sb.append(ibo[i]);
			if (i < ibo.length - 1) {
				sb.append(", ");
			}
		}
		
		if (offset + length < ibo.length) {
			sb.append(" ... ");
		}
		sb.append("]");
		
		if (offset > 0 || offset + length < ibo.length) {
			sb.append(" (" + length + " shorts)");
		}
		return sb.toString();
	}
}
