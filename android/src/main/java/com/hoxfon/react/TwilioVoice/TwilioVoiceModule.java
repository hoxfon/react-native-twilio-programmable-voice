package com.hoxfon.react.TwilioVoice;

import android.app.Activity;
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

import com.facebook.react.bridge.ReadableMap;
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
import java.util.Map;

import com.hoxfon.react.TwilioVoice.gcm.GCMRegistrationService;

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
    public static final String NOTIFICATION_ID = "NOTIFICATION_ID";
    public static final String NOTIFICATION_TYPE = "NOTIFICATION_TYPE";
    public static final String ACTION_INCOMING_CALL = "com.hoxfon.react.TwilioVoice.INCOMING_CALL";
    public static final String ACTION_ANSWER_CALL = "com.hoxfon.react.TwilioVoice.ANSWER_CALL";
    public static final String ACTION_REJECT_CALL = "com.hoxfon.react.TwilioVoice.REJECT_CALL";
    public static final String ACTION_HANGUP_CALL = "com.hoxfon.react.TwilioVoice.HANGUP_CALL";
    public static final String CALL_SID_KEY = "CALL_SID";
    public static final String INCOMING_NOTIFICATION_PREFIX = "Incoming_";
    public static final String HANGUP_NOTIFICATION_PREFIX = "Hangup_";

    public static final String KEY_GCM_TOKEN = "GCM_TOKEN";

    private NotificationManager notificationManager;
    private NotificationHelper notificationHelper;

    private String gcmToken;
    private String accessToken;

    private String toNumber = "";
    private String toName = "";

    public static Map<String, Integer> callNotificationMap;

    RegistrationListener registrationListener = registrationListener();
    OutgoingCall.Listener outgoingCallListener = outgoingCallListener();
    IncomingCall.Listener incomingCallListener = incomingCallListener();
    IncomingCallMessageListener incomingCallMessageListener = incomingCallMessageListener();
    IncomingCallMessageListener incomingCallMessageListenerBackground = incomingCallMessageListenerBackground();

    public TwilioVoiceModule(ReactApplicationContext reactContext) {
        super(reactContext);
        if (BuildConfig.DEBUG) {
            VoiceClient.setLogLevel(LogLevel.DEBUG);
        } else {
            VoiceClient.setLogLevel(LogLevel.ERROR);
        }
        reactContext.addActivityEventListener(this);

        notificationHelper = new NotificationHelper();

        notificationManager = (NotificationManager) reactContext.getSystemService(Context.NOTIFICATION_SERVICE);

        /*
         * Setup the broadcast receiver to be notified of GCM Token updates
         * or incoming call messages in this Activity.
         */
        voiceBroadcastReceiver = new VoiceBroadcastReceiver();
        registerReceiver();

        registerActionReceiver();

        TwilioVoiceModule.callNotificationMap = new HashMap<String, Integer>();
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

    private void registerActionReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_ANSWER_CALL);
        intentFilter.addAction(ACTION_REJECT_CALL);
        intentFilter.addAction(ACTION_HANGUP_CALL);

        getReactApplicationContext().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(ACTION_ANSWER_CALL)) {
                    accept();
                } else if (action.equals(ACTION_REJECT_CALL)) {
                    reject();
                } else if (action.equals(ACTION_HANGUP_CALL)) {
                    disconnect();
                }
                // Dismiss the notification when the user tap on the relative notification action
                // eventually the notification will be cleared anyway
                // but in this way there is no UI lag
                notificationManager.cancel(intent.getIntExtra(NOTIFICATION_ID, 0));
            }
        }, intentFilter);
    }

    private IncomingCallMessageListener incomingCallMessageListener() {
        return new IncomingCallMessageListener() {
            @Override
            public void onIncomingCall(IncomingCall incomingCall) {
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

    // this listener is used when the app is in the background
    // doesn't fire a JS event since the app has not been initialised to received it yet
    // To workaround this problem the app would get the activeIncoming calls at startup
    private IncomingCallMessageListener incomingCallMessageListenerBackground() {
        return new IncomingCallMessageListener() {
            @Override
            public void onIncomingCall(IncomingCall incomingCall) {
                activeIncomingCall = incomingCall;
            }

            @Override
            public void onIncomingCallCancelled(IncomingCall incomingCall) {
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
                sendEvent("deviceReady", null);
            }

            @Override
            public void onError(RegistrationException error, String accessToken, String gcmToken) {
                Log.e(LOG_TAG, String.format("Error: %d, %s", error.getErrorCode(), error.getMessage()));
                WritableMap params = Arguments.createMap();
                params.putString("err", error.getMessage());
                sendEvent("deviceNotReady", params);
            }
        };
    }

    private OutgoingCall.Listener outgoingCallListener() {
        return new OutgoingCall.Listener() {
            @Override
            public void onConnected(OutgoingCall outgoingCall) {
                WritableMap params = Arguments.createMap();
                if (outgoingCall != null) {
                    params.putString("call_sid",   outgoingCall.getCallSid());
                    params.putString("call_state", outgoingCall.getState().name());
                    String caller = "Show call details in the app";
                    if (toName != "") {
                        caller = toName;
                    } else if (toNumber != "") {
                        caller = toNumber;
                    }
                    notificationHelper.createHangupLocalNotification(getReactApplicationContext(),
                            outgoingCall.getCallSid(), caller);
                }
                sendEvent("connectionDidConnect", params);
            }

            @Override
            public void onDisconnected(OutgoingCall outgoingCall) {
                WritableMap params = Arguments.createMap();
                String callSid = "";
                if (outgoingCall != null) {
                    callSid = outgoingCall.getCallSid();
                    params.putString("call_sid", callSid);
                    params.putString("call_state", outgoingCall.getState().name());
                }
                if (activeOutgoingCall != null && activeOutgoingCall.getCallSid() == callSid) {
                    activeOutgoingCall = null;
                }
                sendEvent("connectionDidDisconnect", params);
                notificationHelper.removeHangupNotification(getReactApplicationContext(), callSid, 0);
                toNumber = "";
                toName = "";
            }

            @Override
            public void onDisconnected(OutgoingCall outgoingCall, CallException error) {
                Log.e(LOG_TAG, String.format("outgoingCallListener onDisconnected error: %d, %s",
                        error.getErrorCode(), error.getMessage()));
                WritableMap params = Arguments.createMap();
                String callSid = "";
                if (outgoingCall != null) {
                    callSid = outgoingCall.getCallSid();
                    params.putString("call_sid", callSid);
                    params.putString("call_state", outgoingCall.getState().name());
                    params.putString("err", error.getMessage());
                }
                if (activeOutgoingCall != null && activeOutgoingCall.getCallSid() == callSid) {
                    activeOutgoingCall = null;
                }
                sendEvent("connectionDidDisconnect", params);
                notificationHelper.removeHangupNotification(getReactApplicationContext(), callSid, 0);
                toNumber = "";
                toName = "";
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
                WritableMap params = Arguments.createMap();
                if (incomingCall != null) {
                    params.putString("call_sid",   incomingCall.getCallSid());
                    params.putString("call_from",  incomingCall.getFrom());
                    params.putString("call_to",    incomingCall.getTo());
                    params.putString("call_state", incomingCall.getState().name());
                    notificationHelper.createHangupLocalNotification(getReactApplicationContext(),
                            incomingCall.getCallSid(),
                            incomingCall.getFrom());
                }
                sendEvent("connectionDidConnect", params);
            }

            @Override
            public void onDisconnected(IncomingCall incomingCall) {
                WritableMap params = Arguments.createMap();
                String callSid = "";
                if (incomingCall != null) {
                    callSid = incomingCall.getCallSid();
                    params.putString("call_sid",   callSid);
                    params.putString("call_from",  incomingCall.getFrom());
                    params.putString("call_to",    incomingCall.getTo());
                    params.putString("call_state", incomingCall.getState().name());
                }
                if (activeIncomingCall != null && incomingCall.getCallSid() == callSid) {
                    activeIncomingCall = null;
                }
                sendEvent("connectionDidDisconnect", params);
                notificationHelper.removeHangupNotification(getReactApplicationContext(), callSid, 0);
            }

            @Override
            public void onDisconnected(IncomingCall incomingCall, CallException error) {
                Log.e(LOG_TAG, String.format("incomingCallListener onDisconnected error: %d, %s",
                        error.getErrorCode(), error.getMessage()));
                WritableMap params = Arguments.createMap();
                String callSid = "";
                if (incomingCall != null) {
                    callSid = incomingCall.getCallSid();
                    params.putString("call_sid",   callSid);
                    params.putString("call_from",  incomingCall.getFrom());
                    params.putString("call_to",    incomingCall.getTo());
                    params.putString("call_state", incomingCall.getState().name());
                    params.putString("err", error.getMessage());
                }
                if (activeIncomingCall != null && incomingCall.getCallSid() == callSid) {
                    activeIncomingCall = null;
                }
                sendEvent("connectionDidDisconnect", params);
                notificationHelper.removeHangupNotification(getReactApplicationContext(), callSid, 0);
            }
        };
    }

    // removed @Override temporarily just to get it working on different versions of RN
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        onActivityResult(requestCode, resultCode, data);
    }

    // removed @Override temporarily just to get it working on different versions of RN
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Ignored, required to implement ActivityEventListener for RN 0.33
    }

    private void handleIncomingCallIntent(Intent intent) {
        String action = intent.getAction();
        Log.d(LOG_TAG, "handleIncomingCallIntent action "+action);
        if (intent != null && action != null) {
            if (action == ACTION_INCOMING_CALL) {
                IncomingCallMessage incomingCallMessage = intent.getParcelableExtra(INCOMING_CALL_MESSAGE);
                TwilioVoiceModule.callNotificationMap.put(HANGUP_NOTIFICATION_PREFIX+incomingCallMessage.getCallSid(),
                        intent.getIntExtra(NOTIFICATION_ID, 0));
                Log.d(LOG_TAG, "callNotificationMap "+ callNotificationMap.toString());
                VoiceClient.handleIncomingCallMessage(getReactApplicationContext(), incomingCallMessage, incomingCallMessageListener);
            }
        }
    }

    private class VoiceBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_SET_GCM_TOKEN)) {
                String gcmToken = intent.getStringExtra(KEY_GCM_TOKEN);
                TwilioVoiceModule.this.gcmToken = gcmToken;
                if (gcmToken == null) {
                    WritableMap params = Arguments.createMap();
                    params.putString("err", "Failed to get GCM Token. Unable to receive calls.");
                    sendEvent("deviceNotReady", params);
                }
            } else if (action.equals(ACTION_INCOMING_CALL)) {
                int appImportance = notificationHelper.getApplicationImportance(getReactApplicationContext());
                IncomingCallMessage incomingCallMsg = intent.getParcelableExtra(INCOMING_CALL_MESSAGE);
                callNotificationMap.put(INCOMING_NOTIFICATION_PREFIX +incomingCallMsg.getCallSid(),
                        intent.getIntExtra(NOTIFICATION_ID, 0));
                if (appImportance == RunningAppProcessInfo.IMPORTANCE_BACKGROUND) {
                    VoiceClient.handleIncomingCallMessage(
                            getReactApplicationContext(),
                            incomingCallMsg,
                            incomingCallMessageListenerBackground
                    );
                // if (appImportance == RunningAppProcessInfo.IMPORTANCE_SERVICE || appImportance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
                } else {
                    VoiceClient.handleIncomingCallMessage(
                            getReactApplicationContext(),
                            incomingCallMsg,
                            incomingCallMessageListener
                    );
                }
            }
        }
    }

    @ReactMethod
    public void initWithAccessToken(final String accessToken) {
        if (accessToken != "") {
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

    private void clearIncomingNotification(IncomingCall activeIncomingCall) {
        // remove incoming call notification
        String notificationKey = INCOMING_NOTIFICATION_PREFIX +activeIncomingCall.getCallSid();
        int notificationId = 0;
        if (TwilioVoiceModule.callNotificationMap.containsKey(notificationKey)) {
            notificationId = TwilioVoiceModule.callNotificationMap.get(notificationKey);
        }
        notificationHelper.removeIncomingCallNotification(getReactApplicationContext(), null, notificationId);
        TwilioVoiceModule.callNotificationMap.remove(notificationKey);
    }

    @ReactMethod
    public void accept() {
        if (activeIncomingCall != null){
            activeIncomingCall.accept(incomingCallListener);
            clearIncomingNotification(activeIncomingCall);
        } else {
            sendEvent("connectionDidDisconnect", null);
        }
    }

    @ReactMethod
    public void reject() {
        if (activeIncomingCall != null){
            activeIncomingCall.reject();
            clearIncomingNotification(activeIncomingCall);
        } else {
            sendEvent("connectionDidDisconnect", null);
        }
    }

    @ReactMethod
    public void ignore() {
        if (activeIncomingCall != null){
            activeIncomingCall.ignore();
            clearIncomingNotification(activeIncomingCall);
        } else {
            sendEvent("connectionDidDisconnect", null);
        }
    }

    @ReactMethod
    public void connect(ReadableMap params) {
        Log.d(LOG_TAG, "connect params: "+params);
        WritableMap errParams = Arguments.createMap();
        if (params == null) {
            errParams.putString("err", "Invalid parameters");
            sendEvent("connectionDidDisconnect", errParams);
            return;
        } else if (!params.hasKey("To")) {
            errParams.putString("err", "Invalid To parameter");
            sendEvent("connectionDidDisconnect", errParams);
            return;
        }
        toNumber = params.getString("To");
        if (params.hasKey("ToName")) {
            toName = params.getString("ToName");
        }
        twiMLParams.put("To", params.getString("To"));
        activeOutgoingCall = VoiceClient.call(getReactApplicationContext(), accessToken, twiMLParams, outgoingCallListener);
    }

    @ReactMethod
    public void disconnect() {
        if (activeOutgoingCall != null) {
            activeOutgoingCall.disconnect();
        } else if (activeIncomingCall != null) {
            activeIncomingCall.disconnect();
        }
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
        Log.d(LOG_TAG, "client checks for active incoming calls. Active incoming call: "+activeIncomingCall);
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
        } else {
            Log.d(LOG_TAG, "failed Catalyst instance not active");
        }
    }

    /*
     * Register your GCM token with Twilio to enable receiving incoming calls via GCM
     */
    private void register() {
        VoiceClient.register(getReactApplicationContext(), accessToken, gcmToken, registrationListener);
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
