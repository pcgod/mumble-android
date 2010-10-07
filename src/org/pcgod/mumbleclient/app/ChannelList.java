package org.pcgod.mumbleclient.app;

import java.util.List;

import junit.framework.Assert;

import org.pcgod.mumbleclient.Globals;
import org.pcgod.mumbleclient.R;
import org.pcgod.mumbleclient.service.MumbleService;
import org.pcgod.mumbleclient.service.model.Channel;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface.OnClickListener;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Shows channels for the connected server
 *
 * @author pcgod
 *
 */
public class ChannelList extends ConnectedListActivity {

	public static final String JOIN_CHANNEL = "join_channel";

	private class ChannelAdapter extends ArrayAdapter<Channel> {
		public ChannelAdapter(final Context context,
				final List<Channel> channels) {
			super(context, android.R.layout.simple_list_item_1, channels);
		}

		@Override
		public final View getView(final int position, View v,
				final ViewGroup parent) {
			if (v == null) {
				final LayoutInflater inflater = (LayoutInflater) ChannelList.this
						.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = inflater.inflate(android.R.layout.simple_list_item_1, null);
			}
			final Channel c = getItem(position);
			final TextView tv = (TextView) v.findViewById(android.R.id.text1);
			tv.setText(c.name + " (" + c.userCount + ")");
			return tv;
		}
	}

	private class ChannelBroadcastReceiver extends BroadcastReceiver {
		@Override
		public final void onReceive(final Context ctx, final Intent i) {
			if (i.getAction().equals(MumbleService.INTENT_CONNECTION_STATE_CHANGED)) {
				switch (mService.getConnectionState()) {
				case Connecting:
					Log.i(Globals.LOG_TAG, "ChannelList: Connecting");
					mProgressDialog = ProgressDialog.show(ChannelList.this, "Connecting", "Connecting to Mumble server", true);
					break;
				case Connected:
					Log.i(Globals.LOG_TAG, "ChannelList: Connected");
					if (mProgressDialog != null) {
						mProgressDialog.dismiss();
						mProgressDialog = null;
					}
					setListAdapter(new ChannelAdapter(ChannelList.this, mService.getChannelList()));
					updateList();
					break;
				case Disconnected:
				case Disconnecting:
					if (mProgressDialog != null) {
						mProgressDialog.dismiss();
						mProgressDialog = null;
					}
					
					if (mDisconnectDialog != null) {
						mDisconnectDialog.dismiss();
						mDisconnectDialog = null;
					}

					finish();
					break;
				default:
					Assert.fail("Unknown connection state");
				}

			} else {
				updateList();
			}
		}
	}

	private static final int ACTIVITY_USER_LIST = 0;
	private static final int MENU_CHAT = Menu.FIRST;

	private ChannelBroadcastReceiver bcReceiver;
	private ProgressDialog mProgressDialog;
	private AlertDialog mDisconnectDialog;

	@Override
	public final boolean onCreateOptionsMenu(final Menu menu) {
		menu.add(0, MENU_CHAT, 0, "Chat").setIcon(
				android.R.drawable.ic_btn_speak_now);
		return true;
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

	@Override
	protected final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.channel_list);
		setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
	}

	/**
	 * Signals that the service has been bound and is available for use.
	 */
	@Override
	protected final void onServiceBound() {
		switch (mService.getConnectionState()) {
		case Connecting:
			mProgressDialog = ProgressDialog.show(this, "Connecting", "Connecting to Mumble server", true);
			break;
		case Connected:
			setListAdapter(new ChannelAdapter(this, mService.getChannelList()));
			updateList();
			break;
		case Disconnected:
		case Disconnecting:
			finish();
			break;
		default:
			Assert.fail("Unknown connection state");
		}
	}

	@Override
	protected final void onListItemClick(final ListView l, final View v,
			final int position, final long id) {
		super.onListItemClick(l, v, position, id);

		final Channel c = (Channel) getListView().getItemAtPosition(position);
		final Intent i = new Intent(this, UserList.class);
		i.putExtra("channelId", (long) c.id);
		startActivityForResult(i, ACTIVITY_USER_LIST);
	}

	@Override
	protected final void onPause() {
		super.onPause();

		if (bcReceiver != null) {
			unregisterReceiver(bcReceiver);
			bcReceiver = null;
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

	@Override
	protected final void onResume() {
		super.onResume();

		final IntentFilter ifilter = new IntentFilter();
		ifilter.addAction(MumbleService.INTENT_CHANNEL_LIST_UPDATE);
		ifilter.addAction(MumbleService.INTENT_USER_LIST_UPDATE);
		ifilter.addAction(MumbleService.INTENT_CONNECTION_STATE_CHANGED);
		bcReceiver = new ChannelBroadcastReceiver();
		registerReceiver(bcReceiver, ifilter);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
			AlertDialog.Builder b = new AlertDialog.Builder(this);
			b.setIcon(android.R.drawable.ic_dialog_alert);
			b.setTitle("Disconnect");
			b.setMessage("Are you sure you want to disconnect from Mumble?");
			b.setPositiveButton(android.R.string.yes, new OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog, int which) {
										mService.disconnect();
									}
								});
			b.setNegativeButton(android.R.string.no, null);
			mDisconnectDialog = b.show();

			return true;
		}

		return super.onKeyDown(keyCode, event);
	}

	void updateList() {
		ListAdapter adapter = getListAdapter();
		if (adapter == null) return;
		((BaseAdapter)adapter).notifyDataSetChanged();
	}
}
