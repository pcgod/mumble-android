package org.pcgod.mumbleclient.service;

import java.io.IOException;

import net.sf.mumble.MumbleProto.Ping;

class PingThread implements Runnable {
	private boolean running = true;
	private final MumbleClient mc;

	public PingThread(final MumbleClient mc_) {
		this.mc = mc_;
	}

	@Override
	public final void run() {
		while (running) {
			try {
				final Ping.Builder p = Ping.newBuilder();
				p.setTimestamp(System.currentTimeMillis());
				mc.sendMessage(MumbleClient.MessageType.Ping, p);
				Thread.sleep(5000);
			} catch (final IOException e) {
				e.printStackTrace();
				running = false;
			} catch (final InterruptedException e) {
				e.printStackTrace();
				running = false;
			}
		}
	}

}
