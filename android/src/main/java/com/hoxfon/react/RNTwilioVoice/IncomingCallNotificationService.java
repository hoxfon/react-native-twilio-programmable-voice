package com.hoxfon.react.RNTwilioVoice;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.twilio.voice.CallInvite;

import static com.hoxfon.react.RNTwilioVoice.CallNotificationManager.getMainActivityClass;
import static com.hoxfon.react.RNTwilioVoice.Constants.ACTION_ACCEPT;
import static com.hoxfon.react.RNTwilioVoice.Constants.ACTION_CANCEL_CALL;
import static com.hoxfon.react.RNTwilioVoice.Constants.ACTION_INCOMING_CALL;
import static com.hoxfon.react.RNTwilioVoice.Constants.ACTION_INCOMING_CALL_NOTIFICATION;
import static com.hoxfon.react.RNTwilioVoice.Constants.ACTION_REJECT;
import static com.hoxfon.react.RNTwilioVoice.Constants.CALL_SID_KEY;
import static com.hoxfon.react.RNTwilioVoice.Constants.INCOMING_CALL_INVITE;
import static com.hoxfon.react.RNTwilioVoice.Constants.INCOMING_CALL_NOTIFICATION_ID;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.TAG;

public class IncomingCallNotificationService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();

        CallInvite callInvite = intent.getParcelableExtra(INCOMING_CALL_INVITE);
        int notificationId = intent.getIntExtra(INCOMING_CALL_NOTIFICATION_ID, 0);

        switch (action) {
            case ACTION_INCOMING_CALL:
                handleIncomingCall(callInvite, notificationId);
                break;
            case ACTION_ACCEPT:
                accept(callInvite, notificationId);
                break;
            case ACTION_REJECT:
                reject(callInvite);
                break;
            case ACTION_CANCEL_CALL:
                handleCancelledCall(intent);
                break;
            default:
                break;
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification createNotification(CallInvite callInvite, int notificationId, int channelImportance) {
        Intent intent = new Intent(this, getMainActivityClass(this));
        intent.setAction(ACTION_INCOMING_CALL_NOTIFICATION);
        intent.putExtra(INCOMING_CALL_NOTIFICATION_ID, notificationId);
        intent.putExtra(INCOMING_CALL_INVITE, callInvite);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        /*
         * Pass the notification id and call sid to use as an identifier to cancel the
         * notification later
         */
        Bundle extras = new Bundle();
        extras.putString(CALL_SID_KEY, callInvite.getCallSid());

        String contextText = callInvite.getFrom() + " is calling.";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // TODO make text configurable from app resources
            return buildNotification(contextText,
                    pendingIntent,
                    extras,
                    callInvite,
                    notificationId,
                    createChannel(channelImportance));
        } else {
            return new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_call_white_24dp)
                    .setContentTitle(getString(R.string.call_incoming))
                    .setContentText(contextText)
                    .setAutoCancel(true)
                    .setExtras(extras)
                    .setContentIntent(pendingIntent)
                    .setGroup("test_app_notification")
                    .setColor(Color.rgb(214, 10, 37))
                    .build();
        }
    }

    /**
     * Build a notification.
     *
     * @param text          the text of the notification
     * @param pendingIntent the body, pending intent for the notification
     * @param extras        extras passed with the notification
     * @return the builder
     */
    @TargetApi(Build.VERSION_CODES.O)
    private Notification buildNotification(String text,
                                           PendingIntent pendingIntent,
                                           Bundle extras,
                                           final CallInvite callInvite,
                                           int notificationId,
                                           String channelId) {
        Intent rejectIntent = new Intent(getApplicationContext(), IncomingCallNotificationService.class);
        rejectIntent.setAction(ACTION_REJECT);
        rejectIntent.putExtra(INCOMING_CALL_INVITE, callInvite);
        rejectIntent.putExtra(INCOMING_CALL_NOTIFICATION_ID, notificationId);
        PendingIntent piRejectIntent = PendingIntent.getService(getApplicationContext(), 0, rejectIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent acceptIntent = new Intent(getApplicationContext(), IncomingCallNotificationService.class);
        acceptIntent.setAction(ACTION_ACCEPT);
        acceptIntent.putExtra(INCOMING_CALL_INVITE, callInvite);
        acceptIntent.putExtra(INCOMING_CALL_NOTIFICATION_ID, notificationId);
        PendingIntent piAcceptIntent = PendingIntent.getService(getApplicationContext(), 0, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder =
                new Notification.Builder(getApplicationContext(), channelId)
                        .setSmallIcon(R.drawable.ic_call_white_24dp)
                        .setContentTitle(getString(R.string.call_incoming))
                        .setContentText(text)
                        .setCategory(Notification.CATEGORY_CALL)
                        .setExtras(extras)
                        .setAutoCancel(true)
                        .addAction(android.R.drawable.ic_menu_delete, getString(R.string.decline), piRejectIntent)
                        .addAction(android.R.drawable.ic_menu_call, getString(R.string.answer), piAcceptIntent)
                        .setFullScreenIntent(pendingIntent, true);

        return builder.build();
    }

    @TargetApi(Build.VERSION_CODES.O)
    private String createChannel(int channelImportance) {
        String channelId = Constants.VOICE_CHANNEL_HIGH_IMPORTANCE;
        if (channelImportance == NotificationManager.IMPORTANCE_LOW) {
            channelId = Constants.VOICE_CHANNEL_LOW_IMPORTANCE;
        }
        NotificationChannel callInviteChannel = new NotificationChannel(channelId,
                "Incoming calls", channelImportance);
        callInviteChannel.setLightColor(Color.GREEN);
        // TODO set sound for background incoming call
//        callInviteChannel.setSound();
        callInviteChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(callInviteChannel);

        return channelId;
    }

    private void accept(CallInvite callInvite, int notificationId) {
        endForeground();
        Intent activeCallIntent = new Intent(this, getMainActivityClass(this));
        activeCallIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activeCallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activeCallIntent.putExtra(INCOMING_CALL_INVITE, callInvite);
        activeCallIntent.putExtra(INCOMING_CALL_NOTIFICATION_ID, notificationId);
        activeCallIntent.setAction(ACTION_ACCEPT);
        this.startActivity(activeCallIntent);
    }

    private void reject(CallInvite callInvite) {
        endForeground();
        callInvite.reject(getApplicationContext());
    }

    private void handleCancelledCall(Intent intent) {
        endForeground();
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void handleIncomingCall(CallInvite callInvite, int notificationId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setCallInProgressNotification(callInvite, notificationId);
        }
        sendCallInviteToActivity(callInvite, notificationId);
    }

    private void endForeground() {
        stopForeground(true);
    }

    private void setCallInProgressNotification(CallInvite callInvite, int notificationId) {
        int importance = NotificationManager.IMPORTANCE_LOW;
        if (!isAppVisible()) {
            Log.i(TAG, "setCallInProgressNotification - app is NOT visible.");
            importance = NotificationManager.IMPORTANCE_HIGH;
        }
        this.startForeground(notificationId, createNotification(callInvite, notificationId, importance));
    }

    /*
     * Send the CallInvite to the Activity. Start the activity if it is not running already.
     */
    private void sendCallInviteToActivity(CallInvite callInvite, int notificationId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isAppVisible()) {
            return;
        }
        Intent intent = new Intent(this, getMainActivityClass(this));
        intent.setAction(ACTION_INCOMING_CALL);
        intent.putExtra(INCOMING_CALL_NOTIFICATION_ID, notificationId);
        intent.putExtra(INCOMING_CALL_INVITE, callInvite);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        this.startActivity(intent);
    }

    private boolean isAppVisible() {
        return ProcessLifecycleOwner
                .get()
                .getLifecycle()
                .getCurrentState()
                .isAtLeast(Lifecycle.State.STARTED);
    }
}