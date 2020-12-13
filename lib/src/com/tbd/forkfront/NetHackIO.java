package com.tbd.forkfront;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.app.Activity;
import android.os.Environment;
import android.os.Handler;

public class NetHackIO
{
	private final Handler mHandler;
	private final NH_Handler mNhHandler;
	private final Thread mThread;
	private final ByteDecoder mDecoder;
	private final String mLibraryName;
	private final ConcurrentLinkedQueue<Cmd> mCmdQue;
	private int mNextWinId;
	private int mMessageWid;
	private volatile Integer mIsReady = 0;
	private final Object mReadyMonitor = new Object();
	private String mDataDir;

	// ____________________________________________________________________________________ //
	// Send commands																		//
	// ____________________________________________________________________________________ //
	// TODO make responses explicit on requests instead of queuing them up
	enum CmdType {
		KEY,
		POS,
		LINE,
		SELECT,
		SAVE_STATE,
		ABORT;
	}

	private interface Cmd {
		CmdType type();
	}

	private static class AbortCmd implements Cmd {
		@Override
		public CmdType type() {
			return CmdType.ABORT;
		}
	}

	private static class SaveStateCmd implements Cmd {
		@Override
		public CmdType type() {
			return CmdType.SAVE_STATE;
		}
	}

	private static class KeyCmd implements Cmd {
		private final char key;

		KeyCmd(char key) {
			this.key = key;
		}

		@Override
		public CmdType type() {
			return CmdType.KEY;
		}
	}

	private static class PosCmd implements Cmd {
		private final char key;
		private final int x, y;

		private PosCmd(char key) {
			this.key = key;
			this.x = 0;
			this.y = 0;
		}

		public PosCmd(int x, int y) {
			key = 0;
			this.x = x;
			this.y = y;
		}

		@Override
		public CmdType type() {
			return CmdType.POS;
		}
	}

	private static class LineCmd implements Cmd {

		private final String line;

		private LineCmd(String line) {
			this.line = line;
		}

		@Override
		public CmdType type() {
			return CmdType.LINE;
		}
	}

	private static class Item {
		final int count;
		final long id;

		private Item(long id, int count) {
			this.id = id;
			this.count = count;
		}
	}

	private static class SelectCmd implements Cmd {
		long[] items;

		SelectCmd(long id, int count) {
			items = new long[]{id, count};
		}

		SelectCmd(List<MenuItem> items) {
			if(items == null) {
				this.items = null;
			} else {
				this.items = new long[items.size() * 2];
				for(int i = 0; i < items.size(); i++) {
					this.items[i * 2 + 0] = items.get(i).getId();
					this.items[i * 2 + 1] = items.get(i).getCount();
				}
			}
		}


		@Override
		public CmdType type() {
			return CmdType.SELECT;
		}
	}

	// ____________________________________________________________________________________
	public NetHackIO(Activity context, NH_Handler nhHandler, ByteDecoder decoder)
	{
		mNhHandler = nhHandler;
		mDecoder = decoder;
		mLibraryName = context.getResources().getString(R.string.libraryName);
		mNextWinId = 1;
		mCmdQue = new ConcurrentLinkedQueue<>();
		mHandler = new Handler();
		mThread = new Thread(ThreadMain, "nh_thread");
	}

	// ____________________________________________________________________________________
	public void start(String path)
	{
		mDataDir = path;
		mThread.start();
	}

	// ____________________________________________________________________________________
	public void saveState()
	{
		mCmdQue.add(new SaveStateCmd());
		// give it some time
		for(int i = 0; i < 5; i++)
		{
			try
			{
				Thread.sleep(150);
				break;
			}
			catch(InterruptedException e)
			{
			}
		}
	}

	// ____________________________________________________________________________________
	public void saveAndQuit()
	{
		// send a few abort commands to cancel ongoing operations
		sendAbortCmd();
		sendAbortCmd();
		sendAbortCmd();
		sendAbortCmd();

		// give it some time
		for(int i = 0; i < 5; i++)
		{
			try
			{
				Thread.sleep(150);
				break;
			}
			catch(InterruptedException e)
			{
			}
		}
	}

