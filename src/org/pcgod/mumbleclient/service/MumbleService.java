package org.pcgod.mumbleclient.service;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.pcgod.mumbleclient.app.RecordThread;
import org.pcgod.mumbleclient.service.model.Message;
import org.pcgod.mumbleclient.service.model.User;
import org.pcgod.mumbleclient.service.model.Channel;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
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
public class MumbleService extends Service implements MumbleConnectionHost {

	public static final String INTENT_CHANNEL_LIST_UPDATE = "mumbleclient.intent.CHANNEL_LIST_UPDATE";
	public static final String INTENT_CURRENT_CHANNEL_CHANGED = "mumbleclient.intent.CURRENT_CHANNEL_CHANGED";
	public static final String INTENT_USER_LIST_UPDATE = "mumbleclient.intent.USER_LIST_UPDATE";
	public static final String INTENT_CHAT_TEXT_UPDATE = "mumbleclient.intent.CHAT_TEXT_UPDATE";
	public static final String INTENT_CONNECTION_STATE_CHANGED = "mumbleclient.intent.CONNECTION_STATE_CHANGED";

	public static final String EXTRA_MESSAGE = "mumbleclient.extra.MESSAGE";
	public static final String EXTRA_CONNECTION_STATE = "mumbleclient.extra.CONNECTION_STATE";

	public class LocalBinder extends Binder {
		public MumbleService getService() {
			return MumbleService.this;
		}
	}

	private MumbleConnection mClient;
	private Thread mClientThread;
	private Thread mRecordThread;

	private final LocalBinder mBinder = new LocalBinder();

	private ConnectionState state;
	private final List<Message> messages = new LinkedList<Message>();

	@Override
	public void onCreate() {
		super.onCreate();

		state = ConnectionState.Disconnected;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	public void setServer(final String host, final int port,
			final String username, final String password) {
		if (mClient != null
				&& mClient.isSameServer(host, port, username, password)
				&& isConnected()) {
			return;
		}

		if (mClientThread != null) mClientThread.interrupt();

		mClient = new MumbleConnection(this, host, port, username, password);
		mClientThread = new Thread(mClient, "net");
		mClientThread.start();
	}

	public boolean isConnected() {
		return state == ConnectionState.Connected;
	}

	public int getCurrentChannel() {
		assertConnected();

		return mClient.currentChannel;
	}

	public void joinChannel(int channelId) {
		assertConnected();

		mClient.joinChannel(channelId);
	}

	public boolean canSpeak() {
		return mClient.canSpeak;
	}

	public Collection<User> getUsers() {
		assertConnected();

		return Collections.unmodifiableCollection(mClient.userArray);
	}

	public List<Channel> getChannelList() {
		assertConnected();

		return Collections.unmodifiableList( mClient.channelArray );
	}

	public void sendUdpTunnelMessage(byte[] buffer) throws IOException {
		assertConnected();

		mClient.sendUdpTunnelMessage(buffer);
	}

	public void sendChannelTextMessage(String message) {
		assertConnected();

		mClient.sendChannelTextMessage(message);
	}

	public boolean isRecording() {
		return (mRecordThread != null);
	}

	public void setRecording(boolean state) {
		assertConnected();

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

	public void channelsUpdated() {
		sendBroadcast(INTENT_CHANNEL_LIST_UPDATE);
	}

	public void currentChannelChanged() {
		sendBroadcast(INTENT_CURRENT_CHANNEL_CHANGED);
	}

	public void messageReceived(Message msg) {
		messages.add(msg);
		Bundle b = new Bundle();
		b.putSerializable(EXTRA_MESSAGE, msg);
		sendBroadcast(INTENT_CHAT_TEXT_UPDATE, b);
	}

	public void messageSent(Message msg) {
		messages.add(msg);
		Bundle b = new Bundle();
		b.putSerializable(EXTRA_MESSAGE, msg);
		sendBroadcast(INTENT_CHAT_TEXT_UPDATE, b);
	}

	public void setConnectionState(ConnectionState state) {
		this.state = state;
		Bundle b = new Bundle();
		b.putSerializable(EXTRA_CONNECTION_STATE, state);
		sendBroadcast(INTENT_CONNECTION_STATE_CHANGED);
	}

	public void userListUpdated() {
		sendBroadcast(INTENT_USER_LIST_UPDATE);
	}

	private void assertConnected() {
		if (!isConnected())
			throw new IllegalStateException("Service is not connected");
	}

	private void sendBroadcast(final String action) {
		sendBroadcast(action, null);
	}

	private void sendBroadcast(final String action, Bundle extras) {
		final Intent i = new Intent(action);

		if (extras != null)
			i.putExtras(extras);

		sendBroadcast(i);
	}
}
