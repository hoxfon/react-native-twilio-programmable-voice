package com.hoxfon.react.RNTwilioVoice.screens;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import com.facebook.react.ReactActivity;
import com.facebook.react.bridge.ReactContext;
import com.hoxfon.react.RNTwilioVoice.R;

import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_ALLOW_VISITOR;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_DISCONNECTED_CALL;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_REJECT_VISITOR;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_SPEAKER_OFF;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_SPEAKER_ON;

/**
 * Created by estebanabait on 6/7/18.
 */

public class AutomaticCallScreenActivity extends ReactActivity {
  public static String TAG = "RNTwilioVoice.AutomaticCallScreen";
  private AutomaticCallScreenActivity.AutomaticCallBroadcastReceiver automaticCallBroadcastReceiver;
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

    setContentView(R.layout.activity_automatic_call);

    final ReactContext reactContext = getReactInstanceManager().getCurrentReactContext();

    automaticCallBroadcastReceiver = new AutomaticCallScreenActivity.AutomaticCallBroadcastReceiver();
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

    Button allowVisitorBtn = (Button) findViewById(R.id.allow_visitor_btn);
    allowVisitorBtn.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        Intent intent = new Intent(ACTION_ALLOW_VISITOR);
        LocalBroadcastManager.getInstance(reactContext).sendBroadcast(intent);
        finish();
      }
    });

    Button rejectVisitorBtn = (Button) findViewById(R.id.reject_visitor_btn);
    rejectVisitorBtn.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        Intent intent = new Intent(ACTION_REJECT_VISITOR);
        LocalBroadcastManager.getInstance(reactContext).sendBroadcast(intent);
        finish();
      }
    });
  }

  @Override protected void onPause() {
    super.onPause();
    if (isReceiverRegistered) {
      LocalBroadcastManager.getInstance(
          getReactInstanceManager().getCurrentReactContext()
      ).unregisterReceiver(automaticCallBroadcastReceiver);
    }
  }

  private class AutomaticCallBroadcastReceiver extends BroadcastReceiver {

    @Override public void onReceive(Context context, Intent intent) {

      String action = intent.getAction();
      Log.d(TAG, "ACTION RECEIVED " + action);
      if (action.equals(ACTION_DISCONNECTED_CALL)) {
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
      intentFilter.addAction(ACTION_DISCONNECTED_CALL);

      LocalBroadcastManager.getInstance(getReactInstanceManager().getCurrentReactContext())
          .registerReceiver(automaticCallBroadcastReceiver, intentFilter);

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
