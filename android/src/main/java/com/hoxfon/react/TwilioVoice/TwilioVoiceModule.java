package com.hoxfon.react.TwilioVoice;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.Manifest;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
//import android.media.AudioManager;

import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import com.twilio.voice.CallException;
import com.twilio.voice.CallState;
import com.twilio.voice.IncomingCall;
import com.twilio.voice.IncomingCallMessage;
import com.twilio.voice.IncomingCallMessageListener;
import com.twilio.voice.LogLevel;
import com.twilio.voice.OutgoingCall;
import com.twilio.voice.RegistrationException;
import com.twilio.voice.RegistrationListener;
import com.twilio.voice.VoiceClient;

import java.util.HashMap;
import java.util.List;

import com.hoxfon.react.TwilioVoice.gcm.GCMRegistrationService;

import static android.content.Context.ACTIVITY_SERVICE;
import static com.facebook.react.common.ApplicationHolder.getApplication;

public class TwilioVoiceModule extends ReactContextBaseJavaModule implements ActivityEventListener {

    public static String LOG_TAG = "TwilioVoice";

    private static final int MIC_PERMISSION_REQUEST_CODE = 1;
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

//    private boolean speakerPhone;
//    private AudioManager audioManager;
//    private int savedAudioMode = AudioManager.MODE_INVALID;

    private boolean isReceiverRegistered;
    private VoiceBroadcastReceiver voiceBroadcastReceiver;

    // Empty HashMap, never populated for the Quickstart
    HashMap<String, String> twiMLParams = new HashMap<>();

    private OutgoingCall activeOutgoingCall;
    private IncomingCall activeIncomingCall;

    public static final String ACTION_SET_GCM_TOKEN = "SET_GCM_TOKEN";
    public static final String INCOMING_CALL_MESSAGE = "INCOMING_CALL_MESSAGE";
    public static final String INCOMING_CALL_NOTIFICATION_ID = "INCOMING_CALL_NOTIFICATION_ID";
    public static final String ACTION_INCOMING_CALL = "INCOMING_CALL";
    public static final String ACTION_ANSWER_CALL = "ACTION_ANSWER_CALL";
    public static final String ACTION_REJECT_CALL = "ACTION_REJECT_CALL";

    public static final String KEY_GCM_TOKEN = "GCM_TOKEN";

    private NotificationManager notificationManager;
    private String gcmToken;
    private String accessToken;

    RegistrationListener registrationListener = registrationListener();
    OutgoingCall.Listener outgoingCallListener = outgoingCallListener();
    IncomingCall.Listener incomingCallListener = incomingCallListener();
    IncomingCallMessageListener incomingCallMessageListener = incomingCallMessageListener();
    IncomingCallMessageListener incomingCallMessageListenerBackground = incomingCallMessageListenerBackground();

    public TwilioVoiceModule(ReactApplicationContext reactContext) {
        super(reactContext);
        VoiceClient.setLogLevel(LogLevel.DEBUG);
        reactContext.addActivityEventListener(this);

        notificationManager = (NotificationManager) reactContext.getSystemService(Context.NOTIFICATION_SERVICE);

        /*
         * Setup the broadcast receiver to be notified of GCM Token updates
         * or incoming call messages in this Activity.
         */
        voiceBroadcastReceiver = new VoiceBroadcastReceiver();
        registerReceiver();

//
//        /*
//         * Needed for setting/abandoning audio focus during a call
//         */
//        audioManager = (AudioManager) reactContext.getSystemService(Context.AUDIO_SERVICE);
//
//        /*
//         * Enable changing the volume using the up/down keys during a conversation
//         */
//        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        /*
         * Ensure the microphone permission is enabled
         */
        if (!checkPermissionForMicrophone()) {
            requestPermissionForMicrophone();
        } else {
            startGCMRegistration();
        }
    }

    @Override
    public String getName() {
        return LOG_TAG;
    }

    public void onNewIntent(Intent intent) {
        Log.d(LOG_TAG, "Module::onNewIntent()");
        handleIncomingCallIntent(intent);
    }

    private void startGCMRegistration() {
        if (checkPlayServices()) {
            ReactContext reactContext = getReactApplicationContext();
            Intent intent = new Intent(reactContext, GCMRegistrationService.class);
            reactContext.startService(intent);
        }
    }

