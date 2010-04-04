package org.pcgod.mumbleclient.app;

import java.io.IOException;
import java.util.ArrayList;

import org.pcgod.mumbleclient.MumbleClient;
import org.pcgod.mumbleclient.PacketDataStream;
import org.pcgod.mumbleclient.jni.SWIGTYPE_p_CELTEncoder;
import org.pcgod.mumbleclient.jni.SWIGTYPE_p_CELTMode;
import org.pcgod.mumbleclient.jni.celt;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

public class RecordThread implements Runnable {
	private AudioRecord ar;
	private short[] buffer;
	private int frameLimit = 2;
	SWIGTYPE_p_CELTMode cm;
	SWIGTYPE_p_CELTEncoder ce;
	private static final int AUDIO_QUALITY = 60000;
	private int seq;
	ArrayList<short[]> outputList = new ArrayList<short[]>();

	public RecordThread() {
		int bufferSize = AudioRecord.getMinBufferSize(
				MumbleClient.SAMPLE_RATE - 40000,
				AudioFormat.CHANNEL_CONFIGURATION_MONO,
				AudioFormat.ENCODING_PCM_16BIT);

		Log.i("mumbleclient", "buffersize: " + bufferSize);

		ar = new AudioRecord(MediaRecorder.AudioSource.MIC,
				MumbleClient.SAMPLE_RATE - 40000,
				AudioFormat.CHANNEL_CONFIGURATION_MONO,
				AudioFormat.ENCODING_PCM_16BIT, bufferSize);

		buffer = new short[bufferSize];
		int[] error = new int[1];
		cm = celt.celt_mode_create(MumbleClient.SAMPLE_RATE,
				MumbleClient.FRAME_SIZE, error);
		ce = celt.celt_encoder_create(cm, 1, error);
		celt.celt_encoder_ctl(ce, celt.CELT_SET_PREDICTION_REQUEST, 0);
		celt
				.celt_encoder_ctl(ce, celt.CELT_SET_VBR_RATE_REQUEST,
						AUDIO_QUALITY);
	}

	@Override
	public void run() {
		int limit = 480 * 1;
		while (true) {
			int offset = 0;

			do {
				int read = ar.read(buffer, offset, limit);
				offset += read;
			} while (offset < limit);

			int compressedSize = Math.min(AUDIO_QUALITY / (100 * 8), 127);
			short[] compressed = new short[compressedSize];
			/* int len = */celt.celt_encode(ce, buffer, null, compressed,
					compressedSize);
			outputList.add(compressed);

			if (outputList.size() >= frameLimit) {
				byte[] tmpBuffer = new byte[1024];
				int flags = 0;
				// 0 = MumbleClient.UDPMessageType.UDPVoiceCELTAlpha
				flags |= 0 << 5;
				PacketDataStream pds = new PacketDataStream(tmpBuffer);
				seq += frameLimit;
				pds.writeLong(seq);
				for (int i = 0; i < frameLimit; ++i) {
					short[] tmp = outputList.get(i);
					short head = (short) tmp.length;
					if (i < frameLimit - 1)
						head |= 0x80;

					pds.writeLong(head);
					pds.append(tmp);
				}
				byte[] tmp2 = new byte[pds.size() + 1];
				System.arraycopy(tmpBuffer, 0, tmp2, 1, tmp2.length - 1);
				tmp2[0] = (byte) flags;
				try {
					ServerList.client.sendUdpTunnelMessage(tmp2);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	protected void finalize() {
		celt.celt_encoder_destroy(ce);
		celt.celt_mode_destroy(cm);
	}
}
