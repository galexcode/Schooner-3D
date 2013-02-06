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

package com.supermercerbros.gameengine.engine;

public class Light {
	public float x;
	public float y;
	public float z;
	public float r;
	public float g;
	public float b;

	public void copyTo(Light light) {
		if (light == this) {
			return;
		}
		light.x = x;
		light.y = y;
		light.z = z;
		light.r = r;
		light.g = g;
		light.b = b;
	}
}
