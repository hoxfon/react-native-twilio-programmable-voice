package com.hoxfon.react.TwilioVoice.gcm;

import android.annotation.TargetApi;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
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
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.INCOMING_CALL_NOTIFICATION_ID;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.CALL_SID_KEY;

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
                Log.d(LOG_TAG, "on voice message received: context "+context);
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
        Log.d(LOG_TAG, "prepareNotification messageTyp: "+bundle.getString("twi_message_type"));
        sendIncomingCallMessageToActivity(context, incomingCallMessage, bundle);
        if (!notificationHelper.isAppInForeground(context)) {
            showNotification(context, incomingCallMessage, bundle);
        }
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
        intent.putExtra(INCOMING_CALL_NOTIFICATION_ID, notificationId);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    /*
     * Show the notification in the Android notification drawer
     */
    @TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
    private void showNotification(ReactApplicationContext context, IncomingCallMessage incomingCallMessage, Bundle bundle) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (!incomingCallMessage.isCancelled()) {
            notificationHelper.sendIncomingCallNotification(context, incomingCallMessage, bundle);
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                /*
                 * If the incoming call was cancelled then remove the notification by matching
                 * it with the call sid from the list of notifications in the notification drawer.
                 */
                StatusBarNotification[] activeNotifications = notificationManager.getActiveNotifications();
                for (StatusBarNotification statusBarNotification : activeNotifications) {
                    Notification notification = statusBarNotification.getNotification();
                    Bundle extras = notification.extras;
                    String notificationCallSid = extras.getString(CALL_SID_KEY);
                    if (incomingCallMessage.getCallSid().equals(notificationCallSid)) {
                        notificationManager.cancel(extras.getInt(INCOMING_CALL_NOTIFICATION_ID));
                    }
                }
            } else {
                /*
                 * Prior to Android M the notification manager did not provide a list of
                 * active notifications so we lazily clear all the notifications when
                 * receiving a cancelled call.
                 *
                 * In order to properly cancel a notification using
                 * NotificationManager.cancel(notificationId) we should store the call sid &
                 * notification id of any incoming calls using shared preferences or some other form
                 * of persistent storage.
                 */
                // don't do this. It will remove the HANG UP ongoing notification
                // notificationManager.cancelAll();
            }
        }
    }
}
