package org.pcgod.mumbleclient.app;

import java.util.List;

import org.pcgod.mumbleclient.R;
import org.pcgod.mumbleclient.service.BaseServiceObserver;
import org.pcgod.mumbleclient.service.IServiceObserver;
import org.pcgod.mumbleclient.service.model.Channel;
import org.pcgod.mumbleclient.service.model.Message;

import android.os.Bundle;
import android.os.RemoteException;
import android.text.format.DateUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.TextView.OnEditorActionListener;

public class ChatActivity extends ConnectedActivity {
	private class ChannelItem {
		private final Channel channel;

		public ChannelItem(final Channel channel) {
			this.channel = channel;
		}

		public Channel getChannel() {
			return this.channel;
		}

		@Override
		public String toString() {
			return this.channel.name;
		}
	}

	private class ChatServiceObserver extends BaseServiceObserver {
		@Override
		public void onMessageReceived(final Message msg) throws RemoteException {
			addMessage(msg);
		}

		@Override
		public void onMessageSent(final Message msg) throws RemoteException {
			addMessage(msg);
		}
	}

	private TextView chatText;
	private EditText chatTextEdit;
	private Spinner receiver;
	private ArrayAdapter<ChannelItem> receiverAdapter;
	private Channel receiverChannel;

	private static final int MENU_CLEAR = Menu.FIRST;

	private final OnEditorActionListener chatTextEditActionEvent = new OnEditorActionListener() {
		@Override
		public boolean onEditorAction(
			final TextView v,
			final int actionId,
			final KeyEvent event) {
			if (event != null && !event.isShiftPressed() && v != null) {
				final View focus = v.focusSearch(View.FOCUS_RIGHT);
				if (focus != null) {
					focus.requestFocus();
					return true;
				}
				return false;
			}

			if (actionId == EditorInfo.IME_ACTION_SEND) {
				if (v != null) {
					sendMessage(v);
				}
				return true;
			}
			return true;
		}
	};

	private final OnClickListener sendOnClickEvent = new OnClickListener() {
		@Override
		public void onClick(final View v) {
			sendMessage(chatTextEdit);
		}
	};

	@Override
	public final boolean onCreateOptionsMenu(final Menu menu) {
		menu.add(0, MENU_CLEAR, 0, "Clear").setIcon(
			android.R.drawable.ic_menu_delete);
		return true;
	}

	@Override
	public final boolean onMenuItemSelected(
		final int featureId,
		final MenuItem item) {
		switch (item.getItemId()) {
		case MENU_CLEAR:
			chatText.setText("");
			return true;
		default:
			return super.onMenuItemSelected(featureId, item);
		}
	}

	@Override
	protected IServiceObserver createServiceObserver() {
		return new ChatServiceObserver();
	}

	@Override
	protected void onConnected() {

		final List<Message> messages = mService.getMessageList();
		for (final Message m : messages) {
			addMessage(m);
		}

		this.receiverChannel = mService.getCurrentChannel();
		this.receiverAdapter.clear();

		//TODO: Don't list not accessible files.
		int idx = 0;
		for (final Channel channel : this.mService.getChannelList()) {
			this.receiverAdapter.add(new ChannelItem(channel));
			if (channel == this.receiverChannel) {
				this.receiver.setSelection(idx);
			}
			idx++;
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.chat_view);

		chatText = (TextView) findViewById(R.id.chatText);
		chatText.setMovementMethod(ScrollingMovementMethod.getInstance());
		chatTextEdit = (EditText) findViewById(R.id.chatTextEdit);
		chatTextEdit.setOnEditorActionListener(chatTextEditActionEvent);
		findViewById(R.id.send_button).setOnClickListener(sendOnClickEvent);
		this.receiverAdapter = new ArrayAdapter<ChannelItem>(
			this,
			android.R.layout.simple_spinner_item);
		this.receiverAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		this.receiver = (Spinner) findViewById(R.id.chatReceiver);
		this.receiver.setAdapter(this.receiverAdapter);
		this.receiver.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(
				final AdapterView<?> parent,
				final View view,
				final int pos,
				final long id) {
				// Ugly but not avoidable :(
				ChatActivity.this.receiverChannel = ((ChannelItem) parent.getItemAtPosition(pos)).getChannel();
			}

			@Override
			public void onNothingSelected(final AdapterView<?> arg0) {
				/* Nothing to do here */
			}
		});

		updateText();
	}

	void addMessage(final Message msg) {
		final StringBuilder sb = new StringBuilder();
		sb.append("[");
		sb.append(DateUtils.formatDateTime(
			this,
			msg.timestamp,
			DateUtils.FORMAT_SHOW_TIME));
		sb.append("]");

		if (msg.direction == Message.DIRECTION_SENT) {
			sb.append("To ");
			sb.append(msg.channel.name);
		} else {
			if (msg.channelIds > 0) {
				sb.append("(C) ");
			}
			if (msg.treeIds > 0) {
				sb.append("(T) ");
			}

			if (msg.actor != null) {
				sb.append(msg.actor.name);
			} else {
				sb.append("Server");
			}
		}
		sb.append(": ");
		sb.append(msg.message);
		sb.append("\n");
		chatText.append(sb.toString());
	}

	void sendMessage(final TextView v) {
		mService.sendChannelTextMessage(
			v.getText().toString(),
			this.receiverChannel);
		v.setText("");
	}

	void updateText() {
		chatText.beginBatchEdit();
		chatText.setText("");
//		for (final String s : ServerList.client.chatList) {
//			chatText.append(s);
//		}
//		chatText.endBatchEdit();
//		chatText.post(new Runnable() {
//			@Override
//			public void run() {
//				chatText.scrollTo(0, chatText.getHeight());
//			}
//		});
	}
}
