package org.pcgod.mumbleclient.app;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import junit.framework.Assert;

import org.pcgod.mumbleclient.Globals;
import org.pcgod.mumbleclient.R;
import org.pcgod.mumbleclient.service.MumbleService;
import org.pcgod.mumbleclient.service.MumbleConnectionHost.ConnectionState;
import org.pcgod.mumbleclient.service.model.Channel;
import org.pcgod.mumbleclient.service.model.User;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ToggleButton;

/**
 * The main connection view.
 *
 * The state of this activity depends closely on the state of the underlying
 * MumbleService. When the activity is started it can't really do anything else
 * than initialize its member variables until it has acquired a reference to the
 * MumbleService.
 *
 * Once the MumbleService reference has been acquired the activity is in one of
 * the three states:
 * <dl>
 * <dt>Connecting to server
 * <dd>MumbleService has just been started and ChannelList should wait until the
 * connection has been established. In this case the ChannelList should be very
 * careful as it doesn't have a visible channel and the Service doesn't have a
 * current channel.
 *
 * <dt>Connected to server
 * <dd>When the Activity is resumed during an established Mumble connection it
 * has connection immediately available and is free to act freely.
 *
 * <dt>Disconnecting or Disconnected
 * <dd>If the ChannelList is resumed after the Service has been disconnected the
 * List should exit immediately.
 * </dl>
 *
 * Once the MumbleService has established connection it will signal Connection
 * state change. However this doesn't mean that the connection has been properly
 * established from the view's perspective. After the connection has been
 * established the server has to synchronize the channels and users still.
 *
 * The service state is valid for this view only after the channels and the
 * current user has been synchronized by the server. After these tasks are done
 * the view can resolve the joined channel that it can use as the default
 * channel.
 *
 * In practice this means that the onConnected method must be attempted in the
 * following cases:
 * <dl>
 * <dt>onServiceBound
 * <dd>If the service is already in connected state AND the current channel is
 * set, it is safe to call onConnected. In this case the currently visible
 * channel should have been restored from previously saved state. If such
 * doesn't exist, use currently connected channel as default value.
 *
 * <dt>CurrentChannelUpdated
 * <dd>Once INTENT_CONNECTION_STATE_CHANGED broadcast is received, the
 * connection state is still incomplete. CurrentChannelUpdated means that the
 * channels have been updated and the channel of the current user is known. This
 * can be used as the default value for currently visible channel.
 * </dl>
 *
 * @author pcgod, Rantanen
 *
 */
public class ChannelList extends ConnectedActivity {
	/**
	 * Handles broadcasts from MumbleService
	 */
	private class ChannelBroadcastReceiver extends BroadcastReceiver {
		@Override
		public final void onReceive(final Context ctx, final Intent i) {
			final String action = i.getAction();

			if (action.equals(MumbleService.INTENT_CONNECTION_STATE_CHANGED)) {
				onConnectionStateChanged();
				return;
			}

			if (action.equals(MumbleService.INTENT_CURRENT_CHANNEL_CHANGED)) {
				// Current channel being set is one of the requirements this
				// view has for considering the connection complete. For this
				// reason call onConnected.
				//
				// The method will make sure that the connection is signaled
				// only once so calling is safe even if it has already been
				// called successfully.
				onConnected();

				synchronizeControls();
				return;
			}

			if (action.equals(MumbleService.INTENT_CHANNEL_LIST_UPDATE)) {
				// Channel list update doesn't matter if the visible channel
				// isn't valid from the beginning.
				if (visibleChannel == null) {
					return;
				}

				boolean visibleChannelValid = false;
				for (final Channel c : mService.getChannelList()) {
					if (visibleChannel.id == c.id) {
						visibleChannelValid = true;
						break;
					}
				}

				if (!visibleChannelValid) {
					setChannel(mService.getCurrentChannel());
				}
				return;
			}

			if (action.equals(MumbleService.INTENT_USER_LIST_UPDATE)) {
				updateUserList();
				return;
			}

			Assert.fail("Unknown intent broadcast");
		}

		private final void onConnectionStateChanged() {
			switch (mService.getConnectionState()) {
			case Connecting:
				Log.i(Globals.LOG_TAG, "ChannelList: Connecting");
				mProgressDialog = ProgressDialog.show(ChannelList.this,
						"Connecting", "Connecting to Mumble server", true);
				break;
			case Connected:
				Log.i(Globals.LOG_TAG, "ChannelList: Connected");

				// The service might have been fast enough to properly connect
				// before the broadcast was resolved. Try calling onConnected
				// just in case.
				onConnected();
				break;
			case Disconnected:
			case Disconnecting:
				Log.i(Globals.LOG_TAG, "ChannelList: Disconnected");
				onDisconnected();
				break;
			default:
				Assert.fail("Unknown connection state");
			}
		}
	}

	private class UserAdapter extends ArrayAdapter<User> {
		public UserAdapter(final Context context, final List<User> users) {
			super(context, android.R.layout.simple_list_item_1, users);
		}

