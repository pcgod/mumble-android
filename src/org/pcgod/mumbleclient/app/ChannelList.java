package org.pcgod.mumbleclient.app;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.pcgod.mumbleclient.R;
import org.pcgod.mumbleclient.Settings;
import org.pcgod.mumbleclient.service.BaseServiceObserver;
import org.pcgod.mumbleclient.service.IServiceObserver;
import org.pcgod.mumbleclient.service.model.Channel;
import org.pcgod.mumbleclient.service.model.User;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
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
 * NOTE: Service enters 'Connected' state when it has received and processed
 * server sync message. This means that at this point the service should be
 * fully initialized.
 *
 * And just so the state wouldn't be too easy the connection can be cancelled.
 * Disconnecting the service is practically synchronous operation. Intents
 * broadcast by the Service aren't though. This means that after the ChannelList
 * disconnects the service it might still have some unprocessed intents queued
 * in a queue. For this reason all intents that require active connection must
 * take care to check that the connection is still alive.
 *
 * @author pcgod, Rantanen
 *
 */
public class ChannelList extends ConnectedActivity {
	/**
	 * Handles broadcasts from MumbleService
	 */
	class ChannelServiceObserver extends BaseServiceObserver {
		@Override
		public void onCurrentChannelChanged() throws RemoteException {
			setChannel(mService.getCurrentChannel());
		}

		@Override
		public void onCurrentUserUpdated() throws RemoteException {
			synchronizeControls();
		}

		@Override
		public void onUserAdded(final User user) throws RemoteException {
			refreshUser(user);
		}

		@Override
		public void onUserRemoved(final User user) throws RemoteException {
			usersAdapter.removeUser(user.session);
		}

		@Override
		public void onUserUpdated(final User user) throws RemoteException {
			refreshUser(user);
		}

		private void refreshUser(final User user) {
			usersAdapter.refreshUser(user);
		}
	}

	public static final String JOIN_CHANNEL = "join_channel";
	public static final String SAVED_STATE_VISIBLE_CHANNEL = "visible_channel";

	private static final int MENU_CHAT = Menu.FIRST;

	Channel visibleChannel;

	private TextView channelNameText;
	private Button browseButton;
	private ListView channelUsersList;
	private UserListAdapter usersAdapter;
	private TextView noUsersText;
	private ToggleButton speakButton;
	private Button joinButton;
	private CheckBox speakerCheckBox;

	private AlertDialog mChannelSelectDialog;
	List<Channel> selectableChannels;
	private ProgressDialog mProgressDialog;
	private AlertDialog mDisconnectDialog;

