package org.pcgod.mumbleclient;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.preference.PreferenceManager;

public class Settings {
	public static final String PREF_STREAM = "stream";
	public static final String ARRAY_STREAM_MUSIC = "music";
	public static final String ARRAY_STREAM_CALL = "call";

	public static final String PREF_JITTER = "buffering";
	public static final String ARRAY_JITTER_NONE = "none";
	public static final String ARRAY_JITTER_SPEEX = "speex";

	public static final String PREF_QUALITY = "quality";
	private static final String DEFAULT_QUALITY = "60000";
	
	public static final String PREF_PTT_KEY = "pttkey";
	
	public static final String PREF_EVENT_SOUNDS = "eventsounds";

	public static final String PREF_PROXIMITY = "proximity";
	
	public static final String PREF_TTS = "tts";
	
	public static final String PREF_BACKGROUND_SERVICE = "bgservice";

	private final SharedPreferences preferences;

	public Settings(final Context ctx) {
		preferences = PreferenceManager.getDefaultSharedPreferences(ctx);
	}

	public int getAudioQuality() {
		return Integer.parseInt(preferences.getString(Settings.PREF_QUALITY, DEFAULT_QUALITY));
	}

	public int getAudioStream() {
		return preferences.getString(PREF_STREAM, ARRAY_STREAM_MUSIC).equals(
			ARRAY_STREAM_MUSIC) ? AudioManager.STREAM_MUSIC
			: AudioManager.STREAM_VOICE_CALL;
	}
	
	public int getPttKey() {
		return Integer.parseInt(preferences.getString(PREF_PTT_KEY, "-1"));
	}

	public boolean isJitterBuffer() {
		return preferences.getString(PREF_JITTER, ARRAY_JITTER_NONE).equals(
			ARRAY_JITTER_SPEEX);
	}
	
	public boolean isEventSoundsEnabled() {
		return preferences.getBoolean(PREF_EVENT_SOUNDS, true);
	}

	public boolean isProximityEnabled() {
		return preferences.getBoolean(PREF_PROXIMITY, false);
	}
	
	public boolean isTtsEnabled() {
		return preferences.getBoolean(PREF_TTS, true);
	}
	
	public boolean isBackgroundServiceEnabled() {
		return preferences.getBoolean(PREF_BACKGROUND_SERVICE, true);
	}
}
