package com.hoxfon.react.RNTwilioVoice.fcm;

import android.app.ActivityManager;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.hoxfon.react.RNTwilioVoice.Constants;
import com.hoxfon.react.RNTwilioVoice.IncomingCallNotificationService;
import com.twilio.voice.CallException;
import com.hoxfon.react.RNTwilioVoice.BuildConfig;
import com.hoxfon.react.RNTwilioVoice.CallNotificationManager;
import com.twilio.voice.CallInvite;
import com.twilio.voice.CancelledCallInvite;
import com.twilio.voice.MessageListener;
import com.twilio.voice.Voice;

import java.util.Map;
import java.util.Random;

import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.TAG;

public class VoiceFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Intent intent = new Intent(Constants.ACTION_FCM_TOKEN);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    /**
     * Called when message is received.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "rws Bundle data: " + remoteMessage.getData());
        }

        // Check if message contains a data payload.
        if (remoteMessage.getData().size() > 0) {
            Map<String, String> data = remoteMessage.getData();

            // If notification ID is not provided by the user for push notification, generate one at random
            Random randomNumberGenerator = new Random(System.currentTimeMillis());
            final int notificationId = randomNumberGenerator.nextInt();
            Log.d(TAG, "WE BE HERE!");

            boolean valid = Voice.handleMessage(this, remoteMessage.getData(), new MessageListener() {
                @Override
                public void onCallInvite(@NonNull CallInvite callInvite) {
                    final int notificationId = (int) System.currentTimeMillis();
                    Log.d(TAG, "rws - onCallInvite: " + callInvite);
                    Log.d(TAG, "rws - getCustomParameters: " + callInvite.getCustomParameters().get("voter_id"));

                    Handler handler = new Handler(Looper.getMainLooper());
                    Log.d(TAG, "rws - handler: " + handler);
                    handler.post(new Runnable() {
                        public void run() {
                            Log.d(TAG, "rws - new Runnable()");
                            CallNotificationManager callNotificationManager = new CallNotificationManager();
                            // Construct and load our normal React JS code bundle
                            ReactInstanceManager mReactInstanceManager = ((ReactApplication) getApplication()).getReactNativeHost().getReactInstanceManager();
                            ReactContext context = mReactInstanceManager.getCurrentReactContext();

                            // initialise appImportance to the highest possible importance in case context is null
                            int appImportance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE;

                            if (context != null) {
                                appImportance = callNotificationManager.getApplicationImportance((ReactApplicationContext)context);
                            }
                            if (BuildConfig.DEBUG) {
                                Log.d(TAG, "rws - context: " + context + ". appImportance = " + appImportance + "ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE= "+ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE);
                            }

                            // when the app is not started or in the background
                            if (appImportance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
                                if (BuildConfig.DEBUG) {
                                    Log.d(TAG, "Background");
                                }
                                handleInvite(callInvite, notificationId);
                                return;
                            }

                            Intent intent = new Intent(Constants.ACTION_INCOMING_CALL);
                            intent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId);
                            intent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite);
                            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
//                            handleInvite(callInvite, notificationId);
                        }
                    });
                }

                @Override
                public void onCancelledCallInvite(@NonNull CancelledCallInvite cancelledCallInvite, @Nullable CallException callException) {
                    Log.d(TAG, "onCancelledCallInvite: " + cancelledCallInvite);
                    // The call is prematurely disconnected by the caller.
                    // The callee does not accept or reject the call within 30 seconds.
                    // The Voice SDK is unable to establish a connection to Twilio.
                    handleCancelledCallInvite(cancelledCallInvite, callException);
                }
            });

            if (!valid) {
                Log.e(TAG, "The message was not a valid Twilio Voice SDK payload: " + remoteMessage.getData());
            }
        }

        // Check if message contains a notification payload.
        if (remoteMessage.getNotification() != null) {
            Log.e(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
        }
    }

    private void handleInvite(CallInvite callInvite, int notificationId) {
        Intent intent = new Intent(this, IncomingCallNotificationService.class);
        intent.setAction(Constants.ACTION_INCOMING_CALL);
        intent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId);
        intent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite);
        startService(intent);
    }

    private void handleCancelledCallInvite(CancelledCallInvite cancelledCallInvite, CallException callException) {
        Intent intent = new Intent(this, IncomingCallNotificationService.class);
        intent.setAction(Constants.ACTION_CANCEL_CALL);
        intent.putExtra(Constants.CANCELLED_CALL_INVITE, cancelledCallInvite);
        if (callException != null) {
            intent.putExtra(Constants.CANCELLED_CALL_INVITE_EXCEPTION, callException.getMessage());
        }
        startService(intent);
    }
}
