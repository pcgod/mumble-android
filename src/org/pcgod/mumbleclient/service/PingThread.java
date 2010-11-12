package org.pcgod.mumbleclient.service;

import net.sf.mumble.MumbleProto.Ping;

class PingThread implements Runnable {
	private boolean running = true;
	private final MumbleConnection mc;
	private final byte[] udpBuffer = new byte[9];

	public PingThread(final MumbleConnection mc_) {
		this.mc = mc_;

		// Type: Ping
		udpBuffer[0] = MumbleProtocol.UDPMESSAGETYPE_UDPPING << 5;
	}

	@Override
	public final void run() {
		while (running && mc.isConnectionAlive()) {
			try {
				final long timestamp = System.currentTimeMillis();

				// TCP
				final Ping.Builder p = Ping.newBuilder();
				p.setTimestamp(timestamp);
				mc.sendTcpMessage(MumbleProtocol.MessageType.Ping, p);

				// UDP
				udpBuffer[1] = (byte) ((timestamp >> 56) & 0xFF);
				udpBuffer[2] = (byte) ((timestamp >> 48) & 0xFF);
				udpBuffer[3] = (byte) ((timestamp >> 40) & 0xFF);
				udpBuffer[4] = (byte) ((timestamp >> 32) & 0xFF);
				udpBuffer[5] = (byte) ((timestamp >> 24) & 0xFF);
				udpBuffer[6] = (byte) ((timestamp >> 16) & 0xFF);
				udpBuffer[7] = (byte) ((timestamp >> 8) & 0xFF);
				udpBuffer[8] = (byte) ((timestamp) & 0xFF);

				mc.sendUdpMessage(udpBuffer, udpBuffer.length, true);
				Thread.sleep(5000);
			} catch (final InterruptedException e) {
				e.printStackTrace();
				running = false;
			}
		}
	}
}
