package org.pcgod.mumbleclient;

import android.util.Log;

/**
 * Constant global values
 *
 * @author Rantanen
 */
public class Globals {
	private static final String LOG_TAG = "mumbleclient";
	public static final int PROTOCOL_VERSION = (1 << 16) | (2 << 8) |
											   (3 & 0xFF);
	public static final int CELT_VERSION = 0x8000000b;

	public static void logDebug(Object src, String msg) {
		Log.d(LOG_TAG, formatMsg(src, msg));
	}

	public static void logError(Object src, String msg) {
		Log.e(LOG_TAG, formatMsg(src, msg));
	}

	public static void logError(Object src, String msg, Throwable ex) {
		Log.e(LOG_TAG, formatMsg(src, msg), ex);
	}

	public static void logInfo(Object src, String msg) {
		Log.i(LOG_TAG, formatMsg(src, msg));
	}

	public static void logWarn(Object src, String msg) {
		Log.w(LOG_TAG, formatMsg(src, msg));
	}

	public static void logWarn(Object src, String msg, Throwable ex) {
		Log.w(LOG_TAG, formatMsg(src, msg), ex);
	}

	private static String formatMsg(Object src, String msg) {
		return String.format(
			"%s: %s (%H)",
			src.getClass().getName(),
			msg,
			src.hashCode());

	}
}