    /**
     * Register the Voice broadcast receiver
     */
    private void registerReceiver() {
        if (!isReceiverRegistered) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_SET_GCM_TOKEN);
            intentFilter.addAction(ACTION_INCOMING_CALL);
            LocalBroadcastManager.getInstance(getReactApplicationContext()).registerReceiver(
                    voiceBroadcastReceiver, intentFilter);
            isReceiverRegistered = true;
        }
    }

    private IncomingCallMessageListener incomingCallMessageListener() {
        return new IncomingCallMessageListener() {
            @Override
            public void onIncomingCall(IncomingCall incomingCall) {
                Log.d(LOG_TAG, "Incoming call from " + incomingCall.getFrom());
                activeIncomingCall = incomingCall;

                WritableMap params = Arguments.createMap();
                if (activeIncomingCall != null) {
                    params.putString("call_sid",   activeIncomingCall.getCallSid());
                    params.putString("call_from",  activeIncomingCall.getFrom());
                    params.putString("call_to",    activeIncomingCall.getTo());
                    params.putString("call_state", activeIncomingCall.getState().name());
                }
                sendEvent("deviceDidReceiveIncoming", params);
            }

            @Override
            public void onIncomingCallCancelled(IncomingCall incomingCall) {
                Log.d(LOG_TAG, "Incoming call from " + incomingCall.getFrom() + " was cancelled active call "+activeIncomingCall);
                if (activeIncomingCall != null) {
                    if (incomingCall.getCallSid() == activeIncomingCall.getCallSid() &&
                        incomingCall.getState() == CallState.PENDING) {
                        activeIncomingCall = null;
                    }
                    WritableMap params = Arguments.createMap();
                    params.putString("call_sid",   activeIncomingCall.getCallSid());
                    params.putString("call_from",  activeIncomingCall.getFrom());
                    params.putString("call_to",    activeIncomingCall.getTo());
                    params.putString("call_state", activeIncomingCall.getState().name());
                    sendEvent("connectionDidDisconnect", params);
                } else {
                    sendEvent("connectionDidDisconnect", null);
                }
            }
        };
    }

    private IncomingCallMessageListener incomingCallMessageListenerBackground() {
        return new IncomingCallMessageListener() {
            @Override
            public void onIncomingCall(IncomingCall incomingCall) {
                Log.d(LOG_TAG, "Incoming call from " + incomingCall.getFrom());
                activeIncomingCall = incomingCall;
            }

            @Override
            public void onIncomingCallCancelled(IncomingCall incomingCall) {
                Log.d(LOG_TAG, "Incoming call from " + incomingCall.getFrom() + " was cancelled active call "+activeIncomingCall);
                if (activeIncomingCall != null) {
                    if (incomingCall.getCallSid() == activeIncomingCall.getCallSid() &&
                            incomingCall.getState() == CallState.PENDING) {
                        activeIncomingCall = null;
                    }
                }
            }
        };
    }

    private RegistrationListener registrationListener() {
        return new RegistrationListener() {
            @Override
            public void onRegistered(String accessToken, String gcmToken) {
                Log.d(LOG_TAG, "Successfully registered. Access token "+accessToken+" gcm "+gcmToken);
            }

            @Override
            public void onError(RegistrationException error, String accessToken, String gcmToken) {
                Log.e(LOG_TAG, String.format("Error: %d, %s", error.getErrorCode(), error.getMessage()));
            }
        };
    }

    private OutgoingCall.Listener outgoingCallListener() {
        return new OutgoingCall.Listener() {
            @Override
            public void onConnected(OutgoingCall outgoingCall) {
                Log.d(LOG_TAG, "Connected");
                WritableMap params = Arguments.createMap();
                if (outgoingCall != null) {
                    params.putString("call_sid",   outgoingCall.getCallSid());
                    params.putString("call_state", outgoingCall.getState().name());
                }
                sendEvent("connectionDidConnect", params);
            }

            @Override
            public void onDisconnected(OutgoingCall outgoingCall) {
                Log.d(LOG_TAG, "Disconnect");
                WritableMap params = Arguments.createMap();
                if (activeIncomingCall != null) {
                    params.putString("call_sid",   outgoingCall.getCallSid());
                    params.putString("call_state", outgoingCall.getState().name());
                }
                sendEvent("connectionDidDisconnect", params);
            }

            @Override
            public void onDisconnected(OutgoingCall outgoingCall, CallException error) {
                Log.e(LOG_TAG, String.format("Error: %d, %s", error.getErrorCode(), error.getMessage()));
                WritableMap params = Arguments.createMap();
                if (activeIncomingCall != null) {
                    params.putString("call_sid",   outgoingCall.getCallSid());
                    params.putString("call_state", outgoingCall.getState().name());
                    params.putString("err", error.getMessage());
                }
                sendEvent("connectionDidDisconnect", params);
            }
        };
    }

    /**
     * Listen to each call that is answered
     * @return
     */
    private IncomingCall.Listener incomingCallListener() {
        return new IncomingCall.Listener() {
            @Override
            public void onConnected(IncomingCall incomingCall) {
                Log.d(LOG_TAG, "Connected");
                WritableMap params = Arguments.createMap();
                if (incomingCall != null) {
                    params.putString("call_sid",   incomingCall.getCallSid());
                    params.putString("call_from",  incomingCall.getFrom());
                    params.putString("call_to",    incomingCall.getTo());
                    params.putString("call_state", incomingCall.getState().name());
                }
                sendEvent("connectionDidConnect", params);
            }

            @Override
            public void onDisconnected(IncomingCall incomingCall) {
                Log.d(LOG_TAG, "Disconnected");
                WritableMap params = Arguments.createMap();
                if (incomingCall != null) {
                    params.putString("call_sid",   incomingCall.getCallSid());
                    params.putString("call_from",  incomingCall.getFrom());
                    params.putString("call_to",    incomingCall.getTo());
                    params.putString("call_state", incomingCall.getState().name());
                }
                sendEvent("connectionDidDisconnect", params);
            }

            @Override
            public void onDisconnected(IncomingCall incomingCall, CallException error) {
                Log.e(LOG_TAG, String.format("Error: %d, %s", error.getErrorCode(), error.getMessage()));
                WritableMap params = Arguments.createMap();
                if (incomingCall != null) {
                    params.putString("call_sid",   incomingCall.getCallSid());
                    params.putString("call_from",  incomingCall.getFrom());
                    params.putString("call_to",    incomingCall.getTo());
                    params.putString("call_state", incomingCall.getState().name());
                    params.putString("err", error.getMessage());
                }
                sendEvent("connectionDidDisconnect", params);
            }
        };
    }

    // removed @Override temporarily just to get it working on different versions of RN
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        onActivityResult(requestCode, resultCode, data);
        Log.d(LOG_TAG, "onActivityResult");
    }

    // removed @Override temporarily just to get it working on different versions of RN
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Ignored, required to implement ActivityEventListener for RN 0.33
        Log.d(LOG_TAG, "onActivityResult");
    }

    private void handleIncomingCallIntent(Intent intent) {
        Log.d(LOG_TAG, "handleIncomingCallIntent "+intent);
        if (intent != null && intent.getAction() != null && intent.getAction() == TwilioVoiceModule.ACTION_INCOMING_CALL) {
            IncomingCallMessage incomingCallMessage = intent.getParcelableExtra(INCOMING_CALL_MESSAGE);
            VoiceClient.handleIncomingCallMessage(getReactApplicationContext(), incomingCallMessage, incomingCallMessageListener);
        }
    }

    private class VoiceBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_SET_GCM_TOKEN)) {
                String gcmToken = intent.getStringExtra(KEY_GCM_TOKEN);
                Log.d(LOG_TAG, "GCM Token: " + gcmToken);
                TwilioVoiceModule.this.gcmToken = gcmToken;
                if (gcmToken == null) {
                    WritableMap params = Arguments.createMap();
                    params.putString("err", "Failed to get GCM Token. Unable to receive calls");
                    sendEvent("deviceNotReady", params);
                }
            } else if (action.equals(ACTION_INCOMING_CALL)) {
                Log.d(LOG_TAG, "incoming call n_id: "+intent.getIntExtra(TwilioVoiceModule.INCOMING_CALL_NOTIFICATION_ID, 0));
                Log.d(LOG_TAG, "incoming call intent: "+intent);
                int appImportance = getApplicationImportance();

                /*
                 * if the app is not running launch it
                 */
                if (appImportance == RunningAppProcessInfo.IMPORTANCE_BACKGROUND) {
                    Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
                    if (launchIntent != null) {
                        context.startActivity(launchIntent);
                        /*
                         * Remove the notification from the notification drawer
                         */
//                        notificationManager.cancel(intent.getIntExtra(TwilioVoiceModule.INCOMING_CALL_NOTIFICATION_ID, 0));
                    }
                    /*
                     * Handle the incoming call message
                     */
                    VoiceClient.handleIncomingCallMessage(
                            getReactApplicationContext(),
                            (IncomingCallMessage) intent.getParcelableExtra(INCOMING_CALL_MESSAGE),
                            incomingCallMessageListenerBackground
                    );
                // if (appImportance == RunningAppProcessInfo.IMPORTANCE_SERVICE || appImportance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
                } else if (appImportance == RunningAppProcessInfo.IMPORTANCE_SERVICE) {
                    Intent foregroundIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
                    if (foregroundIntent != null) {
                        foregroundIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        foregroundIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(foregroundIntent);
                        /*
                         * Remove the notification from the notification drawer
                         */
//                        notificationManager.cancel(intent.getIntExtra(TwilioVoiceModule.INCOMING_CALL_NOTIFICATION_ID, 0));
                    }
                    /*
                     * Handle the incoming call message
                     */
                    VoiceClient.handleIncomingCallMessage(
                            getReactApplicationContext(),
                            (IncomingCallMessage) intent.getParcelableExtra(INCOMING_CALL_MESSAGE),
                            incomingCallMessageListener
                    );
                } else {
                    /*
                     * Remove the notification from the notification drawer
                     */
//                    notificationManager.cancel(intent.getIntExtra(TwilioVoiceModule.INCOMING_CALL_NOTIFICATION_ID, 0));
                    /*
                     * Handle the incoming call message
                     */
                    VoiceClient.handleIncomingCallMessage(
                            getReactApplicationContext(),
                            (IncomingCallMessage) intent.getParcelableExtra(INCOMING_CALL_MESSAGE),
                            incomingCallMessageListener
                    );
                }
            } else if (action.equals(ACTION_ANSWER_CALL)) {
                Log.d(LOG_TAG, "ANSWER tapped");
                accept();
            } else if (action.equals(ACTION_REJECT_CALL)) {
                Log.d(LOG_TAG, "Reject tapped");
                reject();
            }
        }
    }

    private int getApplicationImportance() {
        ActivityManager activityManager = (ActivityManager) getReactApplicationContext().getSystemService(ACTIVITY_SERVICE);
        List<RunningAppProcessInfo> processInfos = activityManager.getRunningAppProcesses();
        if (processInfos == null) {
            return 0;
        }
        for (RunningAppProcessInfo processInfo : processInfos) {
            if (processInfo.processName.equals(getApplication().getPackageName())) {
                return processInfo.importance;
            }
        }
        return 0;
    }

    @ReactMethod
    public void initWithAccessToken(final String accessToken) {
        if (accessToken != "") {
            Log.d(LOG_TAG, "Access token: " + accessToken);
            TwilioVoiceModule.this.accessToken = accessToken;
            if (gcmToken != null) {
                register();
            }
        }
    }

    @ReactMethod
    public void requestPermissions(String senderID) {
        ReactContext reactContext = getReactApplicationContext();

        Intent GCMService = new Intent(reactContext, GCMRegistrationService.class);

        GCMService.putExtra("senderID", senderID);
        reactContext.startService(GCMService);
    }

    @ReactMethod
    public void accept() {
        if (activeIncomingCall != null){
            activeIncomingCall.accept(incomingCallListener);
        } else {
            sendEvent("connectionDidDisconnect", null);
        }
    }

    @ReactMethod
    public void reject() {
        if (activeIncomingCall != null){
            activeIncomingCall.reject();
        } else {
            sendEvent("connectionDidDisconnect", null);
        }
    }

    @ReactMethod
    public void ignore() {
        if (activeIncomingCall != null){
            activeIncomingCall.ignore();
        } else {
            sendEvent("connectionDidDisconnect", null);
        }
    }

    @ReactMethod
    public void connect() {
        activeOutgoingCall = VoiceClient.call(getReactApplicationContext(), accessToken, twiMLParams, outgoingCallListener);
    }

    @ReactMethod
    public void disconnect() {
        disconnectCall();
    }

    @ReactMethod
    public void setMuted(Boolean muteValue) {
        if (activeOutgoingCall != null) {
            activeOutgoingCall.mute(muteValue);
        } else if (activeIncomingCall != null) {
            activeOutgoingCall.mute(muteValue);
        }
    }

    @ReactMethod
    public void sendDigits(String digits) {
        if (activeOutgoingCall != null) {
            activeOutgoingCall.sendDigits(digits);
        } else if (activeIncomingCall != null) {
            activeIncomingCall.sendDigits(digits);
        }
    }

    @ReactMethod
    public void getIncomingCall(Promise promise) {
        if (activeIncomingCall != null) {
            WritableMap params = Arguments.createMap();
            params.putString("call_sid",   activeIncomingCall.getCallSid());
            params.putString("call_from",  activeIncomingCall.getFrom());
            params.putString("call_to",    activeIncomingCall.getTo());
            params.putString("call_state", activeIncomingCall.getState().name());
            promise.resolve(params);
            return;
        }
        promise.resolve(null);
    }

