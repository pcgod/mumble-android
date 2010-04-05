package org.pcgod.mumbleclient.app;

import java.util.ArrayList;

import org.pcgod.mumbleclient.MumbleClient;
import org.pcgod.mumbleclient.R;
import org.pcgod.mumbleclient.User;

import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;

public class UserList extends ListActivity {
	public class UserAdapter extends BaseAdapter {
		private ArrayList<User> al;
		private Context ctx;

		public UserAdapter(final Context context,
				final ArrayList<User> userArray) {
			ctx = context;
			al = userArray;
		}

		@Override
		public final int getCount() {
			return al.size();
		}

		@Override
		public final Object getItem(final int position) {
			return getItemId(position);
		}

		@Override
		public final long getItemId(final int position) {
			return al.get(position).session;
		}

		@Override
		public final View getView(final int position, final View v,
				final ViewGroup parent) {
			final User u = al.get(position);
			final TextView tv = new TextView(ctx);
			tv.setText(u.name);
			if (u.session == ServerList.client.session) {
				tv.setTypeface(Typeface.DEFAULT_BOLD);
			}
			return tv;
		}
	}

	public class UserBroadcastReceiver extends BroadcastReceiver {
		@Override
		public final void onReceive(final Context ctx, final Intent i) {
			if (MumbleClient.INTENT_USER_LIST_UPDATE.equals(i.getAction())) {
				updateList();
			} else if (MumbleClient.INTENT_CURRENT_CHANNEL_CHANGED.equals(i
					.getAction())) {
				channelId = ServerList.client.currentChannel;
				updateButtonVisibility();
			}
		}
	}

	private UserBroadcastReceiver bcReceiver;
	private int channelId;
	private Button joinButton;
	private OnClickListener joinButtonClickEvent = new OnClickListener() {
		@Override
		public void onClick(final View v) {
			ServerList.client.joinChannel(channelId);
		}
	};
	private Thread rt;
	private ToggleButton speakButton;

	private OnClickListener speakButtonClickEvent = new OnClickListener() {
		@Override
		public void onClick(final View v) {
			if (speakButton.isChecked()) {
				// start record
				rt = new Thread(new RecordThread(), "record");
				rt.start();
			} else {
				// stop record
				rt.interrupt();
			}
		}
	};

	private ArrayList<User> userList = new ArrayList<User>();

	public final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.user_list);

		if (ServerList.client == null) {
			finish();
			return;
		}

		if (!ServerList.client.isConnected()) {
			finish();
			return;
		}

		joinButton = (Button) findViewById(R.id.joinButton);
		speakButton = (ToggleButton) findViewById(R.id.speakButton);

		joinButton.setOnClickListener(joinButtonClickEvent);
		speakButton.setOnClickListener(speakButtonClickEvent);

		final Intent i = getIntent();
		channelId = (int) i.getLongExtra("channelId", -1);
		updateButtonVisibility();

		setListAdapter(new UserAdapter(this, userList));
		updateList();
	}

	private void updateButtonVisibility() {
		if (channelId == ServerList.client.currentChannel) {
			joinButton.setVisibility(View.GONE);
			speakButton.setVisibility(View.VISIBLE);
		} else {
			joinButton.setVisibility(View.VISIBLE);
			speakButton.setVisibility(View.GONE);
		}
	}

	private void updateList() {
		userList.clear();
		for (final User u : ServerList.client.userArray) {
			if (u.channel == channelId) {
				userList.add(u);
			}
		}
		((BaseAdapter) getListAdapter()).notifyDataSetChanged();
	}

	@Override
	protected final void onPause() {
		super.onPause();

		unregisterReceiver(bcReceiver);
	}

	@Override
	protected final void onResume() {
		super.onResume();

		final IntentFilter ifilter = new IntentFilter(
				MumbleClient.INTENT_USER_LIST_UPDATE);
		ifilter.addAction(MumbleClient.INTENT_CURRENT_CHANNEL_CHANGED);
		bcReceiver = new UserBroadcastReceiver();
		registerReceiver(bcReceiver, ifilter);
	}
}
