package org.pcgod.mumbleclient.app;

import java.util.ArrayList;

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
		private Context ctx;
		private ArrayList<User> al;

		public UserAdapter(Context context, ArrayList<User> userArray) {
			ctx = context;
			al = userArray;
		}

		@Override
		public int getCount() {
			return al.size();
		}

		@Override
		public Object getItem(int position) {
			return getItemId(position);
		}

		@Override
		public long getItemId(int position) {
			return al.get(position).session;
		}

		@Override
		public View getView(int position, View v, ViewGroup parent) {
			User u = al.get(position);
			TextView tv = new TextView(ctx);
			tv.setText(u.name);
			if (u.session == ServerList.client.session)
				tv.setTypeface(Typeface.DEFAULT_BOLD);
			return tv;
		}
	}

	public class UserBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context ctx, Intent i) {
			if (i.getAction().equals("mumbleclient.intent.USER_LIST_UPDATE")) {
				updateList();
			} else if (i.getAction().equals("mumbleclient.intent.CURRENT_CHANNEL_CHANGED")) {
				channelId = ServerList.client.currentChannel;
				updateButtonVisibility();
			}
		}
	}
	
	private Button joinButton;
	private ToggleButton speakButton;
	private UserBroadcastReceiver bcReceiver;
	private int channelId;
	private Thread rt;
	private ArrayList<User> userList = new ArrayList<User>();

	private OnClickListener joinButtonClickEvent = new OnClickListener() {
		@Override
		public void onClick(View v) {
			ServerList.client.joinChannel(channelId);
		}
	};
	
	private OnClickListener speakButtonClickEvent = new OnClickListener() {
		@Override
		public void onClick(View v) {
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
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.user_list);

		if (ServerList.client == null)
			finish();

		if (!ServerList.client.isConnected())
			finish();
		
		joinButton = (Button) findViewById(R.id.joinButton);
		speakButton = (ToggleButton) findViewById(R.id.speakButton);

		joinButton.setOnClickListener(joinButtonClickEvent);
		speakButton.setOnClickListener(speakButtonClickEvent);

		Intent i = getIntent();
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
		for (User u : ServerList.client.userArray) {
			if (u.channel == channelId)
				userList.add(u);
		}
		((BaseAdapter) getListAdapter()).notifyDataSetChanged();
	}

	@Override
	protected void onPause() {
		super.onPause();

		unregisterReceiver(bcReceiver);
	}

	@Override
	protected void onResume() {
		super.onResume();

		IntentFilter ifilter = new IntentFilter(
				"mumbleclient.intent.USER_LIST_UPDATE");
		ifilter.addAction("mumbleclient.intent.CURRENT_CHANNEL_CHANGED");
		bcReceiver = new UserBroadcastReceiver();
		registerReceiver(bcReceiver, ifilter);
	}
}
