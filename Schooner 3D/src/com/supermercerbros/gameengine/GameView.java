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
package com.supermercerbros.gameengine;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

import com.supermercerbros.gameengine.hud.GameHud;

/**
 * 
 */
class GameView extends GLSurfaceView {
	private static final String TAG = "GameView";
	private GameHud hud;

	/**
	 * @see GLSurfaceView#GLSurfaceView(Context)
	 */
	GameView(Context context) {
		super(context);
		setEGLContextClientVersion(2);
	}
	
	/**
	 * Sets the GameHud to use in this GameView
	 * @param hud
	 */
	void setHud(GameHud hud) {
		this.hud = hud;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (hud == null) {
			return false;
		} else {
			hud.onTouchEvent(event);
			return true;
		}
	}
}
