package org.pcgod.mumbleclient.app;

import org.pcgod.mumbleclient.service.MumbleService;
import org.pcgod.mumbleclient.service.MumbleServiceConnection;
import org.pcgod.mumbleclient.R;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class ChatActivity extends Activity {
	private class ChatBroadcastReceiver extends BroadcastReceiver {
		@Override
		public final void onReceive(final Context ctx, final Intent i) {
			updateText();
		}
	}

	MumbleServiceConnection mServiceConn = new MumbleServiceConnection(this);
	MumbleService mService;

	TextView chatText;
	EditText chatTextEdit;

	private static final int MENU_CLEAR = Menu.FIRST;

	private OnEditorActionListener chatTextEditActionEvent = new OnEditorActionListener() {
		@Override
		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
			if (event != null && !event.isShiftPressed() && v != null) {
				View focus = v.focusSearch(View.FOCUS_RIGHT);
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

	private OnClickListener sendOnClickEvent = new OnClickListener() {
		@Override
		public void onClick(final View v) {
			sendMessage(chatTextEdit);
		}
	};

	private ChatBroadcastReceiver bcReceiver;

	@Override
	public final boolean onCreateOptionsMenu(final Menu menu) {
		menu.add(0, MENU_CLEAR, 0, "Clear").setIcon(
				android.R.drawable.ic_menu_delete);
		return true;
	}

	@Override
	public final boolean onMenuItemSelected(final int featureId,
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
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.chat_view);

		chatText = (TextView) findViewById(R.id.chatText);
		chatText.setMovementMethod(ScrollingMovementMethod.getInstance());
		chatTextEdit = (EditText) findViewById(R.id.chatTextEdit);
		chatTextEdit.setOnEditorActionListener(chatTextEditActionEvent);
		findViewById(R.id.send_button).setOnClickListener(sendOnClickEvent);
		updateText();
	}

	@Override
	protected final void onPause() {
		super.onPause();

		unregisterReceiver(bcReceiver);
	}

	@Override
	protected final void onResume() {
		super.onResume();

		updateText();
		final IntentFilter ifilter = new IntentFilter(
				MumbleService.INTENT_CHAT_TEXT_UPDATE);
		bcReceiver = new ChatBroadcastReceiver();
		registerReceiver(bcReceiver, ifilter);
	}

	void sendMessage(TextView v) {
		mService.sendChannelTextMessage(v.getText().toString());
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
