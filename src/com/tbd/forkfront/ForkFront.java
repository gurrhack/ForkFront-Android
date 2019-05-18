/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tbd.forkfront;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.preference.PreferenceManager;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import com.tbd.forkfront.Input.Modifier;

import java.io.File;
import java.util.EnumSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ForkFront extends Activity
{
	private static NH_State nhState;
	private boolean mCtrlDown;
	private boolean mMetaDown;
	private boolean mBackTracking;

	private final int SETTINGS_ACTIVITY_CODE = 42;
	private final int REQUEST_EXTERNAL_STORAGE = 43;

	public interface RequestExternalStorageResult {
		void onGranted();
		void onDenied();
	}
	private RequestExternalStorageResult mRequestExternalStorageResult;

	// ____________________________________________________________________________________
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		Log.print("onCreate");

		if(DEBUG.isOn())
		{
			if(getResources().getString(R.string.namespace).length() == 0
			|| getResources().getString(R.string.nativeDataDir).length() == 0
			|| getResources().getString(R.string.libraryName).length() == 0
			|| getResources().getString(R.string.defaultsFile).length() == 0
			|| getResources().getString(R.string.defaultCmdPanel).length() == 0)
				throw new RuntimeException("missing config vars");
			if(getResources().getBoolean(R.bool.hearseAvailable))
			{
				if(getResources().getString(R.string.hearseClientName).length() == 0
				|| getResources().getString(R.string.hearseNethackVersion).length() == 0
				|| getResources().getString(R.string.hearseRoles).length() == 0)
					throw new RuntimeException("missing config vars");
			}
		}
		// turn off the window's title bar
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setDefaultKeyMode(DEFAULT_KEYS_DISABLE);
		// takeKeyEvents(true);

		setContentView(R.layout.mainwindow);

		ensureReadWritePermissions(new RequestExternalStorageResult() {
			@Override
			public void onGranted() {
				goodToGo();
			}

			@Override
			public void onDenied() {
				finish();
			}
		});
	}

	private void goodToGo() {
		if(nhState == null)
		{
			ByteDecoder decoder;
			if(getResources().getBoolean(R.bool.useCP437Decoder))
				decoder = new CP437();
			else
				decoder = new ByteDecoder() {
					@Override
					public char decode(int b) {
						return (char)b;
					}

					@Override
					public String decode(byte[] bytes) {
						return new String(bytes);
					}
				};

			nhState = new NH_State(this, decoder);
			new UpdateAssets(this, onAssetsReady).execute((Void[])null);
		}
		else
		{
			Log.print("restoring state");
			nhState.setContext(this);
		}
	}

	@TargetApi(Build.VERSION_CODES.M)
	public void ensureReadWritePermissions(RequestExternalStorageResult requestExternalStorageResult)
	{
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
		{
			if(checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED)
			{
				if(mRequestExternalStorageResult == null)
				{
					mRequestExternalStorageResult = requestExternalStorageResult;
					requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_EXTERNAL_STORAGE);
				}
				else
				{
					// Chain callbacks if several requests are activated in parallel. This shouldn't happen though.
					RequestExternalStorageResult prevRequest = mRequestExternalStorageResult;
					mRequestExternalStorageResult = new RequestExternalStorageResult()
					{
						@Override
						public void onGranted()
						{
							prevRequest.onGranted();
							requestExternalStorageResult.onGranted();
						}

						@Override
						public void onDenied()
						{
							prevRequest.onDenied();
							requestExternalStorageResult.onDenied();
						}
					};
				}
			}
			else
			{
				requestExternalStorageResult.onGranted();
			}
		}
		else
		{
			requestExternalStorageResult.onGranted();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		Log.print("onRequestPermissionsResult");
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if(requestCode == REQUEST_EXTERNAL_STORAGE)
		{
			if(permissions.length == 1 && permissions[0].equals(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
			&& grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				mRequestExternalStorageResult.onGranted();
			} else {
				mRequestExternalStorageResult.onDenied();
			}
		}
	}

	// ____________________________________________________________________________________
	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		Log.print("onConfigurationChanged");
		nhState.onConfigurationChanged(newConfig);
		super.onConfigurationChanged(newConfig);
	}

	// ____________________________________________________________________________________
	private UpdateAssets.Listener onAssetsReady = new UpdateAssets.Listener()
	{
		@Override
		public void onAssetsReady(File path)
		{
			// Create save directory if it doesn't exist
			File nhSaveDir = new File(path, "save");
			if(!nhSaveDir.exists())
				nhSaveDir.mkdir();

			PreferenceManager.setDefaultValues(ForkFront.this, R.xml.preferences, false);
			nhState.startNetHack(path.getAbsolutePath());
		}
	};

	// ____________________________________________________________________________________
	@Override
	protected void onStart()
	{
		mCtrlDown = false;
		mMetaDown = false;

		Log.print("onStart");
		if(DEBUG.runTrace())
			Debug.startMethodTracing("nethack");
		super.onStart();
	}

	// ____________________________________________________________________________________
	@Override
	protected void onResume()
	{
		mCtrlDown = false;
		mMetaDown = false;

		Log.print("onResume");
		super.onResume();
	}

	// ____________________________________________________________________________________
	@Override
	protected void onPause()
	{
		mCtrlDown = false;
		mMetaDown = false;

		super.onPause();
	}

	// ____________________________________________________________________________________
	@Override
	protected void onStop()
	{
		mCtrlDown = false;
		mMetaDown = false;

		Log.print("onStop");
		super.onStop();
	}

	// ____________________________________________________________________________________
	@Override
	protected void onDestroy()
	{
		mCtrlDown = false;
		mMetaDown = false;

		Log.print("onDestroy()");
		if(nhState != null)
            nhState.saveAndQuit();
		nhState = null;

		super.onDestroy();
	}

	// ____________________________________________________________________________________
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		mCtrlDown = false;
		mMetaDown = false;

		Log.print("onCreateOptionsMenu");
		menu.add(0, 1, 0, "Settings");

		return super.onCreateOptionsMenu(menu);
	}

	// ____________________________________________________________________________________
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		mCtrlDown = false;
		mMetaDown = false;

		Log.print(String.format("onOptionsItemSelected(item=%d)", item.getItemId()));
		if(item.getItemId() == 1)
		{
			Intent prefsActivity = new Intent(getBaseContext(), Settings.class);
			startActivityForResult(prefsActivity, SETTINGS_ACTIVITY_CODE);
			return true;
		}

		return false;
	}

	// ____________________________________________________________________________________
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
	{
		mCtrlDown = false;
		mMetaDown = false;

		super.onCreateContextMenu(menu, v, menuInfo);
		nhState.onCreateContextMenu(menu, v);
	}

	// ____________________________________________________________________________________
	public void onContextMenuClosed(Menu menu) {
		super.onContextMenuClosed(menu);
		nhState.onContextMenuClosed();
	}

	// ____________________________________________________________________________________
	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		mCtrlDown = false;
		mMetaDown = false;

		nhState.onContextItemSelected(item);
		return super.onContextItemSelected(item);
	}

	// ____________________________________________________________________________________
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		mCtrlDown = false;
		mMetaDown = false;

		if(requestCode == SETTINGS_ACTIVITY_CODE)
		{
			nhState.preferencesUpdated();
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	// ____________________________________________________________________________________
	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		mCtrlDown = false;
		mMetaDown = false;

		Log.print("onSaveInstanceState(Bundle outState)");
		if(nhState != null)
			nhState.saveState();
	}

	// ____________________________________________________________________________________
	@Override
	public boolean dispatchKeyEvent(KeyEvent event)
	{
		// Handle back key long press manually
		if(event.getKeyCode() == KeyEvent.KEYCODE_BACK)
		{
			if(event.getAction() == KeyEvent.ACTION_DOWN)
			{
				if(event.getRepeatCount() == 0)
				{
					mBackTracking = true;
				}
				else if(mBackTracking && event.isLongPress())
				{
					Intent prefsActivity = new Intent(getBaseContext(), Settings.class);
					startActivityForResult(prefsActivity, SETTINGS_ACTIVITY_CODE);
					mBackTracking = false;
				}
			}
			else if(event.getAction() == KeyEvent.ACTION_UP)
			{
				if(mBackTracking && !event.isCanceled())
				{
					EnumSet<Modifier> modifiers = Input.modifiersFromKeyEvent(event);
					handleKeyDown(event.getKeyCode(), event.getUnicodeChar(), event.getRepeatCount(), modifiers);
				}
				mBackTracking = false;
			}
			return true;
		}
		mBackTracking = false;

		if(event.getAction() == KeyEvent.ACTION_DOWN)
		{
			EnumSet<Modifier> modifiers = Input.modifiersFromKeyEvent(event);
			if(handleKeyDown(event.getKeyCode(), event.getUnicodeChar(), event.getRepeatCount(), modifiers))
				return true;
		}

		return super.dispatchKeyEvent(event);
	}

	// ____________________________________________________________________________________
	public boolean handleKeyDown(int keyCode, int unicodeChar, int repeatCount, EnumSet<Modifier> modifiers)
	{
		int fixedCode = Input.keyCodeToAction(keyCode, this);

		if(fixedCode == KeyEvent.KEYCODE_VOLUME_DOWN || fixedCode == KeyEvent.KEYCODE_VOLUME_UP)
			return false;

		if(fixedCode == KeyAction.Control)
			mCtrlDown = true;
		else if(fixedCode == KeyAction.Meta)
			mMetaDown = true;

		if(mCtrlDown)
			modifiers.add(Modifier.Control);
		else if(mMetaDown)
			modifiers.add(Modifier.Meta);

		char ch = (char)unicodeChar;

		int nhKey = Input.nhKeyFromKeyCode(fixedCode, ch, modifiers, nhState.isNumPadOn());
		
		if(nhState.handleKeyDown(ch, nhKey, fixedCode, modifiers, repeatCount, false))
			return true;

		if(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)
		{
			// Prevent default system sound from playing
			return true;
		}
		return false;
	}

	// ____________________________________________________________________________________
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event)
	{
		int fixedCode = Input.keyCodeToAction(keyCode, this);

		if(fixedCode == KeyEvent.KEYCODE_VOLUME_DOWN || fixedCode == KeyEvent.KEYCODE_VOLUME_UP)
			return false;

		if(fixedCode == KeyAction.Control)
			mCtrlDown = false;
		else if(fixedCode == KeyAction.Meta)
			mMetaDown = false;

		if(nhState.handleKeyUp(Input.keyCodeToAction(keyCode, this)))
			return true;
		if(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)
		{
			// Prevent default system sound from playing
			return true;
		}
		return super.onKeyUp(keyCode, event);
	}
}
