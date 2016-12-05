package com.hoxfon.react.TwilioVoice.gcm;

import android.annotation.TargetApi;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;

import com.google.android.gms.gcm.GcmListenerService;
import com.hoxfon.react.TwilioVoice.NotificationHelper;
import com.twilio.voice.IncomingCallMessage;

import java.util.Random;

import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.LOG_TAG;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.ACTION_INCOMING_CALL;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.INCOMING_CALL_MESSAGE;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.NOTIFICATION_ID;

public class VoiceGCMListenerService extends GcmListenerService {

    private NotificationHelper notificationHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationHelper = new NotificationHelper();
    }

    @Override
    public void onMessageReceived(String from, final Bundle bundle) {
        Log.d(LOG_TAG, "onMessageReceived senderId " + from);

        // We need to run this on the main thread, as the React code assumes that is true.
        // Namely, DevServerHelper constructs a Handler() without a Looper, which triggers:
        // "Can't create handler inside thread that has not called Looper.prepare()"
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                // Construct and load our normal React JS code bundle
                ReactInstanceManager mReactInstanceManager = ((ReactApplication) getApplication()).getReactNativeHost().getReactInstanceManager();
                ReactContext context = mReactInstanceManager.getCurrentReactContext();
                // If it's constructed, send a notification
                if (context != null) {
                    prepareNotification((ReactApplicationContext)context, bundle);
                } else {
                    // Otherwise wait for construction, then send the notification
                    mReactInstanceManager.addReactInstanceEventListener(new ReactInstanceManager.ReactInstanceEventListener() {
                        public void onReactContextInitialized(ReactContext context) {
                            prepareNotification((ReactApplicationContext)context, bundle);
                        }
                    });
                    if (!mReactInstanceManager.hasStartedCreatingInitialContext()) {
                        // Construct it in the background
                        mReactInstanceManager.createReactContextInBackground();
                    }
                }
            }
        });
    }

    private void prepareNotification(ReactApplicationContext context, final Bundle bundle) {
        if (!IncomingCallMessage.isValidMessage(bundle)) {
            return;
        }
        // If notification ID is not provided by the user for push notification, generate one at random
        if (bundle.getString("id") == null) {
            Random randomNumberGenerator = new Random(System.currentTimeMillis());
            bundle.putString("id", String.valueOf(randomNumberGenerator.nextInt()));
        }
        /*
         * Create an IncomingCallMessage from the bundle
         */
        IncomingCallMessage incomingCallMessage = new IncomingCallMessage(bundle);
        sendIncomingCallMessageToActivity(context, incomingCallMessage, bundle);
        showNotification(context, incomingCallMessage, bundle);
    }

    /*
     * Send the IncomingCallMessage to the TwilioVoiceModule
     */
    private void sendIncomingCallMessageToActivity(
            ReactApplicationContext context,
            IncomingCallMessage incomingCallMessage,
            Bundle bundle
    ) {
        int notificationId = Integer.parseInt(bundle.getString("id"));
        Intent intent = new Intent(ACTION_INCOMING_CALL);
        intent.putExtra(INCOMING_CALL_MESSAGE, incomingCallMessage);
        intent.putExtra(NOTIFICATION_ID, notificationId);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    /*
     * Show the notification in the Android notification drawer
     */
    @TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
    private void showNotification(ReactApplicationContext context, IncomingCallMessage incomingCallMessage, Bundle bundle) {
        Log.d(LOG_TAG, "showNotification messageType: "+bundle.getString("twi_message_type"));
        if (!incomingCallMessage.isCancelled()) {
            notificationHelper.createIncomingCallNotification(context, incomingCallMessage, bundle);
        } else {
            Log.d(LOG_TAG, "incoming call cancelled");
            notificationHelper.removeIncomingCallNotification(context, incomingCallMessage, 0);
        }
    }
}
