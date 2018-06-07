package com.hoxfon.react.RNTwilioVoice.screens;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import com.facebook.react.ReactActivity;
import com.facebook.react.bridge.ReactContext;
import com.hoxfon.react.RNTwilioVoice.R;

import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_ACCEPTED_CALL;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_DISCONNECTED_CALL;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_REJECTED_CALL;

/**
 * Created by estebanabait on 6/5/18.
 */

public class UnlockScreenActivity extends ReactActivity {

  public static String TAG = "RNTwilioVoice";
  private UnlockScreenActivity.UnlockScreenBroadcastReceiver unlockScreenBroadcastReceiver;
  private boolean isReceiverRegistered = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    getWindow().addFlags(
        WindowManager.LayoutParams.FLAG_FULLSCREEN
            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
    );

    setContentView(R.layout.activity_unlock);

    final ReactContext reactContext = getReactInstanceManager().getCurrentReactContext();
    unlockScreenBroadcastReceiver = new UnlockScreenActivity.UnlockScreenBroadcastReceiver();
    registerReceiver();

    Button acceptCallBtn = (Button) findViewById(R.id.accept_call_btn);
    acceptCallBtn.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        Intent intent = new Intent(ACTION_ACCEPTED_CALL);
        LocalBroadcastManager.getInstance(reactContext).sendBroadcast(intent);
        finish();
      }
    });

    Button rejectCallBtn = (Button) findViewById(R.id.reject_call_btn);
    rejectCallBtn.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        Intent intent = new Intent(ACTION_REJECTED_CALL);
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
      ).unregisterReceiver(unlockScreenBroadcastReceiver);
    }
  }

  private class UnlockScreenBroadcastReceiver extends BroadcastReceiver {

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

      LocalBroadcastManager.getInstance(getReactInstanceManager().getCurrentReactContext()).registerReceiver(
          unlockScreenBroadcastReceiver, intentFilter);

      isReceiverRegistered = true;
    }
  }

}
