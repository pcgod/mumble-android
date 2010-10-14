package org.pcgod.mumbleclient.service;

import java.io.IOException;

class UDPPingThread implements Runnable {
	private boolean running = true;
	private final MumbleConnection mc;
	private final byte[] buffer = new byte[5];

	public UDPPingThread(final MumbleConnection mc_) {
		this.mc = mc_;

		// Type: Ping
		this.buffer[0] = 1 << 5;
	}

	public final void run() {
		while (running && mc.isConnectionAlive()) {
			try {
				final long timestamp = System.currentTimeMillis();
				this.buffer[1] = (byte) (timestamp >> 24);
				this.buffer[2] = (byte) (timestamp >> 16);
				this.buffer[3] = (byte) (timestamp >> 8);
				this.buffer[4] = (byte) (timestamp);

				mc.sendUdpTunnelMessage(this.buffer, 5, true);
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