	// ____________________________________________________________________________________
	public void waitReady()
	{
		// flush out queue   
		long endTime = System.currentTimeMillis() + 1000;			
		while(mCmdQue.peek() != null && endTime - System.currentTimeMillis() > 0)
			Thread.yield();

		// boolean test = mIsReady == 0;
		// if(test)
		// 	Log.print("TEST:TEST:TEST:");
		
		synchronized(mReadyMonitor)
		{
			try
			{
				// wait until nethack is ready for more input
				do
					mReadyMonitor.wait(10);
				while(mIsReady == 0);
			}
			catch(InterruptedException e)
			{
			}
		}
	}
	
	// ____________________________________________________________________________________
	public Handler getHandler()
	{
		return mHandler;
	}

	// ____________________________________________________________________________________
	private Runnable ThreadMain = new Runnable()
	{
		@Override
		public void run()
		{
			Log.print("start native process");

			try
			{
				System.loadLibrary(mLibraryName);
				RunNetHack(mDataDir);
			}
			catch(Exception e)
			{
				Log.print("EXCEPTED");
			}
			Log.print("native process finished");
			System.exit(0);
		}
	};

	// ____________________________________________________________________________________
	public void sendKeyCmd(char key)
	{
		mNhHandler.hideDPad();
		mCmdQue.add(new KeyCmd(key));
	}

	// ____________________________________________________________________________________
	public void sendDirKeyCmd(char key)
	{
		mNhHandler.hideDPad();
		mCmdQue.add(new PosCmd(key));
	}

	// ____________________________________________________________________________________
	public void sendPosCmd(int x, int y)
	{
		mNhHandler.hideDPad();
		mCmdQue.add(new PosCmd(x, y));
	}

	// ____________________________________________________________________________________
	public void sendLineCmd(String str)
	{
		mCmdQue.add(new LineCmd(str));
	}

	// ____________________________________________________________________________________
	public void sendSelectCmd(long id, int count)
	{
		mCmdQue.add(new SelectCmd(id, count));
	}

	// ____________________________________________________________________________________
	public void sendSelectCmd(ArrayList<MenuItem> items)
	{
		mCmdQue.add(new SelectCmd(items));
	}

	// ____________________________________________________________________________________
	public void sendSelectNoneCmd()
	{
		mCmdQue.add(new SelectCmd(0, 0));
	}

	// ____________________________________________________________________________________
	public void sendCancelSelectCmd()
	{
		mCmdQue.add(new SelectCmd(null));
	}

	// ____________________________________________________________________________________
	private void sendAbortCmd()
	{
		mCmdQue.add(new AbortCmd());
	}

	// ------------------------------------------------------------------------------------
	// Receive commands called from nethack thread
	// ------------------------------------------------------------------------------------

	// ____________________________________________________________________________________
	private Cmd removeFromQue()
	{
		Cmd cmd = mCmdQue.poll();
		while(cmd == null)
		{
			try
			{
				Thread.sleep(50);
			}
			catch(InterruptedException e)
			{
			}
			cmd = mCmdQue.poll();
		}
		return cmd;
	}

	// ____________________________________________________________________________________
	private void handleSpecialCmds(Cmd cmd)
	{
		switch(cmd.type())
		{
		case SAVE_STATE:
			SaveNetHackState();
		break;
		}
	}

	// ____________________________________________________________________________________
	private Cmd discardUntil(CmdType cmd0)
	{
		Cmd cmd;
		do
		{
			cmd = removeFromQue();
			handleSpecialCmds(cmd);
		}while(cmd.type() != cmd0 && cmd.type() != CmdType.ABORT);
		return cmd;
	}

	// ____________________________________________________________________________________
	private Cmd discardUntil(CmdType cmd0, CmdType cmd1)
	{
		Cmd cmd;
		do
		{
			cmd = removeFromQue();
			handleSpecialCmds(cmd);
		}while(cmd.type() != cmd0 && cmd.type() != cmd1 && cmd.type() != CmdType.ABORT);
		return cmd;
	}

