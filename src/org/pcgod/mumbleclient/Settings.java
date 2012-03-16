package org.pcgod.mumbleclient;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.preference.PreferenceManager;
import android.media.MediaRecorder;

public class Settings {
	public static final String PREF_STREAM = "stream";
	public static final String ARRAY_STREAM_MUSIC = "music";
	public static final String ARRAY_STREAM_CALL = "call";

	public static final String PREF_JITTER = "buffering";
	public static final String ARRAY_JITTER_NONE = "none";
	public static final String ARRAY_JITTER_SPEEX = "speex";

	public static final String PREF_QUALITY = "quality";

    public static final String PREF_MICDETECT = "microphone";
    public static final String ARRAY_MICDETECT_PHONE = "phone";
    public static final String ARRAY_MICDETECT_HEADSET = "headset";

    private static final String DEFAULT_QUALITY = "60000";
	
	public static final String PREF_PTT_KEY = "pttkey";
	
	public static final String PREF_EVENT_SOUNDS = "eventsounds";
    public static final String PREF_EVENT_SOUNDS_VOL = "eventsounds_volume";
    public static final String DEFAULT_SOUNDS_VOL = "40";

	public static final String PREF_PROXIMITY = "proximity";
	
	public static final String PREF_KEEP_SCREEN_ON = "screenon";
	
	public static final String PREF_FULLSCREEN = "fullscreen";
	
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

    public int isMicSetupDetected(){
        return preferences.getString(PREF_MICDETECT, ARRAY_MICDETECT_HEADSET).equals(
                ARRAY_MICDETECT_PHONE) ? MediaRecorder.AudioSource.CAMCORDER
                : MediaRecorder.AudioSource.MIC;
    }
    
	public boolean isJitterBuffer() {
		return preferences.getString(PREF_JITTER, ARRAY_JITTER_NONE).equals(
			ARRAY_JITTER_SPEEX);
	}
	
	public boolean isEventSoundsEnabled() {
		return preferences.getBoolean(PREF_EVENT_SOUNDS, true);
	}

    public int eventSoundsVolume() {
        return Integer.parseInt(preferences.getString(PREF_EVENT_SOUNDS_VOL, DEFAULT_SOUNDS_VOL));
    }

	public boolean isProximityEnabled() {
		return preferences.getBoolean(PREF_PROXIMITY, false);
	}
	
	public boolean keepScreenOn() {
		return preferences.getBoolean(PREF_KEEP_SCREEN_ON, false);
	}
	
	public boolean fullscreen() {
		return preferences.getBoolean(PREF_FULLSCREEN, false);
	}
	
	public boolean isTtsEnabled() {
		return preferences.getBoolean(PREF_TTS, true);
	}
	
	public boolean isBackgroundServiceEnabled() {
		return preferences.getBoolean(PREF_BACKGROUND_SERVICE, true);
	}
}
