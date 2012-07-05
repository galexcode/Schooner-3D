package com.supermercerbros.gameengine.engine;


import android.content.Context;
import android.util.Log;

import com.supermercerbros.gameengine.Schooner3D;
import com.supermercerbros.gameengine.debug.LoopLog;
import com.supermercerbros.gameengine.engine.shaders.ShaderLib;

/**
 * Used for communication between the main thread, the Engine thread, and the
 * renderer thread.
 */
public class DataPipe {
	private static final String TAG = DataPipe.class.getSimpleName();

	final int VBO_capacity = Schooner3D.vboSize;
	final int IBO_capacity = Schooner3D.iboSize;

	private RenderData data;
	private long lastReadTime;
	private boolean hasData = false;

	/**
	 * Constructs a new DataPipe. This also initializes <code>ShaderLib</code>
	 * and <code>TextureLib</code>
	 * 
	 * @param context
	 *            The app Context
	 * @param mtl
	 *            The material to render GameObjects with.
	 */
	public DataPipe(Context context) {
		ShaderLib.init();
		TextureLib.init(context);
	}

	public void close() {
		TextureLib.close();
		ShaderLib.close();
		EGLContextLostHandler.clear();
	}

	/**
	 * Loads the data for the next frame into the DataPipe. Also returns the
	 * time of the frame to compute next.
	 * 
	 * @param frameTime
	 *            the time of the frame represented by the data
	 * @param newData
	 *            a RenderData object containing the data to be rendered.
	 * @return The time of the next frame that the Engine should calculate
	 */
	public synchronized long putData(Engine engine, RenderData newData) {
		this.data = newData;
		hasData = true;
		LoopLog.i(TAG, "DataPipe now contains RD " + data.index);
		notify();
		while (hasData) {
			try {
				wait(); //1000 / 30);
			} catch (InterruptedException e) {
				if (engine.isEnding()) {
					Log.i(TAG, "Engine was interrupted while waiting in DataPipe.");
					break;
				}
			}
		}
		return lastReadTime + (1000/30);
	}

	public synchronized RenderData retrieveData() {
		while (!hasData) {
			try {
				wait();
			} catch (InterruptedException e) {
			}
		}
		final RenderData ldata = this.data;
		lastReadTime = System.currentTimeMillis();
		hasData = false;
		notify();
		LoopLog.i(TAG, "DataPipe was read. Renderer now has RD " + ldata.index);
		return ldata;
	}
}