	// ____________________________________________________________________________________
	private void incReady()
	{
		synchronized(mReadyMonitor)
		{
			if(mIsReady++ == 0)
				mReadyMonitor.notify();
		}
	}

	// ____________________________________________________________________________________
	private void decReady()
	{
		mIsReady--;
		if(mIsReady < 0)
			throw new RuntimeException();
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private int receiveKeyCmd()
	{
		int key = 0x80;

		incReady();
		
		Cmd cmd = discardUntil(CmdType.KEY);
		if(cmd.type() == CmdType.KEY)
			key = ((KeyCmd)cmd).key;

		decReady();
		return key;
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private int receivePosKeyCmd(int lockMouse, int[] pos)
	{
		incReady();

		if(lockMouse != 0)
		{
			mHandler.post(new Runnable()
			{
				@Override
				public void run()
				{
					mNhHandler.lockMouse();
				}
			});
		}

		Cmd cmd = discardUntil(CmdType.KEY, CmdType.POS);

		int key = 0x80;

		if(cmd.type() == CmdType.KEY) {
			key = ((KeyCmd)cmd).key;
		} else if(cmd.type() == CmdType.POS) {
			key = ((PosCmd)cmd).key;
			pos[0] = ((PosCmd)cmd).x;
			pos[1] = ((PosCmd)cmd).y;
		}
		
		decReady();
		return key;
	}

	// ------------------------------------------------------------------------------------
	// Functions called by nethack thread to schedule an operation on UI thread
	// ------------------------------------------------------------------------------------

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private void debugLog(final byte[] cmsg)
	{
		Log.print(mDecoder.decode(cmsg));
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private void setCursorPos(final int wid, final int x, final int y)
	{
		mHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				mNhHandler.setCursorPos(wid, x, y);
			}
		});
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private void putString(final int wid, final int attr, final byte[] cmsg, final int append, final int color)
	{
		final String msg = mDecoder.decode(cmsg);
		if(wid == mMessageWid)
			Log.print(msg);

		mHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				mNhHandler.putString(wid, attr, msg, append, color);
			}
		});
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private void setHealthColor(final int color)
	{
		mHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				mNhHandler.setHealthColor(color);
			}
		});
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private void redrawStatus()
	{
		mHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				mNhHandler.redrawStatus();
			}
		});
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private void rawPrint(final int attr, final byte[] cmsg)
	{
		final String msg = mDecoder.decode(cmsg);
		Log.print(msg);
		mHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				mNhHandler.rawPrint(attr, msg);
			}
		});
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private void printTile(final int wid, final int x, final int y, final int tile, final int ch, final int col, final int special)
	{
		mHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				mNhHandler.printTile(wid, x, y, tile, ch, col, special);
			}
		});
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private void ynFunction(final byte[] cquestion, final byte[] choices, final int def)
	{
		final String question = mDecoder.decode(cquestion);
		//Log.print("nhthread: ynFunction");
		mHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				//Log.print("uithread: ynFunction");
				mNhHandler.ynFunction(question, choices, def);
			}
		});
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private String getLine(final byte[] title, final int nMaxChars, final int showLog, int reentry)
	{
		if(reentry == 0)
		{
			final String msg = mDecoder.decode(title);
			//Log.print("nhthread: getLine");
			mHandler.post(new Runnable()
			{
				@Override
				public void run()
				{
					//Log.print("uithread: getLine");
					mNhHandler.getLine(msg, nMaxChars, showLog != 0);
				}
			});
		}
		return waitForLine();
	}

	// ____________________________________________________________________________________
	private String waitForLine()
	{
		incReady();

		Cmd cmd = discardUntil(CmdType.LINE);
		String string;
		if(cmd.type() == CmdType.LINE)
		{
			// prevent injecting special abort character
			string = ((LineCmd)cmd).line.replace((char)0x80, '?');
		}
		else {
			string = new String(new char[]{0x80});
		}

		decReady();
		return string;
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private void delayOutput()
	{
		try
		{
			Thread.sleep(50);
		}
		catch(InterruptedException e)
		{
		}
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private int createWindow(final int type)
	{
		final int wid = mNextWinId++;
		if(type == 1)
			mMessageWid = wid;
		mHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				mNhHandler.createWindow(wid, type);
			}
		});
		return wid;
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private void displayWindow(final int wid, final int bBlocking)
	{
		mHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				mNhHandler.displayWindow(wid, bBlocking);
			}
		});
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private void clearWindow(final int wid, final int isRogueLevel)
	{
		mHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				mNhHandler.clearWindow(wid, isRogueLevel);
			}
		});
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private void destroyWindow(final int wid)
	{
		mHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				mNhHandler.destroyWindow(wid);
			}
		});
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private void startMenu(final int wid)
	{
		mHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				mNhHandler.startMenu(wid);
			}
		});
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private void addMenu(final int wid, final int tile, final long id, final int acc, final int groupAcc, final int attr, final byte[] text, final int bSelected, final int color)
	{
		final String msg = mDecoder.decode(text);
		mHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				mNhHandler.addMenu(wid, tile, id, acc, groupAcc, attr, msg, bSelected, color);
			}
		});
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private void endMenu(final int wid, final byte[] prompt)
	{
		final String msg = mDecoder.decode(prompt);
		//Log.print("nhthread: endMenu");
		mHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				//Log.print("uithread: endMenu");
				mNhHandler.endMenu(wid, msg);
			}
		});
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private long[] selectMenu(final int wid, final int how, final int reentry)
	{
		//Log.print("nhthread: selectMenu");
		if(reentry == 0)
		{
			mHandler.post(new Runnable()
			{
				@Override
				public void run()
				{
					//Log.print("uithread: selectMenu");
					mNhHandler.selectMenu(wid, how);
				}
			});
		}
		return waitForSelect();
	}

	// ____________________________________________________________________________________
	private long[] waitForSelect()
	{
		incReady();
		
		long[] items = null;
		
		Cmd cmd = discardUntil(CmdType.SELECT);
		if(cmd.type() == CmdType.SELECT)
			items = ((SelectCmd)cmd).items;
		else
			items = new long[1];

		decReady();
		return items;
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private void cliparound(final int x, final int y, final int playerX, final int playerY)
	{
		mHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				mNhHandler.cliparound(x, y, playerX, playerY);
			}
		});
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private void askDirection()
	{
		mHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				mNhHandler.showDPad();
			}
		});
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private void showLog(final int bBlocking)
	{
		mHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				mNhHandler.showLog(bBlocking);
			}
		});
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private void editOpts()
	{
		mHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				mNhHandler.editOpts();
			}
		});
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private void setUsername(final byte[] username)
	{
		mHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				mNhHandler.setLastUsername(new String(username));
			}
		});
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private void setNumPadOption(final int num_pad)
	{
		mHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				mNhHandler.setNumPadOption(num_pad != 0);
			}
		});
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private String askName(final int nMaxChars, final String[] saves)
	{
		mHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				mNhHandler.askName(nMaxChars, saves);
			}
		});
		return waitForLine();
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private void loadSound(final byte[] filename)
	{
		mHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				mNhHandler.loadSound(new String(filename));
			}
		});
	}

	// ____________________________________________________________________________________
	@SuppressWarnings("unused")
	private void playSound(final byte[] filename, final int volume)
	{
		mHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				mNhHandler.playSound(new String(filename), volume);
			}
		});
	}

	@SuppressWarnings("unused")
	private String getDumplogDir()
	{
		String path = "";
		try
		{
			File file = new File(Environment.getExternalStorageDirectory(), "Documents" + File.separator + "nethack");
			if(!file.exists())
				file.mkdirs();
			path = file.getAbsolutePath();
		} catch(Exception e) {}
		return path;
	}

	// ____________________________________________________________________________________
	private native void RunNetHack(String path);
	private native void SaveNetHackState();
}
