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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Iterator;
import java.util.LinkedList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.opengl.GLES20;
import android.opengl.GLException;
import android.opengl.GLSurfaceView.Renderer;
import android.opengl.GLU;
import android.opengl.Matrix;
import android.util.Log;

import com.supermercerbros.gameengine.Schooner3D;
import com.supermercerbros.gameengine.engine.shaders.Material;
import com.supermercerbros.gameengine.engine.shaders.Program;
import com.supermercerbros.gameengine.engine.shaders.ShaderLib;
import com.supermercerbros.gameengine.hud.GameHud;
import com.supermercerbros.gameengine.objects.Metadata;
import com.supermercerbros.gameengine.render.Compositor;
import com.supermercerbros.gameengine.util.GLES2;
import com.supermercerbros.gameengine.util.Utils;

public class GameRenderer implements Renderer {
	private static final String TAG = GameRenderer.class.getName();
	private static final boolean CHECK_ERRORS = true;

	/**
	 * @param location
	 *            A string that names the just-called method.
	 * @return The GL_ code of the error.
	 */
	public static int logError(String location) {
		final int error = GLES20.glGetError();
		if (CHECK_ERRORS && error != GLES20.GL_NO_ERROR) {
			Log.e("OpenGL", location + ": " + GLU.gluErrorString(error) + " (error code 0x" + Integer.toHexString(error) + ")");			
		}
		return error;
	}

	private final DataPipe pipe;
	private final FloatBuffer vbo; // Vertex Buffer Object
	private final ShortBuffer ibo; // Index Buffer Object

	private int arrayBuffer;
	private int elementBuffer;

	private float[] wvpMatrix = new float[16];
	private float[] projMatrix = new float[16];

	// Shader variable handles
	private int u_viewProj = -1;
	private int u_lightVec = -1;
	private int u_lightColor = -1;

	private float near, far;
	private float aspect;

	// HUD stuff
	private GameHud hud;
	private boolean hasHud = false;
	private boolean isHudLoaded = false;
	
	private Compositor compositor;
	private boolean hasCompositor = false;
	private boolean isCompositorLoaded = false;

	private long frameCount = 0;
	private long lastCalcTime;
	private static final long frameRateCalcAt = 120;

	/**
	 * Constructs a new GameRenderer.
	 * 
	 * @param pipe
	 *            The DataPipe to use to communicate with an Engine
	 * @param near
	 *            The near clipping distance
	 * @param far
	 *            The far clipping distance
	 */
	public GameRenderer(DataPipe pipe, float near, float far) {
		Log.d(TAG, "Constructing GameRenderer...");
		this.pipe = pipe;

		Matrix.setIdentityM(projMatrix, 0);
		Matrix.setIdentityM(wvpMatrix, 0);

		vbo = ByteBuffer.allocateDirect(pipe.VBO_capacity)
				.order(ByteOrder.nativeOrder()).asFloatBuffer();
		ibo = ByteBuffer.allocateDirect(pipe.IBO_capacity)
				.order(ByteOrder.nativeOrder()).asShortBuffer();

		this.near = near;
		this.far = far;
		Log.d(TAG, "GameRenderer constructed!");
	}

