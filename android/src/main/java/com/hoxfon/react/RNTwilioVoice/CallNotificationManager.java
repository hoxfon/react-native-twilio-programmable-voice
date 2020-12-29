package com.hoxfon.react.RNTwilioVoice;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.facebook.react.bridge.ReactApplicationContext;

import java.util.List;

import static android.content.Context.ACTIVITY_SERVICE;

import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.TAG;

public class CallNotificationManager {

    private static final String VOICE_CHANNEL = "default";

    private NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();

    public CallNotificationManager() {}

    public int getApplicationImportance(ReactApplicationContext context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        if (activityManager == null) {
            return 0;
        }
        List<ActivityManager.RunningAppProcessInfo> processInfos = activityManager.getRunningAppProcesses();
        if (processInfos == null) {
            return 0;
        }

        for (ActivityManager.RunningAppProcessInfo processInfo : processInfos) {
            if (processInfo.processName.equals(context.getApplicationInfo().packageName)) {
                return processInfo.importance;
            }
        }
        return 0;
    }

    public static Class getMainActivityClass(Context context) {
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

    public void createMissedCallNotification(ReactApplicationContext context, String callSid, String callFrom) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "createMissedCallNotification()");
        }
        SharedPreferences sharedPref = context.getSharedPreferences(Constants.PREFERENCE_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor sharedPrefEditor = sharedPref.edit();

        Intent intent = new Intent(context, getMainActivityClass(context));
        intent.setAction(Constants.ACTION_MISSED_CALL)
                .putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, Constants.MISSED_CALLS_NOTIFICATION_ID)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );

        PendingIntent clearMissedCallsCountPendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                new Intent(Constants.ACTION_CLEAR_MISSED_CALLS_COUNT)
                        .putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, Constants.CLEAR_MISSED_CALLS_NOTIFICATION_ID),
                0
        );
        /*
         * Pass the notification id and call sid to use as an identifier to open the notification
         */
        Bundle extras = new Bundle();
        extras.putInt(Constants.INCOMING_CALL_NOTIFICATION_ID, Constants.MISSED_CALLS_NOTIFICATION_ID);
        extras.putString(Constants.CALL_SID_KEY, callSid);

        /*
         * Create the notification shown in the notification drawer
         */
        String title = context.getString(R.string.call_missed_title);
        NotificationCompat.Builder notification =
                new NotificationCompat.Builder(context, VOICE_CHANNEL)
                        .setGroup(Constants.MISSED_CALLS_GROUP)
                        .setGroupSummary(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                        .setSmallIcon(R.drawable.ic_call_missed_white_24dp)
                        .setContentTitle(title)
                        .setContentText(callFrom + context.getString(R.string.call_missed_from))
                        .setAutoCancel(true)
                        .setShowWhen(true)
                        .setExtras(extras)
                        .setDeleteIntent(clearMissedCallsCountPendingIntent)
                        .setContentIntent(pendingIntent);

        int missedCalls = sharedPref.getInt(Constants.MISSED_CALLS_GROUP, 0);
        missedCalls++;
        if (missedCalls == 1) {
            inboxStyle = new NotificationCompat.InboxStyle();
            inboxStyle.setBigContentTitle(title);
        } else {
            inboxStyle.setBigContentTitle(String.valueOf(missedCalls) + " " + context.getString(R.string.call_missed_title_plural));
        }
        inboxStyle.addLine(context.getString(R.string.call_missed_more) + " " + callFrom);
        sharedPrefEditor.putInt(Constants.MISSED_CALLS_GROUP, missedCalls);
        sharedPrefEditor.commit();

        notification.setStyle(inboxStyle);

        // build notification large icon
        Resources res = context.getResources();
        int largeIconResId = res.getIdentifier("ic_launcher", "mipmap", context.getPackageName());
        Bitmap largeIconBitmap = BitmapFactory.decodeResource(res, largeIconResId);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && largeIconResId != 0) {
            notification.setLargeIcon(largeIconBitmap);
        }

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(Constants.MISSED_CALLS_NOTIFICATION_ID, notification.build());
    }

    public void createHangupNotification(ReactApplicationContext context, String callSid, String caller) {
        Intent intent = new Intent(context, getMainActivityClass(context));
        intent.setAction(Constants.ACTION_OPEN_CALL_IN_PROGRESS)
                .putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, Constants.HANGUP_NOTIFICATION_ID)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );

        PendingIntent hangupPendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                new Intent(Constants.ACTION_HANGUP_CALL)
                        .putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, Constants.HANGUP_NOTIFICATION_ID),
                PendingIntent.FLAG_UPDATE_CURRENT
        );

        Bundle extras = new Bundle();
        extras.putInt(Constants.INCOMING_CALL_NOTIFICATION_ID, Constants.HANGUP_NOTIFICATION_ID);
        extras.putString(Constants.CALL_SID_KEY, callSid);

        Notification notification;
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String title = context.getString(R.string.call_in_progress);
        String actionText = context.getString(R.string.hangup);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = new NotificationCompat.Builder(context, createChannel(title, notificationManager))
                    .setContentTitle(title)
                    .setContentText(caller)
                    .setSmallIcon(R.drawable.ic_call_white_24dp)
                    .setCategory(Notification.CATEGORY_CALL)
                    .setExtras(extras)
                    .setOngoing(true)
                    .setUsesChronometer(true)
                    .setFullScreenIntent(pendingIntent, true)
                    .addAction(0, actionText, hangupPendingIntent)
                    .build();
        } else {
            // noinspection deprecation
            notification = new NotificationCompat.Builder(context)
                    .setContentTitle(title)
                    .setContentText(caller)
                    .setSmallIcon(R.drawable.ic_call_white_24dp)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setOngoing(true)
                    .setUsesChronometer(true)
                    .setExtras(extras)
                    .setContentIntent(pendingIntent)
                    .addAction(0, actionText, hangupPendingIntent)
                    .build();
        }
        notificationManager.notify(Constants.HANGUP_NOTIFICATION_ID, notification);
    }

    @TargetApi(Build.VERSION_CODES.O)
    private String createChannel(String channelName, NotificationManager notificationManager) {
        String channelId = VOICE_CHANNEL;
        NotificationChannel channel = new NotificationChannel(channelId,
                channelName, NotificationManager.IMPORTANCE_DEFAULT);
        channel.setLightColor(Color.GREEN);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        notificationManager.createNotificationChannel(channel);
        return channelId;
    }

    public void removeHangupNotification(ReactApplicationContext context) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(Constants.HANGUP_NOTIFICATION_ID);
    }
}
