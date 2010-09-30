package org.pcgod.mumbleclient.service;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.pcgod.mumbleclient.app.RecordThread;
import org.pcgod.mumbleclient.service.model.User;
import org.pcgod.mumbleclient.service.model.Channel;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

/**
 * Service for providing the client an access to the connection.
 *
 * MumbleService manages the MumbleClient connection and provides access to
 * it for binding activities.
 *
 * @author wace
 *
 */
public class MumbleService extends Service {

	public class LocalBinder extends Binder {
		public MumbleService getService() {
			return MumbleService.this;
		}
	}

	MumbleConnection mClient;
	Thread mClientThread;
	Thread mRecordThread;

	private final LocalBinder mBinder = new LocalBinder();

	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	public void setServer(final String host, final int port,
			final String username, final String password) {
		if (mClient != null
				&& mClient.isSameServer(host, port, username, password)
				&& mClient.isConnected()) {
			return;
		}

		if (mClientThread != null) mClientThread.interrupt();

		mClient = new MumbleConnection(this, host, port, username, password);
		mClientThread = new Thread(mClient, "net");
		mClientThread.start();
	}

	public boolean isConnected() {
		return mClient.isConnected();
	}

	public int getCurrentChannel() {
		return mClient.currentChannel;
	}

	public void joinChannel(int channelId) {
		mClient.joinChannel(channelId);
	}

	public boolean canSpeak() {
		return mClient.canSpeak;
	}

	public Collection<User> getUsers() {
		return Collections.unmodifiableCollection(mClient.userArray);
	}

	public List<Channel> getChannelList() {
		return Collections.unmodifiableList( mClient.channelArray );
	}

	public void sendUdpTunnelMessage(byte[] buffer) throws IOException {
		mClient.sendUdpTunnelMessage(buffer);
	}

	public void sendChannelTextMessage(String message) {
		mClient.sendChannelTextMessage(message);
	}

	public boolean isRecording() {
		return (mRecordThread != null);
	}

	public void setRecording(boolean state) {
		if (mRecordThread == null && state) {
			// start record
			// TODO check initialized
			mRecordThread = new Thread(new RecordThread(this), "record");
			mRecordThread.start();
		} else if (mRecordThread != null && !state) {
			// stop record
			mRecordThread.interrupt();
			mRecordThread = null;
		}
	}
}