	@Override
	public void onDrawFrame(GL10 unused) {
		GLES20.glClearColor(Schooner3D.backgroundColor[0],
				Schooner3D.backgroundColor[1], Schooner3D.backgroundColor[2],
				Schooner3D.backgroundColor[3]);

		// Setup compositor
		if (hasCompositor && isCompositorLoaded) {
			compositor.preDraw();
			logError("compositor preDraw");
		}
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
		
		final RenderData in = pipe.retrieveData();

		// Bind buffers
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, arrayBuffer);
		GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, elementBuffer);
		
		// Load VBO data
		if (in.vboRange.needsToBeUpdated()) {
			final int start = in.vboRange.start;
			final int length = in.vboRange.end - start;
			vbo.position(start);
			vbo.put(in.vbo, start, length);
			vbo.position(start);
			GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, start * 4, length * 4,
					vbo);
			in.vboRange.reset();
		}
		
		// Load IBO data
		if (in.iboRange.needsToBeUpdated()) {
			final int start = in.iboRange.start;
			final int length = in.iboRange.end - start;
			ibo.position(start);
			ibo.put(in.ibo, start, length);
			ibo.position(start);
			GLES20.glBufferSubData(GLES20.GL_ELEMENT_ARRAY_BUFFER, start * 2,
					length * 2, ibo);
			in.iboRange.reset();
		}

		// Render each primitive
		Iterator<float[]> matrixIter = in.modelMatrices.iterator();
		final LinkedList<Metadata> primitives = in.primitives;
		final int inIndexOffset = in.index * 2;
		for (final Metadata primitive : primitives) {
			// Ensure depth test is enabled
			GLES20.glEnable(GLES20.GL_DEPTH_TEST);
			logError("glEnable (DEPTH)");
			
			// Error checks
			if (primitive == null) {
				Log.e(TAG, "primitive == null");
				continue;
			}
			final Material material = primitive.mtl;
			if (material == null) {
				Log.e(TAG, "primitive.mtl == null");
				continue;
			}
			final Program program = material.getProgram();
			if (program == null) {
				Log.e(TAG, "program == null");
				continue;
			}
			
			// Load program
			try {
				program.load();
			} catch (GLException e) {
				Log.e(TAG, "Program could not be loaded.", e);
				throw new RuntimeException(e); // TODO: remove after debug?
//					continue; // Is there something better to do here?
			}
			GLES20.glUseProgram(program.getHandle());
			
			// Load uniforms
			u_viewProj = program.getUniformLocation(ShaderLib.U_VIEWPROJ);
			u_lightVec = program.getUniformLocation(ShaderLib.U_LIGHTVEC);
			u_lightColor = program
					.getUniformLocation(ShaderLib.U_LIGHTCOLOR);

			// Load World View-Projection matrix
			Matrix.multiplyMM(wvpMatrix, 0, projMatrix, 0, in.viewMatrix, 0);
			GLES20.glUniformMatrix4fv(u_viewProj, 1, false, wvpMatrix, 0);
			logError("glUniformMatrix4fv (wvpMatrix)");

			// Load directional light
			final Light light = in.light;
			if (u_lightVec != -1) {
				GLES20.glUniform3f(u_lightVec, light.x, light.y, light.z);
				logError("glUniform3fv (light vector)");
			}
			if (u_lightColor != -1) {
				GLES20.glUniform3f(u_lightColor, light.r, light.g, light.b);
				logError("glUniform3fv (light color)");
			}

			// Material-specific stuff
			final int[] bufferLocations = primitive.bufferLocations;
			material.attachAttribs(primitive,
					bufferLocations[inIndexOffset] * 4,
					matrixIter.next());

			// Render primitive!
			GLES2.glDrawElements(material.getGeometryType(), primitive.size,
					GLES20.GL_UNSIGNED_SHORT,
					bufferLocations[inIndexOffset + 1] * 2);
			logError("DrawElements");
		}

		// Render Compositor
		if (hasCompositor && isCompositorLoaded) {
			compositor.postDraw();
			logError("compositor postDraw");
		}

		// Render HUD
		synchronized (this) {
			if (hasHud) {
				if (!isHudLoaded) {
					hud.load();
				}
				hud.render();
			}
		}

		// FPS calculation
		frameCount++;
		if (frameCount >= frameRateCalcAt) {
			final long currentTime = System.currentTimeMillis();
			final long timeDelta = currentTime - lastCalcTime;
			final double fps = (1000 * frameRateCalcAt) / (double) timeDelta;
			Log.i(TAG, fps + " FPS");
			frameCount = 0;
			lastCalcTime = currentTime;
		}
	}

	@Override
	public void onSurfaceChanged(GL10 unused, int width, int height) {
		GLES20.glViewport(0, 0, width, height);
		aspect = width / (float) height;
		projMatrix(projMatrix);

		if (hasCompositor) {
			compositor.onSurfaceChanged(width, height);
			isCompositorLoaded = true;
		}

		frameCount = 0;
		lastCalcTime = System.currentTimeMillis();
	}

	/**
	 * Writes this GameRenderer's projection matrix to the given float array.
	 */
	public void projMatrix(float[] matrix) {
		Utils.perspectiveM(matrix, 0, 45, aspect, near, far);
	}

	@Override
	public void onSurfaceCreated(GL10 unused, EGLConfig config) {
		EGLContextLostHandler.contextLost();

		// Generate buffers
		final int[] buffers = new int[2];
		GLES20.glGenBuffers(2, buffers, 0);
		final int localArrayBuffer = buffers[0];
		final int localElementBuffer = buffers[1];

		// Bind buffers
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, localArrayBuffer);
		GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, localElementBuffer);

		// Initialize buffers
		GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, pipe.VBO_capacity, vbo,
				GLES20.GL_DYNAMIC_DRAW);
		GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, pipe.IBO_capacity,
				ibo, GLES20.GL_DYNAMIC_DRAW);

		// Store handles as fields
		arrayBuffer = localArrayBuffer;
		elementBuffer = localElementBuffer;

		// Initialize HUD
		synchronized (this) {
			if (hasHud && !isHudLoaded) {
				hud.load();
			}
		}
	}

	/**
	 * Sets the GameHud to render over the game.
	 * 
	 * @param hud
	 */
	public synchronized void setHud(GameHud hud) {
		if (hasHud && isHudLoaded) {
			this.hud.unload();
		}
		this.hud = hud;
		this.hasHud = hud != null;
		this.isHudLoaded = false;
	}

	public void setCompositor(Compositor c) {
		if (compositor != null) {
			throw new IllegalStateException("A Compositor has already been set");
		} else {
			compositor = c;
			hasCompositor = true;
		}
	}

}