//    @ReactMethod
//    public void speakerPhoneToggle() {
//        toggleSpeakerPhone();
//    }

    private void sendEvent(String eventName, @Nullable WritableMap params) {
        Log.d(LOG_TAG, "sendEvent "+eventName+" params "+params);
        if (getReactApplicationContext().hasActiveCatalystInstance()) {
            getReactApplicationContext()
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit(eventName, params);
            Log.d(LOG_TAG, "sent");
        } else {
            Log.d(LOG_TAG, "failed Catalyst instance not active");
        }
    }

    /*
     * Register your GCM token with Twilio to enable receiving incoming calls via GCM
     */
    private void register() {
        VoiceClient.register(getReactApplicationContext(), accessToken, gcmToken, registrationListener);
        sendEvent("deviceReady", null);
//        Log.d(LOG_TAG, "active incoming call "+activeIncomingCall);
//        if (activeIncomingCall != null) {
//            WritableMap params = Arguments.createMap();
//            params.putString("call_sid",   activeIncomingCall.getCallSid());
//            params.putString("call_from",  activeIncomingCall.getFrom());
//            params.putString("call_to",    activeIncomingCall.getTo());
//            params.putString("call_state", activeIncomingCall.getState().name());
//            sendEvent("deviceDidReceiveIncoming", params);
//        }
    }

    /*
     * Disconnect an active Call
     */
    private void disconnectCall() {
        WritableMap params = Arguments.createMap();
        if (activeOutgoingCall != null) {
            activeOutgoingCall.disconnect();
            params.putString("call_sid",   activeOutgoingCall.getCallSid());
            params.putString("call_state", activeOutgoingCall.getState().name());
            activeOutgoingCall = null;
        } else if (activeIncomingCall != null) {
            activeIncomingCall.reject();
            params.putString("call_sid",   activeIncomingCall.getCallSid());
            params.putString("call_from",  activeIncomingCall.getFrom());
            params.putString("call_to",    activeIncomingCall.getTo());
            params.putString("call_state", activeIncomingCall.getState().name());
            activeIncomingCall = null;
        }
        // check whether this should be communicated. coult it cause an infinite recursion?
        sendEvent("connectionDidDisconnect", params);
    }

