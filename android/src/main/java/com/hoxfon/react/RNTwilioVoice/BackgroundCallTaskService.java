package com.hoxfon.react.RNTwilioVoice;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.facebook.react.HeadlessJsTaskService;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.jstasks.HeadlessJsTaskConfig;

import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.TAG;

public class BackgroundCallTaskService extends HeadlessJsTaskService {
    @Override
    protected HeadlessJsTaskConfig getTaskConfig(Intent intent) {
        Bundle extras = intent.getExtras();
        Log.d(TAG, "###getTaskConfig"+ extras.toString());
        if (extras != null) {
            return new HeadlessJsTaskConfig(
                    "BackgroundCallTaskService",
                    Arguments.fromBundle(extras),
                    5000, // timeout for the task
                    false // optional: defines whether or not  the task is allowed in foreground. Default is false
            );
        }
        return null;
    }
}