package org.pcgod.mumbleclient.service;

/**
 * Callback interface for Connection to communicate back to the service.
 *
 * @author Rantanen
 *
 */
public interface MumbleConnectionHost {
	public final static int STATE_DISCONNECTED = 0;
	public final static int STATE_CONNECTING = 1;
	public final static int STATE_CONNECTED = 2;

	public void setConnectionState(int state);

	public void setError(final String error);
}
