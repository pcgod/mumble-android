package org.pcgod.mumbleclient.service;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import junit.framework.Assert;

import org.pcgod.mumbleclient.Globals;
import org.pcgod.mumbleclient.R;
import org.pcgod.mumbleclient.app.ChannelList;
import org.pcgod.mumbleclient.service.MumbleConnectionHost.ConnectionState;
import org.pcgod.mumbleclient.service.audio.AudioOutputHost;
import org.pcgod.mumbleclient.service.audio.RecordThread;
import org.pcgod.mumbleclient.service.model.Channel;
import org.pcgod.mumbleclient.service.model.Message;
import org.pcgod.mumbleclient.service.model.User;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

/**
 * Service for providing the client an access to the connection.
 *
 * MumbleService manages the MumbleClient connection and provides access to
 * it for binding activities.
 *
 * @author Rantanen
 */
public class MumbleService extends Service {
	public class LocalBinder extends Binder {
		public MumbleService getService() {
			return MumbleService.this;
		}
	}

	public static final String ACTION_CONNECT = "mumbleclient.action.CONNECT";
	public static final String INTENT_CHANNEL_LIST_UPDATE = "mumbleclient.intent.CHANNEL_LIST_UPDATE";
	public static final String INTENT_CURRENT_CHANNEL_CHANGED = "mumbleclient.intent.CURRENT_CHANNEL_CHANGED";
	public static final String INTENT_CURRENT_USER_UPDATED = "mumbleclient.intent.CURRENT_USER_UPDATED";
	public static final String INTENT_USER_ADDED = "mumbleclient.intent.USER_ADDED";
	public static final String INTENT_USER_REMOVED = "mumbleclient.intent.USER_REMOVED";
	public static final String INTENT_USER_UPDATE = "mumbleclient.intent.USER_UPDATE";
	public static final String INTENT_CHAT_TEXT_UPDATE = "mumbleclient.intent.CHAT_TEXT_UPDATE";

	public static final String INTENT_CONNECTION_STATE_CHANGED = "mumbleclient.intent.CONNECTION_STATE_CHANGED";
	public static final String EXTRA_MESSAGE = "mumbleclient.extra.MESSAGE";

	public static final String EXTRA_CONNECTION_STATE = "mumbleclient.extra.CONNECTION_STATE";
	public static final String EXTRA_HOST = "mumbleclient.extra.HOST";
	public static final String EXTRA_PORT = "mumbleclient.extra.PORT";
	public static final String EXTRA_USERNAME = "mumbleclient.extra.USERNAME";
	public static final String EXTRA_PASSWORD = "mumbleclient.extra.PASSWORD";
	public static final String EXTRA_USER = "mumbleclient.extra.USER";

	private MumbleConnection mClient;
	private Thread mClientThread;
	private Thread mRecordThread;

	Notification mNotification;
	boolean mHasConnections;

