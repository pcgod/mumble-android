package org.pcgod.mumbleclient;

public class TestClient {
	public static void main(final String[] args) throws Exception {
		final MumbleClient mc = new MumbleClient("srv01.2jam.de", 64739,
				"test123", "");
		mc.run();
	}
}
