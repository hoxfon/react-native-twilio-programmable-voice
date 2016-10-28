package com.hoxfon.react.TwilioVoice.gcm;

import android.app.IntentService;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import com.hoxfon.react.TwilioVoice.TwilioVoiceModule;

import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.LOG_TAG;

public class GCMRegistrationService extends IntentService {

    public GCMRegistrationService() {
        super("GCMRegistrationService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String SenderID = intent.getStringExtra("senderID");
        if (SenderID == null) {
            SenderID = getString( getResources().getIdentifier("gcm_defaultSenderId", "string", this.getPackageName()) );
        }
        try {
            InstanceID instanceID = InstanceID.getInstance(this);
            String token = instanceID.getToken(SenderID, GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
            sendGCMTokenToActivity(token);
        } catch (Exception e) {
            /*
             * If we are unable to retrieve the GCM token we notify the Activity
             * letting the user know this step failed.
             */
            Log.e(LOG_TAG, "Failed to retrieve GCM token", e);
            sendGCMTokenToActivity(null);
        }
    }

    /**
     * Send the GCM Token to the TwilioVoice.
     *
     * @param gcmToken The new token.
     */
    private void sendGCMTokenToActivity(String gcmToken) {
        Intent intent = new Intent(TwilioVoiceModule.ACTION_SET_GCM_TOKEN);
        intent.putExtra(TwilioVoiceModule.KEY_GCM_TOKEN, gcmToken);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}
