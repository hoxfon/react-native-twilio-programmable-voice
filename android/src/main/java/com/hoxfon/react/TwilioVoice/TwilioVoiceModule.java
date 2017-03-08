package com.hoxfon.react.TwilioVoice;

import android.app.Activity;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.Manifest;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;

import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.facebook.react.bridge.JSApplicationIllegalArgumentException;
import com.facebook.react.bridge.AssertionException;
import com.facebook.react.bridge.LifecycleEventListener;
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

public class TwilioVoiceModule extends ReactContextBaseJavaModule implements ActivityEventListener, LifecycleEventListener {

    public static String LOG_TAG = "TwilioVoice";

    private static final int MIC_PERMISSION_REQUEST_CODE = 1;

    private AudioManager audioManager;
    private int savedAudioMode = AudioManager.MODE_INVALID;

    private boolean isReceiverRegistered;
    private VoiceBroadcastReceiver voiceBroadcastReceiver;

    // Empty HashMap, contains parameters for the Outbound call
    private HashMap<String, String> twiMLParams = new HashMap<>();

    private OutgoingCall activeOutgoingCall;
    private IncomingCall activeIncomingCall;

    public static final String ACTION_SET_GCM_TOKEN = "SET_GCM_TOKEN";
    public static final String INCOMING_CALL_MESSAGE = "INCOMING_CALL_MESSAGE";
    public static final String NOTIFICATION_ID = "NOTIFICATION_ID";
    public static final String NOTIFICATION_TYPE = "NOTIFICATION_TYPE";
    public static final String ACTION_INCOMING_CALL = "com.hoxfon.react.TwilioVoice.INCOMING_CALL";
    public static final String ACTION_MISSED_CALL = "com.hoxfon.react.TwilioVoice.MISSED_CALL";
    public static final String ACTION_ANSWER_CALL = "com.hoxfon.react.TwilioVoice.ANSWER_CALL";
    public static final String ACTION_REJECT_CALL = "com.hoxfon.react.TwilioVoice.REJECT_CALL";
    public static final String ACTION_HANGUP_CALL = "com.hoxfon.react.TwilioVoice.HANGUP_CALL";
    public static final String CALL_SID_KEY = "CALL_SID";
    public static final String INCOMING_NOTIFICATION_PREFIX = "Incoming_";
    public static final String HANGUP_NOTIFICATION_PREFIX = "Hangup_";
    public static final String MISSED_CALLS_GROUP = "MISSED_CALLS";
    public static final int MISSED_CALLS_NOTIFICATION_ID = 1;
    public static final String PREFERENCE_KEY = "com.hoxfon.react.TwilioVoice.PREFERENCE_FILE_KEY";

    public static final String KEY_GCM_TOKEN = "GCM_TOKEN";

    private NotificationManager notificationManager;
    private NotificationHelper notificationHelper;

    private String gcmToken;
    private String accessToken;

    private Boolean isGooglePlayServicesAvailable = false;

    private String toNumber = "";
    private String toName = "";

    public static Map<String, Integer> callNotificationMap;

    private RegistrationListener registrationListener = registrationListener();
    private OutgoingCall.Listener outgoingCallListener = outgoingCallListener();
    private IncomingCall.Listener incomingCallListener = incomingCallListener();
    private IncomingCallMessageListener incomingCallMessageListener = incomingCallMessageListener();
    private IncomingCallMessageListener incomingCallMessageListenerBackground = incomingCallMessageListenerBackground();

