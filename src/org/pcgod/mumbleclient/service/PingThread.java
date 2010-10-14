package org.pcgod.mumbleclient.service;

import java.io.IOException;

import net.sf.mumble.MumbleProto.Ping;

class PingThread implements Runnable {
	private boolean running = true;
	private final MumbleConnection mc;
	private final byte[] udpBuffer = new byte[5];

	public PingThread(final MumbleConnection mc_) {
		this.mc = mc_;

		// Type: Ping
		this.udpBuffer[0] = MumbleConnection.UDPMESSAGETYPE_UDPPING << 5;
	}

	@Override
	public final void run() {
		while (running && mc.isConnectionAlive()) {
			try {
				final long timestamp = System.currentTimeMillis();

				// TCP
				final Ping.Builder p = Ping.newBuilder();
				p.setTimestamp(timestamp);
				mc.sendMessage(MumbleConnection.MessageType.Ping, p);

				// UDP
				this.udpBuffer[1] = (byte) (timestamp >> 24);
				this.udpBuffer[2] = (byte) (timestamp >> 16);
				this.udpBuffer[3] = (byte) (timestamp >> 8);
				this.udpBuffer[4] = (byte) (timestamp);

				mc.sendUdpMessage(this.udpBuffer, udpBuffer.length, true);
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