//    private void toggleSpeakerPhone() {
//        speakerPhone = !speakerPhone;
//
//        setAudioFocus(speakerPhone);
//        audioManager.setSpeakerphoneOn(speakerPhone);
//
//        if(speakerPhone) {
//            // send event to JS mute
//            // speakerActionFab.setImageDrawable(ContextCompat.getDrawable(VoiceActivity.this, R.drawable.ic_volume_mute_white_24px));
//        } else {
//            // send event to JS volume down
//            // speakerActionFab.setImageDrawable(ContextCompat.getDrawable(VoiceActivity.this, R.drawable.ic_volume_down_white_24px));
//        }
//    }

//    private void setAudioFocus(boolean setFocus) {
//        if (audioManager != null) {
//            if (setFocus) {
//                savedAudioMode = audioManager.getMode();
//                // Request audio focus before making any device switch.
//                audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
//                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
//
//                /*
//                 * Start by setting MODE_IN_COMMUNICATION as default audio mode. It is
//                 * required to be in this mode when playout and/or recording starts for
//                 * best possible VoIP performance. Some devices have difficulties with speaker mode
//                 * if this is not set.
//                 */
//                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
//            } else {
//                audioManager.setMode(savedAudioMode);
//                audioManager.abandonAudioFocus(null);
//            }
//        }
//    }

    private boolean checkPermissionForMicrophone() {
        int resultMic = ContextCompat.checkSelfPermission(getReactApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (resultMic == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        return false;
    }

    private void requestPermissionForMicrophone() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(getCurrentActivity(), Manifest.permission.RECORD_AUDIO)) {
            // Snackbar.make(coordinatorLayout,
            //         "Microphone permissions needed. Please allow in your application settings.",
            //         Snackbar.LENGTH_LONG).show();
        } else {
            ActivityCompat.requestPermissions(
                    getCurrentActivity(),
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    MIC_PERMISSION_REQUEST_CODE);
        }
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(getReactApplicationContext());
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(getCurrentActivity(), resultCode, PLAY_SERVICES_RESOLUTION_REQUEST)
                        .show();
            } else {
                Log.e(LOG_TAG, "This device is not supported.");
                getCurrentActivity().finish();
            }
            return false;
        }
        return true;
    }

}
