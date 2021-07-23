package com.hoxfon.react.RNTwilioVoice.screens;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import com.facebook.react.ReactActivity;
import com.facebook.react.bridge.ReactContext;
import com.hoxfon.react.RNTwilioVoice.R;

import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_CANCEL_CALL_INVITE;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_DISCONNECTED_CALL;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_HANGUP_CALL;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_SPEAKER_OFF;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_SPEAKER_ON;

/**
 * Created by estebanabait on 6/6/18.
 */

public class DirectCallScreenActivity extends ReactActivity {

  public static String TAG = "RNTwilioVoice.DirectCallScreen";
  private DirectCallScreenActivity.DirectCallBroadcastReceiver directCallBroadcastReceiver;
  private boolean isReceiverRegistered = false;
  private boolean isSpeakerOn = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    getWindow().addFlags(
        WindowManager.LayoutParams.FLAG_FULLSCREEN
            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
    );

    setContentView(R.layout.activity_direct_call);

    final ReactContext reactContext = getReactInstanceManager().getCurrentReactContext();

    directCallBroadcastReceiver = new DirectCallScreenActivity.DirectCallBroadcastReceiver();
    registerReceiver();

    Button speakerBtn = (Button) findViewById(R.id.speaker_btn);
    speakerBtn.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        String action;
        toggleSpeaker();
        if (isSpeakerOn()) {
          action = ACTION_SPEAKER_ON;
        } else {
          action = ACTION_SPEAKER_OFF;
        }
        updateSpeakerButton();
        Intent intent = new Intent(action);
        LocalBroadcastManager.getInstance(reactContext).sendBroadcast(intent);
      }
    });

    Button disconnectCallBtn = (Button) findViewById(R.id.disconnect_call_btn);
    disconnectCallBtn.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        Intent intent = new Intent(ACTION_HANGUP_CALL);
        LocalBroadcastManager.getInstance(reactContext).sendBroadcast(intent);
        finish();
      }
    });
  }

  private class DirectCallBroadcastReceiver extends BroadcastReceiver {

    @Override public void onReceive(Context context, Intent intent) {

      String action = intent.getAction();
      Log.d(TAG, "ACTION RECEIVED " + action);
      if (action.equals(ACTION_CANCEL_CALL_INVITE) || action.equals(ACTION_DISCONNECTED_CALL)) {
        finish();
      }

    }
  }

  /**
   * Register the Voice broadcast receiver
   */
  private void registerReceiver() {

    if (!isReceiverRegistered) {
      IntentFilter intentFilter = new IntentFilter();
      intentFilter.addAction(ACTION_CANCEL_CALL_INVITE);
      intentFilter.addAction(ACTION_DISCONNECTED_CALL);

      LocalBroadcastManager.getInstance(getReactInstanceManager().getCurrentReactContext())
          .registerReceiver(directCallBroadcastReceiver, intentFilter);

      isReceiverRegistered = true;
    }
  }

  private void toggleSpeaker() {
    this.isSpeakerOn = !this.isSpeakerOn;
  }

  private boolean isSpeakerOn() {
    return this.isSpeakerOn;
  }

  private void updateSpeakerButton() {
    Button speakerBtn = (Button) findViewById(R.id.speaker_btn);
    int resourceId;
    if (isSpeakerOn()) {
      resourceId = R.drawable.speaker_off;
    } else {
      resourceId = R.drawable.speaker_on;
    }
    Drawable top = getResources().getDrawable(resourceId);
    speakerBtn.setCompoundDrawablesWithIntrinsicBounds(
        null,
        top,
        null,
        null
    );
  }

}
