package com.hoxfon.react.TwilioVoice.gcm;

import android.annotation.TargetApi;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.Context;
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
import com.twilio.voice.CallInvite;

import java.util.Random;

import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.LOG_TAG;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.ACTION_INCOMING_CALL;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.INCOMING_CALL_INVITE;
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
        Log.d(LOG_TAG, "VoiceGCMListenerService::onMessageReceived senderId " + from);

        if (!CallInvite.isValidMessage(bundle)) {
            return;
        }

        // If notification ID is not provided by the user for push notification, generate one at random
        if (bundle.getString("id") == null) {
            Random randomNumberGenerator = new Random(System.currentTimeMillis());
            bundle.putString("id", String.valueOf(randomNumberGenerator.nextInt()));
        }

        /*
         * Create an CallInvite from the bundle
         */
        final CallInvite callInvite = CallInvite.create(bundle);

        // We need to run this on the main thread, as the React code assumes that is true.
        // Namely, DevServerHelper constructs a Handler() without a Looper, which triggers:
        // "Can't create handler inside thread that has not called Looper.prepare()"
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                Boolean shouldBroadcastIntent = true;
                // Construct and load our normal React JS code bundle
                ReactInstanceManager mReactInstanceManager = ((ReactApplication) getApplication()).getReactNativeHost().getReactInstanceManager();
                ReactContext context = mReactInstanceManager.getCurrentReactContext();
                // If it's constructed, send a notification
                if (context != null) {
                    int appImportance = notificationHelper.getApplicationImportance((ReactApplicationContext)context);
                    Intent launchIntent = notificationHelper.getLaunchIntent(
                            (ReactApplicationContext)context,
                            bundle,
                            callInvite,
                            false,
                            appImportance
                    );
                    if (appImportance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE) {
                        context.startActivity(launchIntent);
                        shouldBroadcastIntent = false;
                    }
                    KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
                    Boolean shouldShowIncomingCallNotification = false;
                    if (keyguardManager.inKeyguardRestrictedInputMode() ||
                            (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && keyguardManager.isDeviceLocked())
                    ) {
                        shouldShowIncomingCallNotification = true;
                    }
                    handleIncomingCall((ReactApplicationContext)context, bundle, callInvite, launchIntent, shouldBroadcastIntent, shouldShowIncomingCallNotification);
                } else {
                    // Otherwise wait for construction, then handle the incoming call
                    mReactInstanceManager.addReactInstanceEventListener(new ReactInstanceManager.ReactInstanceEventListener() {
                        public void onReactContextInitialized(ReactContext context) {
                            int appImportance = notificationHelper.getApplicationImportance((ReactApplicationContext)context);
                            Intent launchIntent = notificationHelper.getLaunchIntent((ReactApplicationContext)context, bundle, callInvite, true, appImportance);
                            context.startActivity(launchIntent);
                            handleIncomingCall((ReactApplicationContext)context, bundle, callInvite, launchIntent, true, true);
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

    private void handleIncomingCall(ReactApplicationContext context,
                                    final Bundle bundle,
                                    CallInvite callInvite,
                                    Intent launchIntent,
                                    Boolean shouldBroadcastIntent,
                                    Boolean showIncomingCallNotification
    ) {
        if (shouldBroadcastIntent) {
            sendIncomingCallMessageToActivity(context, callInvite, bundle);
        }
        showNotification(context, callInvite, bundle, launchIntent, showIncomingCallNotification);
    }

    /*
     * Send the IncomingCallMessage to the TwilioVoiceModule
     */
    private void sendIncomingCallMessageToActivity(
            ReactApplicationContext context,
            CallInvite callInvite,
            Bundle bundle
    ) {
        int notificationId = Integer.parseInt(bundle.getString("id"));
        Intent intent = new Intent(ACTION_INCOMING_CALL);
        intent.putExtra(INCOMING_CALL_INVITE, callInvite);
        intent.putExtra(NOTIFICATION_ID, notificationId);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    /*
     * Show the notification in the Android notification drawer
     */
    @TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
    private void showNotification(ReactApplicationContext context,
                                  CallInvite callInvite,
                                  Bundle bundle,
                                  Intent launchIntent,
                                  Boolean showIncomingCallNotification
    ) {
        Log.d(LOG_TAG, "showNotification messageType: "+bundle.getString("twi_message_type"));
        if (!callInvite.isCancelled()) {
            if (showIncomingCallNotification) {
                notificationHelper.createIncomingCallNotification(context, callInvite, bundle, launchIntent);
            }
        } else {
            Log.d(LOG_TAG, "incoming call cancelled");
            notificationHelper.removeIncomingCallNotification(context, callInvite, 0);
        }
    }
}
