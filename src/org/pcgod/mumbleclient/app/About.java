package org.pcgod.mumbleclient.app;

import android.app.Activity;

import android.os.Bundle;
import android.view.Window;
import org.pcgod.mumbleclient.R;

public class About extends Activity {
	@Override
	protected final void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.about);
		}
	}
