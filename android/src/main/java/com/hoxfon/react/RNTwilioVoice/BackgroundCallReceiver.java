package com.hoxfon.react.RNTwilioVoice;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.facebook.react.HeadlessJsTaskService;

import java.util.List;
//import java.util.Random;
//
//import androidx.localbroadcastmanager.content.LocalBroadcastManager;
//
//import com.facebook.react.ReactApplication;
//import com.facebook.react.ReactInstanceManager;
//import com.facebook.react.bridge.ReactApplicationContext;
//import com.facebook.react.bridge.ReactContext;
//import com.hoxfon.react.RNTwilioVoice.CallNotificationManager;
//
//import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_INCOMING_CALL;
//import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.INCOMING_CALL_NOTIFICATION_ID;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.TAG;

public class BackgroundCallReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        Log.d(TAG, "### onReceive");
        /**
         This part will be called every time network connection is changed
         e.g. Connected -> Not Connected
         **/
        if (!isAppOnForeground((context))) {
//            ReactApplicationContext ctx = new ReactApplicationContext(context);
//
//            Random randomNumberGenerator = new Random(System.currentTimeMillis());
//            final int notificationId = randomNumberGenerator.nextInt();
//            CallNotificationManager callNotificationManager = new CallNotificationManager();
//
//            int appImportance = callNotificationManager.getApplicationImportance(ctx);
//            if (BuildConfig.DEBUG) {
//                Log.d(TAG, "CONTEXT not present appImportance = " + appImportance);
//            }
//            Intent launchIntent = callNotificationManager.getLaunchIntent(
//                    ctx,
//                    notificationId,
//                    intent.getStringExtra("call_sid"),
//                    intent.getStringExtra("call_from"),
//                    intent.getStringExtra("call_to"),
//                    true,
//                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND
//            );
//            context.startActivity(launchIntent);
////            Intent callInviteIntent = new Intent(ACTION_INCOMING_CALL);
////            callInviteIntent.putExtra(INCOMING_CALL_NOTIFICATION_ID, notificationId);
////            LocalBroadcastManager.getInstance(context).sendBroadcast(callInviteIntent);
////            callNotificationManager.createIncomingCallNotification(
////                    ctx,
////                    intent.getStringExtra("call_sid"),
////                    intent.getStringExtra("call_from"),
////                    notificationId,
////                    launchIntent
////            );



            /**
             We will start our service and send extra info about
             network connections
             **/
            Bundle extras = intent.getExtras();
            Intent serviceIntent = new Intent(context, BackgroundCallTaskService.class);
            serviceIntent.putExtras(extras);
            context.startService(serviceIntent);
            HeadlessJsTaskService.acquireWakeLockNow(context);
        }
    }

    private boolean isAppOnForeground(Context context) {
        /**
         We need to check if app is in foreground otherwise the app will crash.
         http://stackoverflow.com/questions/8489993/check-android-application-is-in-foreground-or-not
         **/
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcesses =
                activityManager.getRunningAppProcesses();
        if (appProcesses == null) {
            return false;
        }
        final String packageName = context.getPackageName();
        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.importance ==
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                    appProcess.processName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

}