		@Override
		public final View getView(final int position, View v,
				final ViewGroup parent) {
			if (v == null) {
				final LayoutInflater inflater = (LayoutInflater) ChannelList.this
						.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = inflater.inflate(android.R.layout.simple_list_item_1, null);
			}
			final User u = getItem(position);
			final TextView tv = (TextView) v.findViewById(android.R.id.text1);
			tv.setText(u.name);
			return tv;
		}
	}

	public static final String JOIN_CHANNEL = "join_channel";

	public static final String SAVED_STATE_VISIBLE_CHANNEL = "visible_channel";

	private static final int MENU_CHAT = Menu.FIRST;

	private boolean isConnected = false;
	private Channel visibleChannel;
	private final List<User> channelUsers = new ArrayList<User>();

	private TextView channelNameText;
	private Button browseButton;
	private ListView channelUsersList;
	private TextView noUsersText;
	private ToggleButton speakButton;
	private Button joinButton;
	private CheckBox speakerCheckBox;

	private ChannelBroadcastReceiver bcReceiver;
	private AlertDialog mChannelSelectDialog;
	private List<Channel> selectableChannels;
	private ProgressDialog mProgressDialog;
	private AlertDialog mDisconnectDialog;

	public final OnClickListener browseButtonClickEvent = new OnClickListener() {
		@Override
		public void onClick(final View v) {
			// Save the current channels so we can match them by index even if
			// the real
			// channels change.
			final List<Channel> currentChannels = mService.getChannelList();
			selectableChannels = new ArrayList<Channel>(currentChannels);

			final Channel currentChannel = mService.getCurrentChannel();
			int currentChannelId = -1;
			if (currentChannel != null) {
				currentChannelId = currentChannel.id;
			}

			final Iterator<Channel> i = selectableChannels.iterator();
			int step = 0;
			final String[] channelNames = new String[selectableChannels.size()];
			while (i.hasNext()) {
				final Channel c = i.next();
				if (c.id == currentChannelId) {
					channelNames[step] = String.format("%s (C, %d)", c.name,
							c.userCount);
				} else {
					channelNames[step] = String.format("%s (%d)", c.name,
							c.userCount);
				}
				step++;
			}

			new AlertDialog.Builder(ChannelList.this).setCancelable(true)
					.setItems(channelNames, channelListClickEvent).show();
		}
	};

	public final DialogInterface.OnClickListener channelListClickEvent = new DialogInterface.OnClickListener() {
		@Override
		public void onClick(final DialogInterface dialog, final int which) {
			setChannel(selectableChannels.get(which));
		}
	};

	private final OnClickListener joinButtonClickEvent = new OnClickListener() {
		public void onClick(final View v) {
			mService.joinChannel(visibleChannel.id);
		}
	};

	private final OnClickListener speakButtonClickEvent = new OnClickListener() {
		public void onClick(final View v) {
			mService.setRecording(!mService.isRecording());
		}
	};

	public final DialogInterface.OnClickListener onDisconnectConfirm = new DialogInterface.OnClickListener() {
		@Override
		public void onClick(final DialogInterface dialog, final int which) {
			mService.disconnect();
		}
	};

	@Override
	public final boolean onCreateOptionsMenu(final Menu menu) {
		menu.add(0, MENU_CHAT, 0, "Chat").setIcon(
				android.R.drawable.ic_btn_speak_now);
		return true;
	}

	@Override
	public boolean onKeyDown(final int keyCode, final KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
			final AlertDialog.Builder b = new AlertDialog.Builder(this);
			b.setIcon(android.R.drawable.ic_dialog_alert);
			b.setTitle("Disconnect");
			b.setMessage("Are you sure you want to disconnect from Mumble?");
			b.setPositiveButton(android.R.string.yes, onDisconnectConfirm);
			b.setNegativeButton(android.R.string.no, null);
			mDisconnectDialog = b.show();

			return true;
		}

