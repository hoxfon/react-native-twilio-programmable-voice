package com.hoxfon.react.RNTwilioVoice;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
//import android.media.AudioAttributes;
//import android.media.RingtoneManager;
//import android.net.Uri;
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
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.TAG;

public class IncomingCallNotificationService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();

        CallInvite callInvite = intent.getParcelableExtra(Constants.INCOMING_CALL_INVITE);
        int notificationId = intent.getIntExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, 0);

        switch (action) {
            case Constants.ACTION_INCOMING_CALL:
                handleIncomingCall(callInvite, notificationId);
                break;
            case Constants.ACTION_ACCEPT:
                accept(callInvite, notificationId);
                break;
            case Constants.ACTION_REJECT:
                reject(callInvite, notificationId);
                break;
            case Constants.ACTION_CANCEL_CALL:
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
        Context ctx = getApplicationContext();

        Intent intent = new Intent(ctx, getMainActivityClass(ctx));
        intent.setAction(Constants.ACTION_OPEN_CALL_INVITE);
        intent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId);
        intent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(ctx, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        /*
         * Pass the notification id and call sid to use as an identifier to cancel the
         * notification later
         */
        Bundle extras = new Bundle();
        extras.putString(Constants.CALL_SID_KEY, callInvite.getCallSid());

        String contentText = callInvite.getFrom() + " " + getString(R.string.call_incoming_content);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return buildNotification(contentText,
                    pendingIntent,
                    extras,
                    callInvite,
                    notificationId,
                    createChannel(channelImportance));
        } else {
            // noinspection deprecation
            return new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_call_white_24dp)
                    .setContentTitle(getString(R.string.call_incoming_title))
                    .setContentText(contentText)
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
    private Notification buildNotification(String text, PendingIntent pendingIntent, Bundle extras,
                                           final CallInvite callInvite,
                                           int notificationId,
                                           String channelId) {
        Context ctx = getApplicationContext();

        Intent rejectIntent = new Intent(ctx, IncomingCallNotificationService.class);
        rejectIntent.setAction(Constants.ACTION_REJECT);
        rejectIntent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite);
        rejectIntent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId);
        PendingIntent piRejectIntent = PendingIntent.getService(getApplicationContext(), 0, rejectIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Action rejectAction = new NotificationCompat.Action.Builder(android.R.drawable.ic_menu_delete, getString(R.string.reject), piRejectIntent).build();

        Intent acceptIntent = new Intent(ctx, IncomingCallNotificationService.class);
        acceptIntent.setAction(Constants.ACTION_ACCEPT);
        acceptIntent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite);
        acceptIntent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId);
        PendingIntent piAcceptIntent = PendingIntent.getService(getApplicationContext(), 0, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Action answerAction = new NotificationCompat.Action.Builder(android.R.drawable.ic_menu_call, getString(R.string.accept), piAcceptIntent).build();

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(ctx, channelId)
                        .setSmallIcon(R.drawable.ic_call_white_24dp)
                        .setContentTitle(getString(R.string.call_incoming_title))
                        .setContentText(text)
                        .setCategory(Notification.CATEGORY_CALL)
                        .setExtras(extras)
                        .setAutoCancel(true)
                        .addAction(rejectAction)
                        .addAction(answerAction)
                        .setFullScreenIntent(pendingIntent, true)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setContentIntent(pendingIntent);

        // build notification large icon
        Resources res = ctx.getResources();
        int largeIconResId = res.getIdentifier("ic_launcher", "mipmap", ctx.getPackageName());
        Bitmap largeIconBitmap = BitmapFactory.decodeResource(res, largeIconResId);

        if (largeIconResId != 0) {
            builder.setLargeIcon(largeIconBitmap);
        }

        return builder.build();
    }

    @TargetApi(Build.VERSION_CODES.O)
    private String createChannel(int channelImportance) {
        String channelId = Constants.VOICE_CHANNEL_HIGH_IMPORTANCE;
        if (channelImportance == NotificationManager.IMPORTANCE_LOW) {
            channelId = Constants.VOICE_CHANNEL_LOW_IMPORTANCE;
        }
        Log.d(TAG, "channel importance: %d" + channelImportance);
        NotificationChannel callInviteChannel = new NotificationChannel(channelId,
                "Incoming calls", channelImportance);
        callInviteChannel.setLightColor(Color.GREEN);

        // TODO set sound for background incoming call
//        Uri defaultRingtoneUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE);
//        AudioAttributes audioAttributes = new AudioAttributes.Builder()
//                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
//                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
//                .build();
//        callInviteChannel.setSound(defaultRingtoneUri, audioAttributes);

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
        activeCallIntent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite);
        activeCallIntent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId);
        activeCallIntent.setAction(Constants.ACTION_ACCEPT);
        this.startActivity(activeCallIntent);
    }

    private void reject(CallInvite callInvite, int notificationId) {
        endForeground();
        callInvite.reject(getApplicationContext());
    }

    private void handleCancelledCall(Intent intent) {
        SoundPoolManager.getInstance(this).stopRinging();
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
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "setCallInProgressNotification()");
        }
        int importance = NotificationManager.IMPORTANCE_LOW;
        if (!isAppVisible()) {
            if (BuildConfig.DEBUG) {
                Log.i(TAG, "setCallInProgressNotification - app is NOT visible.");
            }
            importance = NotificationManager.IMPORTANCE_HIGH;
        }
        this.startForeground(notificationId, createNotification(callInvite, notificationId, importance));
    }

    /*
     * Send the CallInvite to the Activity. Start the activity if it is not running already.
     */
    private void sendCallInviteToActivity(CallInvite callInvite, int notificationId) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "sendCallInviteToActivity()");
        }

        SoundPoolManager.getInstance(this).playRinging();

        // From Android 29 app are prevented to start an activity from the background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isAppVisible()) {
            return;
        }
        Intent intent = new Intent(this, getMainActivityClass(this));
        intent.setAction(Constants.ACTION_INCOMING_CALL);
        intent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId);
        intent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite);
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