	private Settings settings;

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
					channelNames[step] = String.format(
						"%s (C, %d)",
						c.name,
						c.userCount);
				} else {
					channelNames[step] = String.format(
						"%s (%d)",
						c.name,
						c.userCount);
				}
				step++;
			}

			new AlertDialog.Builder(ChannelList.this).setCancelable(true).setItems(
				channelNames,
				channelListClickEvent).show();
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
	public final boolean onMenuItemSelected(
		final int featureId,
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

	/**
	 * Handles activity initialization when the Service has connected.
	 *
	 * Should be called when there is a reason to believe that the connection
	 * might have became valid. The connection MUST be established but other
	 * validity criteria may still be unfilled such as server synchronization
	 * being complete.
	 *
	 * The method implements the logic required for making sure that the
	 * Connected service is in such a state that it fills all the connection
	 * criteria for ChannelList.
	 *
	 * The method also takes care of making sure that its initialization code
	 * is executed only once so calling it several times doesn't cause problems.
	 */
	@Override
	protected void onConnected() {
		// We are now connected! \o/
		if (mProgressDialog != null) {
			mProgressDialog.dismiss();
			mProgressDialog = null;
		}

		// If we don't have visible channel selected, default to the current channel.
		// Setting channel also synchronizes the UI so we don't need to do it manually.
		//
		// TODO: Resync channel if current channel was visible on pause.
		// Currently if the user is moved to another channel while this activity
		// is paused the channel isn't updated when the activity resumes.
		if (visibleChannel == null) {
			setChannel(mService.getCurrentChannel());
		} else {
			synchronizeControls();
			usersAdapter.notifyDataSetChanged();
		}

		usersAdapter.setUsers(mService.getUserList());
	}

	/**
	 * Handles activity initialization when the Service is connecting.
	 */
	@Override
	protected void onConnecting() {
		showProgressDialog(R.string.connectionProgressConnectingMessage);
		synchronizeControls();
	}

	@Override
	protected void onSynchronizing() {
		showProgressDialog(R.string.connectionProgressSynchronizingMessage);
		synchronizeControls();
	}

	private void setChannel(final Channel channel) {
		visibleChannel = channel;

		final int channelId = channel.id;
		usersAdapter.setVisibleChannel(channelId);
		synchronizeControls();
	}

	private void showProgressDialog(final int message) {
		if (mProgressDialog == null) {
			mProgressDialog = ProgressDialog.show(
				ChannelList.this,
				getString(R.string.connectionProgressTitle),
				getString(message),
				true,
				true,
				new OnCancelListener() {
					@Override
					public void onCancel(final DialogInterface dialog) {
						mService.disconnect();
						mProgressDialog.setMessage(getString(R.string.connectionProgressDisconnectingMessage));
					}
				});
		} else {
			mProgressDialog.setMessage(getString(message));
		}
	}

	private void synchronizeControls() {
		// Use 'visibleChannel' to mark whether we should show stuff or not.
		// We used mService.isConnected at some point but this has an issue if
		// onConnected has not been called yet (and thus visibleChannel has not
		// been set).
		if (mService == null || mService.getCurrentChannel() == null ||
			visibleChannel == null) {
			findViewById(R.id.connectionViewRoot).setVisibility(View.GONE);
			speakButton.setEnabled(false);
			joinButton.setEnabled(false);
		} else {
			findViewById(R.id.connectionViewRoot).setVisibility(View.VISIBLE);
			if (mService.getCurrentChannel().id == visibleChannel.id) {
				speakButton.setVisibility(View.VISIBLE);
				speakButton.setEnabled(mService.canSpeak());
				speakButton.setChecked(mService.isRecording());
				joinButton.setVisibility(View.GONE);
			} else {
				speakButton.setVisibility(View.GONE);
				joinButton.setVisibility(View.VISIBLE);
				joinButton.setEnabled(true);
			}
			channelNameText.setText(visibleChannel.name);
		}
	}

	@Override
	protected IServiceObserver createServiceObserver() {
		return new ChannelServiceObserver();
	}

	@Override
	protected final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.channel_list);

		settings = new Settings(this);
		setVolumeControlStream(settings.getAudioStream());

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

		usersAdapter = new UserListAdapter(this, channelUsersList, null);
		channelUsersList.setAdapter(usersAdapter);
		channelUsersList.setEmptyView(noUsersText);

		// Disable speaker check box for now since switching between audio
		// inputs isn't supported.
		speakerCheckBox.setEnabled(false);
		speakerCheckBox.setVisibility(View.GONE);

		if (savedInstanceState != null) {
			final Channel channel = (Channel) savedInstanceState.getParcelable(SAVED_STATE_VISIBLE_CHANNEL);

			// Channel might be null if we for example caused screen rotation
			// while still connecting.
			if (channel != null) {
				setChannel(channel);
			}
		}
	}

	@Override
	protected final void onPause() {
		super.onPause();
		cleanDialogs();
	}

	@Override
	protected void onSaveInstanceState(final Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putParcelable(SAVED_STATE_VISIBLE_CHANNEL, visibleChannel);
	}

	/**
	 * Signals that the service has been bound and is available for use.
	 */
	@Override
	protected final void onServiceBound() {
	}
}
