package com.hoxfon.react.RNTwilioVoice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;

import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_PHONE_CALL_STARTED;
import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_PHONE_CALL_ENDED;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.TAG;

public class PhoneCallManager {
    private ReactApplicationContext appContext;
    private EventManager eventManager;
    private boolean isReceiverRegistered = false;
    private PhoneCallBroadcastReceiver phoneCallBroadcastReceiver;

    PhoneCallManager(ReactApplicationContext appContext, EventManager eventManager) {
        this.appContext = appContext;
        this.eventManager = eventManager;
        phoneCallBroadcastReceiver = new PhoneCallBroadcastReceiver();
        registerReceiver();
    }

    private void registerReceiver() {
        if (!isReceiverRegistered) {
            IntentFilter phoneCallIntentFilter = new IntentFilter();
            phoneCallIntentFilter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
            appContext.registerReceiver(
                    phoneCallBroadcastReceiver, phoneCallIntentFilter);

            isReceiverRegistered = true;
        }
    }

    private class PhoneCallBroadcastReceiver extends BroadcastReceiver {

        private int lastState = TelephonyManager.CALL_STATE_IDLE;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "received onReceive action 1 " + intent.toString());
            }
            String stateStr = intent.getExtras().getString(TelephonyManager.EXTRA_STATE);
            int state = 0;
            if(stateStr.equals(TelephonyManager.EXTRA_STATE_IDLE)){
                state = TelephonyManager.CALL_STATE_IDLE;
            }
            else if(stateStr.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)){
                state = TelephonyManager.CALL_STATE_OFFHOOK;
            }
            else if(stateStr.equals(TelephonyManager.EXTRA_STATE_RINGING)){
                state = TelephonyManager.CALL_STATE_RINGING;
            }

            onCallStateChanged(context, state);
            lastState = state;
        }

        public void onCallStateChanged(Context context, int state) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "received onCallStateChanged action " + state + " lastState " + lastState);
            }
            if (lastState == state) {
                //No change, debounce extras
                return;
            }
            else {
                switch (state)
                {
                    case TelephonyManager.CALL_STATE_IDLE:
                        if (lastState == TelephonyManager.CALL_STATE_OFFHOOK) {
                            eventManager.sendEvent(EVENT_PHONE_CALL_ENDED, null);
                        }
                        break;
                    case TelephonyManager.CALL_STATE_RINGING:
                        break;
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                            eventManager.sendEvent(EVENT_PHONE_CALL_STARTED, null);
                        }
                        break;
                    default:
                        break;
                }
            }
        }
    }
}
