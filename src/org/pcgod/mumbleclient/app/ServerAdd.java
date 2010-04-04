package org.pcgod.mumbleclient.app;

import org.pcgod.mumbleclient.R;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

public class ServerAdd extends Activity {
	private OnClickListener addButtonListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			String host = ((EditText)findViewById(R.id.serverHostEdit)).getText().toString();
			int port = Integer.parseInt(((EditText)findViewById(R.id.serverPortEdit)).getText().toString());
			String username = ((EditText)findViewById(R.id.serverUsernameEdit)).getText().toString();
			String password = ((EditText)findViewById(R.id.serverPasswordEdit)).getText().toString();

			DbAdapter db = new DbAdapter(v.getContext());
			db.open();
			db.createServer(host, port, username, password);
			db.close();
			
			finish();
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.server_add);

		Button addButton = (Button) findViewById(R.id.serverAdd);
		addButton.setOnClickListener(addButtonListener);
	}
}
