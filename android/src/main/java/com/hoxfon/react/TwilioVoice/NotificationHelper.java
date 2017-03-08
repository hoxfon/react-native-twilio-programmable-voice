package com.hoxfon.react.TwilioVoice;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;
import com.twilio.voice.IncomingCall;
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
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.ACTION_MISSED_CALL;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.INCOMING_CALL_MESSAGE;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.NOTIFICATION_ID;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.NOTIFICATION_TYPE;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.CALL_SID_KEY;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.INCOMING_NOTIFICATION_PREFIX;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.HANGUP_NOTIFICATION_PREFIX;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.MISSED_CALLS_GROUP;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.MISSED_CALLS_NOTIFICATION_ID;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.PREFERENCE_KEY;

public class NotificationHelper {

    private NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();

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

    private PendingIntent getActivityOpenPendingIntent(ReactApplicationContext context,
                                                       IncomingCallMessage incomingCallMessage,
                                                       int notificationId) {
        /*
         * Create a PendingIntent to specify the action when the notification is
         * selected in the notification drawer
         */
        Intent intent = new Intent(context, getMainActivityClass(context));
        intent.setAction(ACTION_INCOMING_CALL)
                .putExtra(NOTIFICATION_ID, notificationId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (incomingCallMessage != null) {
            intent.putExtra(INCOMING_CALL_MESSAGE, incomingCallMessage);
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public void createIncomingCallNotification(ReactApplicationContext context,
                                               IncomingCallMessage incomingCallMessage,
                                               Bundle bundle) {

        int notificationId = Integer.parseInt(bundle.getString("id"));

        PendingIntent pendingIntent = getActivityOpenPendingIntent(context, incomingCallMessage, notificationId);

        /*
         * Pass the notification id and call sid to use as an identifier to cancel the
         * notification later
         */
        Bundle extras = new Bundle();
        extras.putInt(NOTIFICATION_ID, notificationId);
        extras.putString(CALL_SID_KEY, incomingCallMessage.getCallSid());
        extras.putString(NOTIFICATION_TYPE, ACTION_INCOMING_CALL);

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

        // Reject action
        Intent rejectIntent = new Intent(ACTION_REJECT_CALL)
                .putExtra(INCOMING_CALL_MESSAGE, incomingCallMessage)
                .putExtra(NOTIFICATION_ID, notificationId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingRejectIntent = PendingIntent.getBroadcast(context, 1, rejectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        notification.addAction(0, "DISMISS", pendingRejectIntent);

        // Answer action
        Intent answerIntent = new Intent(ACTION_ANSWER_CALL);
        answerIntent
                .putExtra(INCOMING_CALL_MESSAGE, incomingCallMessage)
                .putExtra(NOTIFICATION_ID, notificationId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingAnswerIntent = PendingIntent.getBroadcast(context, 0, answerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        notification.addAction(R.drawable.ic_call_white_24dp, "ANSWER", pendingAnswerIntent);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(notificationId, notification.build());
        TwilioVoiceModule.callNotificationMap.put(INCOMING_NOTIFICATION_PREFIX+incomingCallMessage.getCallSid(), notificationId);
    }

    public void createMissedCallNotification(ReactApplicationContext context, IncomingCall incomingCall) {
        SharedPreferences sharedPref = context.getSharedPreferences(PREFERENCE_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor sharedPrefEditor = sharedPref.edit();

        /*
         * Create a PendingIntent to specify the action when the notification is
         * selected in the notification drawer
         */
        Intent intent = new Intent(context, getMainActivityClass(context));
        intent.setAction(ACTION_MISSED_CALL)
                .putExtra(NOTIFICATION_ID, MISSED_CALLS_NOTIFICATION_ID)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        /*
         * Pass the notification id and call sid to use as an identifier to open the notification
         */
        Bundle extras = new Bundle();
        extras.putInt(NOTIFICATION_ID, MISSED_CALLS_NOTIFICATION_ID);
        extras.putString(CALL_SID_KEY, incomingCall.getCallSid());
        extras.putString(NOTIFICATION_TYPE, ACTION_MISSED_CALL);

        /*
         * Create the notification shown in the notification drawer
         */
        NotificationCompat.Builder notification =
                new NotificationCompat.Builder(context)
                        .setGroup(MISSED_CALLS_GROUP)
                        .setGroupSummary(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                        .setSmallIcon(R.drawable.ic_call_missed_white_24dp)
                        .setContentTitle("Missed call")
                        .setContentText(incomingCall.getFrom() + " called")
                        .setAutoCancel(true)
                        .setShowWhen(true)
                        .setExtras(extras)
                        .setDeleteIntent(pendingIntent)
                        .setContentIntent(pendingIntent);

        int missedCalls = sharedPref.getInt(MISSED_CALLS_GROUP, 0);
        missedCalls++;
        if (missedCalls == 1) {
            inboxStyle = new NotificationCompat.InboxStyle();
            inboxStyle.setBigContentTitle("Missed call");
        } else {
            inboxStyle.setBigContentTitle(String.valueOf(missedCalls) + " missed calls");
        }
        inboxStyle.addLine("from: " +incomingCall.getFrom());
        sharedPrefEditor.putInt(MISSED_CALLS_GROUP, missedCalls);
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
        notificationManager.notify(MISSED_CALLS_NOTIFICATION_ID, notification.build());
    }

    public void createHangupLocalNotification(ReactApplicationContext context, String callSid, String caller) {
        Random randomNumberGenerator = new Random(System.currentTimeMillis());
        int notificationId = randomNumberGenerator.nextInt();
        Intent intent = new Intent(ACTION_HANGUP_CALL)
                .putExtra(NOTIFICATION_ID, notificationId);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        PendingIntent activityPendingIntent = getActivityOpenPendingIntent(context, null, notificationId);

        /*
         * Pass the notification id and call sid to use as an identifier to cancel the
         * notification later
         */
        Bundle extras = new Bundle();
        extras.putInt(NOTIFICATION_ID, notificationId);
        extras.putString(CALL_SID_KEY, callSid);
        extras.putString(NOTIFICATION_TYPE, ACTION_HANGUP_CALL);

        NotificationCompat.Builder notification = new NotificationCompat.Builder(context)
                .setContentTitle("Call in progress")
                .setContentText(caller)
                .setSmallIcon(R.drawable.ic_call_white_24dp)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setUsesChronometer(true)
                .setExtras(extras)
                .setContentIntent(activityPendingIntent);

        notification.addAction(0, "HUNG UP", pendingIntent);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(notificationId, notification.build());

        TwilioVoiceModule.callNotificationMap.put(HANGUP_NOTIFICATION_PREFIX+callSid, notificationId);
    }

    public void removeIncomingCallNotification(ReactApplicationContext context,
                                               IncomingCallMessage incomingCallMessage,
                                               int notificationId) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (incomingCallMessage != null && incomingCallMessage.isCancelled()) {
                /*
                 * If the incoming call message was cancelled then remove the notification by matching
                 * it with the call sid from the list of notifications in the notification drawer.
                 */
                StatusBarNotification[] activeNotifications = notificationManager.getActiveNotifications();
                for (StatusBarNotification statusBarNotification : activeNotifications) {
                    Notification notification = statusBarNotification.getNotification();
                    String notificationType = notification.extras.getString(NOTIFICATION_TYPE);
                    if (incomingCallMessage.getCallSid().equals(notification.extras.getString(CALL_SID_KEY)) &&
                            notificationType != null && notificationType.equals(ACTION_INCOMING_CALL)) {
                        notificationManager.cancel(notification.extras.getInt(NOTIFICATION_ID));
                    }
                }
            } else if (notificationId != 0) {
                notificationManager.cancel(notificationId);
            }
        } else {
            if (notificationId != 0) {
                Log.d(LOG_TAG, "cancel direct notification id "+ notificationId);
                notificationManager.cancel(notificationId);
            } else if (incomingCallMessage != null) {
                String notificationKey = INCOMING_NOTIFICATION_PREFIX+incomingCallMessage.getCallSid();
                if (TwilioVoiceModule.callNotificationMap.containsKey(notificationKey)) {
                    notificationId = TwilioVoiceModule.callNotificationMap.get(notificationKey);
                    Log.d(LOG_TAG, "cancel map notification id " + notificationId);
                    notificationManager.cancel(notificationId);
                    TwilioVoiceModule.callNotificationMap.remove(notificationKey);
                }
            }
        }
    }

    public void removeHangupNotification(ReactApplicationContext context,
                                         String callSid,
                                         int notificationId) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (!callSid.equals("")) {
                /*
                 * If the call disconnected then remove the notification by matching
                 * it with the call sid from the list of notifications in the notification drawer.
                 */
                StatusBarNotification[] activeNotifications = notificationManager.getActiveNotifications();
                for (StatusBarNotification statusBarNotification : activeNotifications) {
                    Notification notification = statusBarNotification.getNotification();
                    String notificationType = notification.extras.getString(NOTIFICATION_TYPE);
                    if (callSid.equals(notification.extras.getString(CALL_SID_KEY)) &&
                            notificationType != null && notificationType.equals(ACTION_HANGUP_CALL)) {
                        notificationManager.cancel(notification.extras.getInt(NOTIFICATION_ID));
                    }
                }
            } else if (notificationId != 0) {
                notificationManager.cancel(notificationId);
            }
        } else {
            if (notificationId != 0) {
                Log.d(LOG_TAG, "cancel direct notification id "+ notificationId);
                notificationManager.cancel(notificationId);
            } else if (!callSid.equals("")) {
                String notificationKey = HANGUP_NOTIFICATION_PREFIX +callSid;
                if (TwilioVoiceModule.callNotificationMap.containsKey(notificationKey)) {
                    notificationId = TwilioVoiceModule.callNotificationMap.get(notificationKey);
                    Log.d(LOG_TAG, "cancel map notification id " + notificationId);
                    notificationManager.cancel(notificationId);
                    TwilioVoiceModule.callNotificationMap.remove(notificationKey);
                }
            }
        }
    }
}