    public TwilioVoiceModule(ReactApplicationContext reactContext) {
        super(reactContext);
        if (BuildConfig.DEBUG) {
            VoiceClient.setLogLevel(LogLevel.DEBUG);
        } else {
            VoiceClient.setLogLevel(LogLevel.ERROR);
        }
        reactContext.addActivityEventListener(this);
        reactContext.addLifecycleEventListener(this);

        notificationHelper = new NotificationHelper();

        notificationManager = (NotificationManager) reactContext.getSystemService(Context.NOTIFICATION_SERVICE);

        /*
         * Setup the broadcast receiver to be notified of GCM Token updates
         * or incoming call messages in this Activity.
         */
        voiceBroadcastReceiver = new VoiceBroadcastReceiver();
        registerReceiver();

        registerActionReceiver();

        TwilioVoiceModule.callNotificationMap = new HashMap<>();

        /*
         * Needed for setting/abandoning audio focus during a call
         */
        audioManager = (AudioManager) reactContext.getSystemService(Context.AUDIO_SERVICE);

        isGooglePlayServicesAvailable = getPlayServicesAvailability();

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
    public void onHostResume() {
        /*
         * Enable changing the volume using the up/down keys during a conversation
         */
        getCurrentActivity().setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
    }

    @Override
    public void onHostPause() {
    }

    @Override
    public void onHostDestroy() {
    }

    @Override
    public String getName() {
        return LOG_TAG;
    }

    public void onNewIntent(Intent intent) {
        Log.d(LOG_TAG, "onNewIntent" + intent.toString());
        handleIncomingCallIntent(intent);
    }

    private void startGCMRegistration() {
        if (isGooglePlayServicesAvailable) {
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
            intentFilter.addAction(ACTION_MISSED_CALL);
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
                switch (action) {
                    case ACTION_ANSWER_CALL:
                        accept();
                        break;
                    case ACTION_REJECT_CALL:
                        reject();
                        break;
                    case ACTION_HANGUP_CALL:
                        disconnect();
                        break;
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
                if (activeIncomingCall != null && incomingCall != null) {
                    Log.d(LOG_TAG, "onIncomingCallCancelled: Incoming call from " + incomingCall.getFrom() + " was cancelled active call "+activeIncomingCall);
                    if (incomingCall.getCallSid() != null &&
                            incomingCall.getState() == CallState.PENDING &&
                            activeIncomingCall.getCallSid() != null &&
                            incomingCall.getCallSid().equals(activeIncomingCall.getCallSid())) {
                        activeIncomingCall = null;
                    }
                    WritableMap params = Arguments.createMap();
                    params.putString("call_sid",   activeIncomingCall.getCallSid());
                    params.putString("call_from",  activeIncomingCall.getFrom());
                    params.putString("call_to",    activeIncomingCall.getTo());
                    params.putString("call_state", activeIncomingCall.getState().name());
                    sendEvent("connectionDidDisconnect", params);
                    notificationHelper.createMissedCallNotification(getReactApplicationContext(), activeIncomingCall);
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
                if (activeIncomingCall != null && incomingCall != null) {
                    if (incomingCall.getCallSid() != null  &&
                            incomingCall.getState() == CallState.PENDING &&
                            activeIncomingCall.getCallSid() != null &&
                            incomingCall.getCallSid().equals(activeIncomingCall.getCallSid())) {
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
                    if (!toName.equals("")) {
                        caller = toName;
                    } else if (!toNumber.equals("")) {
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
                if (callSid != null && activeOutgoingCall != null && activeOutgoingCall.getCallSid() != null && activeOutgoingCall.getCallSid().equals(callSid)) {
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
                if (callSid != null && activeOutgoingCall != null && activeOutgoingCall.getCallSid() != null && activeOutgoingCall.getCallSid().equals(callSid)) {
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
                if (callSid != null && activeIncomingCall != null && incomingCall.getCallSid() != null && incomingCall.getCallSid().equals(callSid)) {
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
                    if (activeIncomingCall != null) {
                        activeIncomingCall = null;
                    }
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Ignored, required to implement ActivityEventListener for RN 0.33
    }

    private void handleIncomingCallIntent(Intent intent) {
        String action = intent.getAction();
        Log.d(LOG_TAG, "handleIncomingCallIntent action "+action);
        if (action != null) {
            if (action.equals(ACTION_INCOMING_CALL)) {
                IncomingCallMessage incomingCallMessage = intent.getParcelableExtra(INCOMING_CALL_MESSAGE);
                if (incomingCallMessage != null) {
                    if (incomingCallMessage.getCallSid() != null) {
                        Log.d(LOG_TAG, "handleIncomingCallIntent incomingCallMessage call_sid " + incomingCallMessage.getCallSid());
                        TwilioVoiceModule.callNotificationMap.put(HANGUP_NOTIFICATION_PREFIX + incomingCallMessage.getCallSid(),
                                intent.getIntExtra(NOTIFICATION_ID, 0));
                    }
                    Log.d(LOG_TAG, "callNotificationMap " + callNotificationMap.toString());
                    VoiceClient.handleIncomingCallMessage(getReactApplicationContext(), incomingCallMessage, incomingCallMessageListener);
                }
            } else if (action.equals(ACTION_MISSED_CALL)) {
                SharedPreferences sharedPref = getReactApplicationContext().getSharedPreferences(PREFERENCE_KEY, Context.MODE_PRIVATE);
                SharedPreferences.Editor sharedPrefEditor = sharedPref.edit();
                sharedPrefEditor.remove(MISSED_CALLS_GROUP);
                sharedPrefEditor.commit();
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
                if (!isGooglePlayServicesAvailable) {
                    WritableMap params = Arguments.createMap();
                    params.putString("err", "Failed to get GCM Token. Google Play Services is not available.");
                    sendEvent("deviceNotReady", params);
                } else if (gcmToken == null) {
                    WritableMap params = Arguments.createMap();
                    params.putString("err", "Failed to get GCM Token. Unable to receive calls.");
                    sendEvent("deviceNotReady", params);
                }
            } else if (action.equals(ACTION_INCOMING_CALL)) {
                int appImportance = notificationHelper.getApplicationImportance(getReactApplicationContext());
                IncomingCallMessage incomingCallMsg = intent.getParcelableExtra(INCOMING_CALL_MESSAGE);
                if (incomingCallMsg != null) {
                    if (incomingCallMsg.getCallSid() != null) {
                        callNotificationMap.put(INCOMING_NOTIFICATION_PREFIX + incomingCallMsg.getCallSid(),
                                intent.getIntExtra(NOTIFICATION_ID, 0));
                    }
                    if (appImportance == RunningAppProcessInfo.IMPORTANCE_BACKGROUND) {
                        VoiceClient.handleIncomingCallMessage(
                                getReactApplicationContext(),
                                incomingCallMsg,
                                incomingCallMessageListenerBackground
                        );
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
    }

    @ReactMethod
    public void initWithAccessToken(final String accessToken, Promise promise) {
        if (accessToken.equals("")) {
            promise.reject(new JSApplicationIllegalArgumentException("Invalid access token"));
            return;
        }
        if (!isGooglePlayServicesAvailable) {
            promise.reject(new AssertionException("Google Play Services not available"));
            return;
        }
        if (gcmToken == null) {
            promise.reject(new AssertionException("Push notification token not available"));
            return;
        }

        TwilioVoiceModule.this.accessToken = accessToken;
        register();
        WritableMap params = Arguments.createMap();
        params.putBoolean("initilized", true);
        promise.resolve(params);
    }

    @ReactMethod
    public void requestPermissions(String senderID) {
        ReactContext reactContext = getReactApplicationContext();

        Intent GCMService = new Intent(reactContext, GCMRegistrationService.class);

        GCMService.putExtra("senderID", senderID);
        reactContext.startService(GCMService);
    }

    private void clearIncomingNotification(IncomingCall activeIncomingCall) {
        if (activeIncomingCall != null && activeIncomingCall.getCallSid() != null) {
            // remove incoming call notification
            String notificationKey = INCOMING_NOTIFICATION_PREFIX + activeIncomingCall.getCallSid();
            int notificationId = 0;
            if (TwilioVoiceModule.callNotificationMap.containsKey(notificationKey)) {
                notificationId = TwilioVoiceModule.callNotificationMap.get(notificationKey);
            }
            notificationHelper.removeIncomingCallNotification(getReactApplicationContext(), null, notificationId);
            TwilioVoiceModule.callNotificationMap.remove(notificationKey);
        }
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
        if (accessToken == null) {
            errParams.putString("err", "Invalid access token");
            sendEvent("deviceNotReady", errParams);
            return;
        }
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
        // optional parameter that will be delivered to the server
        if (params.hasKey("CallerId")) {
            twiMLParams.put("CallerId", params.getString("CallerId"));
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
            activeIncomingCall.mute(muteValue);
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

    @ReactMethod
    public void setSpeakerPhone(Boolean value) {
        setAudioFocus(value);
        audioManager.setSpeakerphoneOn(value);
    }

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

    private void setAudioFocus(boolean setFocus) {
        if (audioManager != null) {
            if (setFocus) {
                savedAudioMode = audioManager.getMode();
                // Request audio focus before making any device switch.
                audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);

                /*
                 * Start by setting MODE_IN_COMMUNICATION as default audio mode. It is
                 * required to be in this mode when playout and/or recording starts for
                 * best possible VoIP performance. Some devices have difficulties with speaker mode
                 * if this is not set.
                 */
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            } else {
                audioManager.setMode(savedAudioMode);
                audioManager.abandonAudioFocus(null);
            }
        }
    }

    private boolean checkPermissionForMicrophone() {
        int resultMic = ContextCompat.checkSelfPermission(getReactApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (resultMic == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        return false;
    }

    private void requestPermissionForMicrophone() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(getCurrentActivity(), Manifest.permission.RECORD_AUDIO)) {
            // TODO implement this to fit react-native logic
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
     * Returns the Google Play Services APK availability.
     */
    private boolean getPlayServicesAvailability() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(getReactApplicationContext());
        if (resultCode != ConnectionResult.SUCCESS) {
            Log.e(LOG_TAG, "Google Play Services not available");
            return false;
        } else {
            return true;
        }
    }

}
