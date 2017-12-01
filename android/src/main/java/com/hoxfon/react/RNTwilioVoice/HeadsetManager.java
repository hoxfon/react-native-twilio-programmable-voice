package com.hoxfon.react.RNTwilioVoice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;

import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_WIRED_HEADSET;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.TAG;

public class HeadsetManager {

    private BroadcastReceiver wiredHeadsetReceiver;
    private static final String ACTION_HEADSET_PLUG = (android.os.Build.VERSION.SDK_INT >= 21)
        ? AudioManager.ACTION_HEADSET_PLUG
        : Intent.ACTION_HEADSET_PLUG;

    private EventManager eventManager;

    public HeadsetManager(EventManager em) {
        eventManager = em;
    }

    public void startWiredHeadsetEvent(ReactApplicationContext context) {
        if (wiredHeadsetReceiver != null) {
            return;
        }
        if (context == null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "startWiredHeadsetEvent() reactContext is null");
            }
            return;
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "startWiredHeadsetEvent()");
        }
        wiredHeadsetReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_HEADSET_PLUG.equals(intent.getAction())) {
                    String deviceName = intent.getStringExtra("name");
                    if (deviceName == null) {
                        deviceName = "";
                    }
                    WritableMap data = Arguments.createMap();
                    data.putBoolean("isPlugged", (intent.getIntExtra("state", 0) == 1) ? true : false);
                    data.putBoolean("hasMic", (intent.getIntExtra("microphone", 0) == 1) ? true : false);
                    data.putString("deviceName", deviceName);
                    eventManager.sendEvent(EVENT_WIRED_HEADSET, data);
                }
            }
        };
        context.registerReceiver(wiredHeadsetReceiver, new IntentFilter(ACTION_HEADSET_PLUG));
    }

    public void stopWiredHeadsetEvent(ReactApplicationContext context) {
        if (wiredHeadsetReceiver == null) {
            return;
        }
        if (context == null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "stopWiredHeadsetEvent() reactContext is null");
            }
            return;
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "stopWiredHeadsetEvent()");
        }
        context.unregisterReceiver(wiredHeadsetReceiver);
        wiredHeadsetReceiver = null;
    }
}