	/**
	 * Connection host for MumbleConnection.
	 *
	 * MumbleConnection uses this interface to communicate back to
	 * MumbleService. Since MumbleConnection processes the data packets in a
	 * background thread these methods will be called from that thread.
	 * MumbleService should expose itself as a single threaded Service so its
	 * consumers don't need to bother with synchronizing. For this reason these
	 * handlers should take care of the required synchronization.
	 *
	 * Also it is worth noting that in case a certain handler doesn't need
	 * synchronizing for its own purposes it might need it to maintain the order
	 * of events. Forwarding the CURRENT_USER_UPDATED event shouldn't be done
	 * before the USER_ADDED event has been processed for that user. For this
	 * reason even events like the CURRENT_USER_UPDATED are posted to the
	 * MumbleService handler.
	 */
	private final MumbleConnectionHost connectionHost = new MumbleConnectionHost() {
		@Override
		public void channelAdded(final Channel channel) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					channels.add(channel);
					sendBroadcast(INTENT_CHANNEL_LIST_UPDATE);
				}
			});
		}

		@Override
		public void channelRemoved(final int channelId) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					for (int i = 0; i < channels.size(); i++) {
						if (channels.get(i).id == channelId) {
							channels.remove(i);
							break;
						}
					}
					sendBroadcast(INTENT_CHANNEL_LIST_UPDATE);
				}
			});
		}

		@Override
		public void channelUpdated(final Channel channel) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					for (int i = 0; i < channels.size(); i++) {
						if (channels.get(i).id == channel.id) {
							channels.set(i, channel);
							break;
						}
					}
					sendBroadcast(INTENT_CHANNEL_LIST_UPDATE);
				}
			});
		}

		public void currentChannelChanged() {
			handler.post(new Runnable() {
				@Override
				public void run() {
					sendBroadcast(INTENT_CURRENT_CHANNEL_CHANGED);
				}
			});
		}

		@Override
		public void currentUserUpdated() {
			handler.post(new Runnable() {
				@Override
				public void run() {
					if (!canSpeak() && isRecording()) {
						setRecording(false);
					}

					sendBroadcast(INTENT_CURRENT_USER_UPDATED);
				}
			});
		}

		public void messageReceived(final Message msg) {
			messages.add(msg);
			final Bundle b = new Bundle();
			b.putSerializable(EXTRA_MESSAGE, msg);
			sendBroadcast(INTENT_CHAT_TEXT_UPDATE, b);
		}

		public void messageSent(final Message msg) {
			messages.add(msg);
			final Bundle b = new Bundle();
			b.putSerializable(EXTRA_MESSAGE, msg);
			sendBroadcast(INTENT_CHAT_TEXT_UPDATE, b);
		}

		public void setConnectionState(final ConnectionState state) {
			// TODO: Synchronize this with the main application thread!
			// This state is being set from the Connection thread which means
			// it might be changed in the middle of a call from the Activities.
			//
			// This might result in assertion failures or worse even if the
			// activities check that the Service is connected before requesting
			// user lists for example in case the MumbleConnection decided to
			// update the state between the isConnected check and the actual
			// call.

			if (MumbleService.this.state == state) {
				return;
			}

			MumbleService.this.state = state;
			final Bundle b = new Bundle();
			b.putSerializable(EXTRA_CONNECTION_STATE, state);
			sendBroadcast(INTENT_CONNECTION_STATE_CHANGED);

			Log.i(
				Globals.LOG_TAG,
				"MumbleService: Connection state changed to " +
					state.toString());

			// Handle foreground stuff
			if (state == ConnectionState.Connected) {
				mNotification = new Notification(
					R.drawable.icon,
					"Mumble connected",
					System.currentTimeMillis());

				final Intent channelListIntent = new Intent(
					MumbleService.this,
					ChannelList.class);
				channelListIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP).addFlags(
					Intent.FLAG_ACTIVITY_NEW_TASK);
				mNotification.setLatestEventInfo(
					MumbleService.this,
					"Mumble",
					"Mumble is connected to a server",
					PendingIntent.getActivity(
						MumbleService.this,
						0,
						channelListIntent,
						0));
				startForegroundCompat(1, mNotification);
			} else if (state == ConnectionState.Disconnected) {
				if (mNotification != null) {
					stopForegroundCompat(1);
					mNotification = null;
				}

				// Clear the user and channel collections.
				users.clear();
				channels.clear();
			}

			// If the connection was disconnected and there are no bound
			// connections to this service, finish it.
			if (state == ConnectionState.Disconnected && !mHasConnections) {
				Log.i(
					Globals.LOG_TAG,
					"MumbleService: Service disconnected while there are no connections up.");
				stopSelf();
			}
		}

		@Override
		public void userAdded(final User user) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					users.add(user);
					final Bundle b = new Bundle();
					b.putSerializable(EXTRA_USER, user);
					sendBroadcast(INTENT_USER_ADDED, b);
				}
			});
		}

		@Override
		public void userRemoved(final int userId) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					for (int i = 0; i < users.size(); i++) {
						if (users.get(i).session == userId) {
							final User user = users.remove(i);

							final Bundle b = new Bundle();
							b.putSerializable(EXTRA_USER, user);
							sendBroadcast(INTENT_USER_REMOVED, b);

							return;
						}
					}

					Assert.fail("Non-existant user was removed");
				}
			});
		}

		@Override
		public void userUpdated(final User user) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					for (int i = 0; i < users.size(); i++) {
						if (users.get(i).session == user.session) {
							users.set(i, user);

							final Bundle b = new Bundle();
							b.putSerializable(EXTRA_USER, user);
							sendBroadcast(INTENT_USER_UPDATE, b);

							return;
						}
					}
					Assert.fail("Non-existant user was updated");
				}
			});
		}
	};

	private final AudioOutputHost audioHost = new AudioOutputHost() {
		@Override
		public void setTalkState(final User user, final int talkState) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					user.talkingState = talkState;
					final Bundle b = new Bundle();
					b.putSerializable(EXTRA_USER, user);
					sendBroadcast(INTENT_USER_UPDATE, b);
				}
			});
		}
	};

	private final LocalBinder mBinder = new LocalBinder();
	final Handler handler = new Handler();

	ConnectionState state;
	final List<Message> messages = new LinkedList<Message>();
	final List<Channel> channels = new ArrayList<Channel>();
	final List<User> users = new ArrayList<User>();

	private static final Class[] mStartForegroundSignature = new Class[] {
			int.class, Notification.class };

	private static final Class[] mStopForegroundSignature = new Class[] { boolean.class };

	private Method mStartForeground;
	private Method mStopForeground;

	private final Object[] mStartForegroundArgs = new Object[2];
	private final Object[] mStopForegroundArgs = new Object[1];

	public boolean canSpeak() {
		return mClient.canSpeak;
	}

	public void disconnect() {
		mClient.disconnect();
	}

	public List<Channel> getChannelList() {
		assertConnected();

		return Collections.unmodifiableList(channels);
	}

	public int getCodec() {
		if (mClient.codec == MumbleConnection.CODEC_NOCODEC) {
			throw new IllegalStateException(
				"Called getCodec on a connection with unsupported codec");
		}

		return mClient.codec;
	}

	public ConnectionState getConnectionState() {
		return state;
	}

	public Channel getCurrentChannel() {
		assertConnected();

		return mClient.currentChannel;
	}

	public User getCurrentUser() {
		assertConnected();

		return mClient.currentUser;
	}

	public String getError() {
		if (mClient == null) {
			return null;
		}
		return mClient.getError();
	}

	public List<Message> getMessageList() {
		return Collections.unmodifiableList(messages);
	}

	public List<User> getUserList() {
		assertConnected();

		return Collections.unmodifiableList(users);
	}

	public int handleCommand(final Intent intent) {
		// When using START_STICKY the onStartCommand can be called with
		// null intent after the whole service process has been killed.
		// Such scenario doesn't make sense for the service process so
		// returning START_NOT_STICKY for now.
		//
		// Leaving the null check in though just in case.
		//
		// TODO: Figure out the correct start type.
		if (intent == null) {
			return START_NOT_STICKY;
		}

		Log.i(Globals.LOG_TAG, "MumbleService: Starting service");

		final String host = intent.getStringExtra(EXTRA_HOST);
		final int port = intent.getIntExtra(EXTRA_PORT, -1);
		final String username = intent.getStringExtra(EXTRA_USERNAME);
		final String password = intent.getStringExtra(EXTRA_PASSWORD);

		if (mClient != null &&
			mClient.isSameServer(host, port, username, password) &&
			isConnected()) {
			return START_NOT_STICKY;
		}

		if (mClientThread != null) {
			mClientThread.interrupt();
		}

		mClient = new MumbleConnection(
			connectionHost,
			audioHost,
			host,
			port,
			username,
			password);
		mClientThread = new Thread(mClient, "net");
		mClientThread.start();
		return START_NOT_STICKY;
	}

	public boolean isConnected() {
		return state == ConnectionState.Connected;
	}

	public boolean isRecording() {
		return (mRecordThread != null);
	}

	public void joinChannel(final int channelId) {
		assertConnected();

		mClient.joinChannel(channelId);
	}

	@Override
	public IBinder onBind(final Intent intent) {
		mHasConnections = true;

		Log.i(Globals.LOG_TAG, "MumbleService: Bound");
		return mBinder;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		try {
			mStartForeground = getClass().getMethod(
				"startForeground",
				mStartForegroundSignature);
			mStopForeground = getClass().getMethod(
				"stopForeground",
				mStopForegroundSignature);
		} catch (final NoSuchMethodException e) {
			// Running on an older platform.
			mStartForeground = mStopForeground = null;
		}

		Log.i(Globals.LOG_TAG, "MumbleService: Created");
		state = ConnectionState.Disconnected;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		// Make sure our notification is gone.
		stopForegroundCompat(1);

		Log.i(Globals.LOG_TAG, "MumbleService: Destroyed");
	}

	@Override
	public void onStart(final Intent intent, final int startId) {
		handleCommand(intent);
	}

	@Override
	public int onStartCommand(
		final Intent intent,
		final int flags,
		final int startId) {
		return handleCommand(intent);
	}

	@Override
	public boolean onUnbind(final Intent intent) {
		mHasConnections = false;

		Log.i(Globals.LOG_TAG, "MumbleService: Unbound");

		if (state == ConnectionState.Disconnected) {
			stopSelf();
			Log.i(
				Globals.LOG_TAG,
				"MumbleService: No clients bound and connection is not alive -> Stopping");
		}

		return false;
	}

	public void sendChannelTextMessage(final String message) {
		assertConnected();

		mClient.sendChannelTextMessage(message);
	}

	public void sendUdpTunnelMessage(final byte[] buffer, final int length)
		throws IOException {
		assertConnected();

		mClient.sendUdpTunnelMessage(buffer, length);
	}

	public void setRecording(final boolean state) {
		assertConnected();

		if (mRecordThread == null && state) {
			Assert.assertTrue(canSpeak());

			// start record
			// TODO check initialized
			mRecordThread = new Thread(new RecordThread(this), "record");
			mRecordThread.start();
			audioHost.setTalkState(
				mClient.currentUser,
				AudioOutputHost.STATE_TALKING);
		} else if (mRecordThread != null && !state) {
			// stop record
			mRecordThread.interrupt();
			mRecordThread = null;
			audioHost.setTalkState(
				mClient.currentUser,
				AudioOutputHost.STATE_PASSIVE);
		}
	}

	private void assertConnected() {
		if (!isConnected()) {
			throw new IllegalStateException("Service is not connected");
		}
	}

	void sendBroadcast(final String action) {
		sendBroadcast(action, null);
	}

	void sendBroadcast(final String action, final Bundle extras) {
		final Intent i = new Intent(action);

		if (extras != null) {
			i.putExtras(extras);
		}

		sendBroadcast(i);
	}

	/**
	 * This is a wrapper around the new startForeground method, using the older
	 * APIs if it is not available.
	 */
	void startForegroundCompat(final int id, final Notification notification) {
		// If we have the new startForeground API, then use it.
		if (mStartForeground != null) {
			mStartForegroundArgs[0] = Integer.valueOf(id);
			mStartForegroundArgs[1] = notification;
			try {
				mStartForeground.invoke(this, mStartForegroundArgs);
			} catch (final InvocationTargetException e) {
				// Should not happen.
				Log.w(Globals.LOG_TAG, "Unable to invoke startForeground", e);
			} catch (final IllegalAccessException e) {
				// Should not happen.
				Log.w(Globals.LOG_TAG, "Unable to invoke startForeground", e);
			}
			return;
		}

		// Fall back on the old API.
		setForeground(true);
		((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(
			id,
			notification);
	}

	/**
	 * This is a wrapper around the new stopForeground method, using the older
	 * APIs if it is not available.
	 */
	void stopForegroundCompat(final int id) {
		// If we have the new stopForeground API, then use it.
		if (mStopForeground != null) {
			mStopForegroundArgs[0] = Boolean.TRUE;
			try {
				mStopForeground.invoke(this, mStopForegroundArgs);
			} catch (final InvocationTargetException e) {
				// Should not happen.
				Log.w(Globals.LOG_TAG, "Unable to invoke stopForeground", e);
			} catch (final IllegalAccessException e) {
				// Should not happen.
				Log.w(Globals.LOG_TAG, "Unable to invoke stopForeground", e);
			}
			return;
		}

		// Fall back on the old API.  Note to cancel BEFORE changing the
		// foreground state, since we could be killed at that point.
		((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(id);
		setForeground(false);
	}
}
