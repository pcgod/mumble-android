package org.pcgod.mumbleclient.service.audio;

/**
 * Wrapper class for all the AudioOutput settings. This class allows passing
 * the settings from MumbleService that can read the application preferences
 * through the MumbleConnection to AudioOutput without the MumbleConnection
 * needing to know what is contained in the settings.
 *
 * @author Rantanen
 *
 */
public class AudioOutputSettings {
	public boolean useJitter;
	public int stream;
}
