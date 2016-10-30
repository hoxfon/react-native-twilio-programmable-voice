package com.hoxfon.react.TwilioVoice.gcm;

import android.annotation.TargetApi;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;

import com.google.android.gms.gcm.GcmListenerService;
import com.twilio.voice.IncomingCallMessage;
import com.hoxfon.react.TwilioVoice.R;

import java.util.Random;


import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.LOG_TAG;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.ACTION_ANSWER_CALL;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.ACTION_REJECT_CALL;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.ACTION_INCOMING_CALL;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.INCOMING_CALL_MESSAGE;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.INCOMING_CALL_NOTIFICATION_ID;

public class VoiceGCMListenerService extends GcmListenerService {

    /*
     * Notification related keys
     */
    private static final String NOTIFICATION_ID_KEY = "NOTIFICATION_ID";
    private static final String CALL_SID_KEY = "CALL_SID";

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
         * Generate a unique notification id using the system time
         */
        int notificationId = Integer.parseInt(bundle.getString("id"));
        Log.d(LOG_TAG, "voice call notification id: "+notificationId);

        /*
         * Create an IncomingCallMessage from the bundle
         */
        IncomingCallMessage incomingCallMessage = new IncomingCallMessage(bundle);
        sendIncomingCallMessageToActivity(context, incomingCallMessage, notificationId);
        showNotification(context, incomingCallMessage, notificationId);
    }

    /*
     * Show the notification in the Android notification drawer
     */
    @TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
    private void showNotification(ReactApplicationContext context, IncomingCallMessage incomingCallMessage, int notificationId) {
        String callSid = incomingCallMessage.getCallSid();
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (!incomingCallMessage.isCancelled()) {
            /*
             * Create a PendingIntent to specify the action when the notification is
             * selected in the notification drawer
             */
            Intent intent = new Intent(context, getMainActivityClass(context));
            intent.setAction(ACTION_INCOMING_CALL);
            intent.putExtra(INCOMING_CALL_MESSAGE, incomingCallMessage);
            intent.putExtra(INCOMING_CALL_NOTIFICATION_ID, notificationId);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT);

            /*
             * Pass the notification id and call sid to use as an identifier to cancel the
             * notification later
             */
            Bundle extras = new Bundle();
            extras.putInt(NOTIFICATION_ID_KEY, notificationId);
            extras.putString(CALL_SID_KEY, callSid);

            Resources res = context.getResources();
            String packageName = context.getPackageName();
            int largeIconResId = res.getIdentifier("ic_launcher", "mipmap", packageName);
            Bitmap largeIconBitmap = BitmapFactory.decodeResource(res, largeIconResId);

            /*
             * Create the notification shown in the notification drawer
             */
            NotificationCompat.Builder notification =
                    new NotificationCompat.Builder(context)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setSmallIcon(R.drawable.ic_call_white_24dp)
                            .setContentTitle("Incoming call")
                            .setContentText(incomingCallMessage.getFrom() + " is calling.")
                            .setAutoCancel(true)
                            .setExtras(extras)
                            .setContentIntent(pendingIntent)
                            .setColor(Color.rgb(214, 10, 37));
            if (largeIconResId != 0 && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)) {
                notification.setLargeIcon(largeIconBitmap);
            }

            // Answer action
            Intent answerIntent = new Intent();
            answerIntent.putExtra(INCOMING_CALL_MESSAGE, incomingCallMessage);
            answerIntent.setAction(ACTION_ANSWER_CALL);

            PendingIntent pendingAnswerIntent = PendingIntent.getBroadcast(context, 1, answerIntent,
                    PendingIntent.FLAG_ONE_SHOT);
            notification.addAction(0, "Answer", pendingAnswerIntent);

            // Reject action
            Intent rejectIntent = new Intent();
            rejectIntent.putExtra(INCOMING_CALL_MESSAGE, incomingCallMessage);
            rejectIntent.setAction(ACTION_REJECT_CALL);

            PendingIntent pendingRejectIntent = PendingIntent.getBroadcast(context, 1, rejectIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            notification.addAction(0, "Reject", pendingRejectIntent);

            notificationManager.notify(notificationId, notification.build());
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
                    if (callSid.equals(notificationCallSid)) {
                        notificationManager.cancel(extras.getInt(NOTIFICATION_ID_KEY));
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
                notificationManager.cancelAll();
            }
        }
    }

    public Class getMainActivityClass(ReactApplicationContext context) {
        String packageName = context.getPackageName();
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        String className = launchIntent.getComponent().getClassName();
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    /*
     * Send the IncomingCallMessage to the TwilioVoiceModule
     */
    private void sendIncomingCallMessageToActivity(
            ReactApplicationContext context,
            IncomingCallMessage incomingCallMessage,
            int notificationId
    ) {
        Log.d(LOG_TAG, "sendIncomingCallMessageToActivity()");
        Intent intent = new Intent(ACTION_INCOMING_CALL);
        intent.putExtra(INCOMING_CALL_MESSAGE, incomingCallMessage);
        intent.putExtra(INCOMING_CALL_NOTIFICATION_ID, notificationId);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
}
