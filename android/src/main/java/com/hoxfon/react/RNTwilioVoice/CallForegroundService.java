package com.hoxfon.react.RNTwilioVoice;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

public class CallForegroundService extends Service {
    public static String TAG = "CallForegroundService";
    public static final int FOREGROUND_SERVICE_ID = 111;
    public static final String NOTIFICATION_KEY = "notification";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Bundle extras = intent.getExtras();
                startForeground(FOREGROUND_SERVICE_ID, (Notification)extras.getParcelable(NOTIFICATION_KEY));
                Log.d(TAG, "call foreground service started");
            }
        }
        catch (Exception ex)
        {
            Log.e(TAG, "error while starting call foreground service:\n" + ex.toString());

        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.d("RNTwilioVoice", "onDestroy");
    }
}
