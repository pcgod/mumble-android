package org.pcgod.mumbleclient.service.audio;

class JitterBufferPacket {
	public byte[] data;
	public int timestamp;
	public int span;
	public int flags;
}