		return super.onKeyDown(keyCode, event);
	}

	@Override
	public final boolean onMenuItemSelected(final int featureId,
			final MenuItem item) {
		switch (item.getItemId()) {
		case MENU_CHAT:
			final Intent i = new Intent(this, ChatActivity.class);
			startActivity(i);
			return true;
		default:
			return super.onMenuItemSelected(featureId, item);
		}
	}

	private void cleanDialogs() {
		if (mChannelSelectDialog != null) {
			mChannelSelectDialog.dismiss();
			mChannelSelectDialog = null;
		}

		if (mProgressDialog != null) {
			mProgressDialog.dismiss();
			mProgressDialog = null;
		}

		if (mDisconnectDialog != null) {
			mDisconnectDialog.dismiss();
			mDisconnectDialog = null;
		}
	}

	private void onConnected() {
		if (isConnected || mService.getCurrentChannel() == null) {
			if (mProgressDialog != null &&
					mService.getConnectionState() == ConnectionState.Connected) {
				mProgressDialog
						.setMessage(getString(R.string.connectionProgressSynchronizingMessage));
			}
			return;
		}

		isConnected = true;

		// If we don't have visible channel selected, default to the
		// current channel.
		if (visibleChannel == null) {
			visibleChannel = mService.getCurrentChannel();
		}

		// We are now connected! \o/
		if (mProgressDialog != null) {
			mProgressDialog.dismiss();
			mProgressDialog = null;
		}

		updateUserList();

		speakButton.setEnabled(true);
		joinButton.setEnabled(true);
		final String channelName = mService.getCurrentChannel().name;
		channelNameText.setText(channelName);
	}

	private void onDisconnected() {
		cleanDialogs();
		finish();
	}

	private void setChannel(final Channel channel) {
		visibleChannel = channel;

		synchronizeControls();
		updateUserList();
	}

	private void synchronizeControls() {
		if (!isConnected) {
			findViewById(R.id.connectionViewRoot).setVisibility(View.GONE);
			speakButton.setEnabled(false);
			joinButton.setEnabled(false);
			channelNameText.setText("(Not connected)");
		} else {
			findViewById(R.id.connectionViewRoot).setVisibility(View.VISIBLE);
			if (mService.getCurrentChannel().id == visibleChannel.id) {
				speakButton.setVisibility(View.VISIBLE);
				joinButton.setVisibility(View.GONE);
				speakButton.setChecked(mService.isRecording());
			} else {
				speakButton.setVisibility(View.GONE);
				joinButton.setVisibility(View.VISIBLE);
			}
			channelNameText.setText(visibleChannel.name);
		}
	}

	private void updateUserList() {
		final List<User> allUsers = mService.getUserList();
		channelUsers.clear();

		if (isConnected) {
			for (final User u : allUsers) {
				if (u.getChannel().id == visibleChannel.id) {
					channelUsers.add(u);
				}
			}

			((UserAdapter) channelUsersList.getAdapter())
					.notifyDataSetChanged();
		}

		final boolean showList = (channelUsers.size() > 0);
		channelUsersList.setVisibility(showList ? View.VISIBLE : View.GONE);
		noUsersText.setVisibility(showList ? View.GONE : View.VISIBLE);
	}

	@Override
	protected final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.channel_list);
		setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

		if (savedInstanceState != null) {
			visibleChannel = (Channel) savedInstanceState
					.getSerializable(SAVED_STATE_VISIBLE_CHANNEL);
		}

		// Get the UI views
		channelNameText = (TextView) findViewById(R.id.channelName);
		browseButton = (Button) findViewById(R.id.browseButton);
		channelUsersList = (ListView) findViewById(R.id.channelUsers);
		noUsersText = (TextView) findViewById(R.id.noUsersText);
		speakButton = (ToggleButton) findViewById(R.id.speakButton);
		joinButton = (Button) findViewById(R.id.joinButton);
		speakerCheckBox = (CheckBox) findViewById(R.id.speakerCheckBox);

		// Set event handlers.
		browseButton.setOnClickListener(browseButtonClickEvent);
		joinButton.setOnClickListener(joinButtonClickEvent);
		speakButton.setOnClickListener(speakButtonClickEvent);

		channelUsersList.setAdapter(new UserAdapter(this, channelUsers));

		// Disable speaker check box for now since switching between audio
		// inputs isn't supported.
		speakerCheckBox.setEnabled(false);
		speakerCheckBox.setVisibility(View.GONE);
	}

	@Override
	protected final void onPause() {
		super.onPause();

		if (bcReceiver != null) {
			unregisterReceiver(bcReceiver);
			bcReceiver = null;
		}

		cleanDialogs();
	}

	@Override
	protected final void onResume() {
		super.onResume();

		final IntentFilter ifilter = new IntentFilter();
		ifilter.addAction(MumbleService.INTENT_CHANNEL_LIST_UPDATE);
		ifilter.addAction(MumbleService.INTENT_USER_LIST_UPDATE);
		ifilter.addAction(MumbleService.INTENT_CONNECTION_STATE_CHANGED);
		ifilter.addAction(MumbleService.INTENT_CURRENT_CHANNEL_CHANGED);
		bcReceiver = new ChannelBroadcastReceiver();
		registerReceiver(bcReceiver, ifilter);

		synchronizeControls();
	}

	@Override
	protected void onSaveInstanceState(final Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putSerializable(SAVED_STATE_VISIBLE_CHANNEL, visibleChannel);
	}

	/**
	 * Signals that the service has been bound and is available for use.
	 */
	@Override
	protected final void onServiceBound() {
		switch (mService.getConnectionState()) {
		case Connecting:
			mProgressDialog = ProgressDialog.show(this,
					getString(R.string.connectionProgressTitle),
					getString(R.string.connectionProgressConnectingMessage),
					true);
			break;
		case Connected:
			// We might have resumed right after the connection was established
			// in which case the connection is incomplete. Try setting up the
			// connection anyway as onConnected takes care of the proper checks.
			onConnected();
			break;
		case Disconnected:
		case Disconnecting:
			onDisconnected();
			break;
		default:
			Assert.fail("Unknown connection state");
		}
	}
}
