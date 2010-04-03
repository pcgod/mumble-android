package org.pcgod.mumbleclient;

import android.app.Activity;
import android.os.Bundle;

public class main extends Activity {
	private Thread clientThread;

	public final void onCreate(Bundle savedInstanceState) {
		System.loadLibrary("celt_interface");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		final MumbleClient mc = new MumbleClient("srv01.2jam.de", 64739,
				"test123", "");
		clientThread = new Thread(mc, "net");
		clientThread.start();
	}
}
