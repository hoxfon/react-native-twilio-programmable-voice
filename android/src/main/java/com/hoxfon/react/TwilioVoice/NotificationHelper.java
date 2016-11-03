package com.hoxfon.react.TwilioVoice;


import android.app.ActivityManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;
import com.twilio.voice.IncomingCallMessage;

import java.util.List;
import java.util.Random;

import static android.content.Context.ACTIVITY_SERVICE;
import static com.facebook.react.common.ApplicationHolder.getApplication;

import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.LOG_TAG;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.ACTION_ANSWER_CALL;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.ACTION_REJECT_CALL;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.ACTION_HANGUP_CALL;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.ACTION_INCOMING_CALL;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.INCOMING_CALL_MESSAGE;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.INCOMING_CALL_NOTIFICATION_ID;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.CALL_SID_KEY;

public class NotificationHelper {

    public NotificationHelper() {}

    public int getApplicationImportance(ReactApplicationContext context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processInfos = activityManager.getRunningAppProcesses();
        if (processInfos == null) {
            return 0;
        }
        for (ActivityManager.RunningAppProcessInfo processInfo : processInfos) {
            if (processInfo.processName.equals(getApplication().getPackageName())) {
                return processInfo.importance;
            }
        }
        return 0;
    }

    public boolean isAppInForeground(ReactApplicationContext context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processInfos = activityManager.getRunningAppProcesses();
        if (processInfos == null) {
            return false;
        }
        for (ActivityManager.RunningAppProcessInfo processInfo : processInfos) {
            if (processInfo.processName.equals(getApplication().getPackageName())) {
                return processInfo.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
            }
        }
        return false;
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

    public void sendIncomingCallNotification(ReactApplicationContext context,
                                             IncomingCallMessage incomingCallMessage,
                                             Bundle bundle) {

        int notificationId = Integer.parseInt(bundle.getString("id"));

        /*
         * Create a PendingIntent to specify the action when the notification is
         * selected in the notification drawer
         */
        Intent intent = new Intent(context, getMainActivityClass(context));
        intent.setAction(ACTION_INCOMING_CALL)
                .putExtra(INCOMING_CALL_MESSAGE, incomingCallMessage)
                .putExtra(INCOMING_CALL_NOTIFICATION_ID, notificationId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_ONE_SHOT);

        /*
         * Pass the notification id and call sid to use as an identifier to cancel the
         * notification later
         */
        Bundle extras = new Bundle();
        extras.putInt(INCOMING_CALL_NOTIFICATION_ID, notificationId);
        extras.putString(CALL_SID_KEY, incomingCallMessage.getCallSid());

        /*
         * Create the notification shown in the notification drawer
         */
        NotificationCompat.Builder notification =
                new NotificationCompat.Builder(context)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setCategory(NotificationCompat.CATEGORY_CALL)
                        .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
                        .setSmallIcon(R.drawable.ic_call_white_24dp)
                        .setContentTitle("Incoming call")
                        .setContentText(incomingCallMessage.getFrom() + " is calling")
                        .setAutoCancel(true)
                        .setExtras(extras)
                        .setContentIntent(pendingIntent);

        // build notification large icon
        Resources res = context.getResources();
        int largeIconResId = res.getIdentifier("ic_launcher", "mipmap", context.getPackageName());
        Bitmap largeIconBitmap = BitmapFactory.decodeResource(res, largeIconResId);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            notification
                    .setVibrate(new long[]{0, 1000, 1000, 1000, 1000})
                    .setLights(Color.WHITE, 500, 500);
            if (largeIconResId != 0) {
                notification.setLargeIcon(largeIconBitmap);
            }
        }

        // Answer action
        Intent answerIntent = new Intent(ACTION_ANSWER_CALL);
        answerIntent
                .putExtra(INCOMING_CALL_MESSAGE, incomingCallMessage)
                .putExtra(INCOMING_CALL_NOTIFICATION_ID, notificationId)
                .setAction(ACTION_ANSWER_CALL)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingAnswerIntent = PendingIntent.getBroadcast(context, 0, answerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        notification.addAction(R.drawable.ic_call_white_24dp, "ANSWER", pendingAnswerIntent);

        // Reject action
        Intent rejectIntent = new Intent(ACTION_REJECT_CALL)
                .putExtra(INCOMING_CALL_MESSAGE, incomingCallMessage)
                .putExtra(INCOMING_CALL_NOTIFICATION_ID, notificationId)
                .setAction(ACTION_REJECT_CALL)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingRejectIntent = PendingIntent.getBroadcast(context, 1, rejectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        notification.addAction(0, "REJECT", pendingRejectIntent);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(notificationId, notification.build());
    }

    public void sendHangupLocalNotification(ReactApplicationContext context, String caller) {
        Random randomNumberGenerator = new Random(System.currentTimeMillis());
        int notificationId = randomNumberGenerator.nextInt();
        Intent intent = new Intent(ACTION_HANGUP_CALL)
                .putExtra(INCOMING_CALL_NOTIFICATION_ID, notificationId)
                .setAction(ACTION_HANGUP_CALL);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder notification = new NotificationCompat.Builder(context)
                .setContentTitle("call in progress")
                .setContentText(caller)
                .setSmallIcon(R.drawable.ic_call_white_24dp)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
//                .setContentIntent(pendingIntent)
                ;

        notification.addAction(0, "HUNG UP", pendingIntent);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(notificationId, notification.build());
    }
}
