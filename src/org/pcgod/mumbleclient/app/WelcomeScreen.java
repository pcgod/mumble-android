package org.pcgod.mumbleclient.app;

import org.pcgod.mumbleclient.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;

public class WelcomeScreen extends Activity {
    protected boolean active = true;
    protected int splashTime = 1500;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.splash);
        
        ImageView splash = (ImageView) findViewById(R.id.splashId);
        splash.setOnTouchListener(new OnTouchListener() {
			
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_DOWN) active = false;
		        return true;
			}
		});
        
        Thread splashTread = new Thread() {
            @Override
            public void run() {
                try {
                    int waited = 0;
                    while(active && (waited < splashTime)) {
                        sleep(100);
                        if(active) {
                            waited += 100;
                        }
                    }
                } catch(InterruptedException e) {
                	// interrupted
                } finally {
                    finish();
                    startMain();
                    stop();
                }
            }
        };
        splashTread.start();
    }
    
    private void startMain() {
    	startActivity(new Intent(this, ServerList.class));
    }
}