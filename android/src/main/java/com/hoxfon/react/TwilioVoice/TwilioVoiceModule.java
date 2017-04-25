package com.hoxfon.react.TwilioVoice;

import android.app.Activity;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.Manifest;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;

import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

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

import com.twilio.voice.Call;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.LogLevel;
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

    public static final String ACTION_SET_GCM_TOKEN = "SET_GCM_TOKEN";
    public static final String INCOMING_CALL_INVITE = "INCOMING_CALL_INVITE";
    public static final String NOTIFICATION_ID = "NOTIFICATION_ID";
    public static final String NOTIFICATION_TYPE = "NOTIFICATION_TYPE";

    public static final String ACTION_INCOMING_CALL = "com.hoxfon.react.TwilioVoice.INCOMING_CALL";
    public static final String ACTION_MISSED_CALL = "com.hoxfon.react.TwilioVoice.MISSED_CALL";
    public static final String ACTION_ANSWER_CALL = "com.hoxfon.react.TwilioVoice.ANSWER_CALL";
    public static final String ACTION_REJECT_CALL = "com.hoxfon.react.TwilioVoice.REJECT_CALL";
    public static final String ACTION_HANGUP_CALL = "com.hoxfon.react.TwilioVoice.HANGUP_CALL";
    public static final String ACTION_CLEAR_MISSED_CALLS_COUNT = "com.hoxfon.react.TwilioVoice.CLEAR_MISSED_CALLS_COUNT";

    public static final String CALL_SID_KEY = "CALL_SID";
    public static final String INCOMING_NOTIFICATION_PREFIX = "Incoming_";
    public static final String MISSED_CALLS_GROUP = "MISSED_CALLS";
    public static final int MISSED_CALLS_NOTIFICATION_ID = 1;
    public static final int HANGUP_NOTIFICATION_ID = 11;
    public static final int CLEAR_MISSED_CALLS_NOTIFICATION_ID = 21;


    public static final String PREFERENCE_KEY = "com.hoxfon.react.TwilioVoice.PREFERENCE_FILE_KEY";

    public static final String KEY_GCM_TOKEN = "GCM_TOKEN";

    private NotificationManager notificationManager;
    private NotificationHelper notificationHelper;

    private String gcmToken;
    private String accessToken;

    private Boolean isGooglePlayServicesAvailable = false;

    private String toNumber = "";
    private String toName = "";

    static Map<String, Integer> callNotificationMap;

    private RegistrationListener registrationListener = registrationListener();
    private Call.Listener callListener = callListener();

    private CallInvite activeCallInvite;
    private Call activeCall;

    private Ringtone ringtone;
    private KeyguardManager keyguardManager;
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;

    private Boolean callCancelledManually = false;

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

        Uri ringtoneSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        ringtone = RingtoneManager.getRingtone(reactContext, ringtoneSound);

        powerManager = (PowerManager) reactContext.getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, LOG_TAG);

        keyguardManager = (KeyguardManager) reactContext.getSystemService(Context.KEYGUARD_SERVICE);

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
        // This is called only when the App is in the foreground
        Log.d(LOG_TAG, "onNewIntent " + intent.toString());
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
        intentFilter.addAction(ACTION_CLEAR_MISSED_CALLS_COUNT);

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
                    case ACTION_CLEAR_MISSED_CALLS_COUNT:
                        SharedPreferences sharedPref = context.getSharedPreferences(PREFERENCE_KEY, Context.MODE_PRIVATE);
                        SharedPreferences.Editor sharedPrefEditor = sharedPref.edit();
                        sharedPrefEditor.putInt(MISSED_CALLS_GROUP, 0);
                        sharedPrefEditor.commit();
                }
                // Dismiss the notification when the user tap on the relative notification action
                // eventually the notification will be cleared anyway
                // but in this way there is no UI lag
                notificationManager.cancel(intent.getIntExtra(NOTIFICATION_ID, 0));
            }
        }, intentFilter);
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

    private Call.Listener callListener() {
        return new Call.Listener() {
            @Override
            public void onConnected(Call call) {
                if (wakeLock.isHeld()) {
                    wakeLock.release();
                }
                WritableMap params = Arguments.createMap();
                if (call != null) {
                    params.putString("call_sid",   call.getCallSid());
                    params.putString("call_state", call.getState().name());
                    String caller = "Show call details in the app";
                    if (!toName.equals("")) {
                        caller = toName;
                    } else if (!toNumber.equals("")) {
                        caller = toNumber;
                    }
                    activeCall = call;
                    notificationHelper.createHangupLocalNotification(getReactApplicationContext(),
                            call.getCallSid(), caller);
                }
                sendEvent("connectionDidConnect", params);
            }

            @Override
            public void onDisconnected(Call call) {
                if (wakeLock.isHeld()) {
                    wakeLock.release();
                }
                WritableMap params = Arguments.createMap();
                String callSid = "";
                if (call != null) {
                    callSid = call.getCallSid();
                    params.putString("call_sid", callSid);
                    params.putString("call_state", call.getState().name());
                }
                if (callSid != null && activeCall != null && activeCall.getCallSid() != null && activeCall.getCallSid().equals(callSid)) {
                    activeCall = null;
                }
                sendEvent("connectionDidDisconnect", params);
                notificationHelper.removeHangupNotification(getReactApplicationContext());
                toNumber = "";
                toName = "";
            }

            @Override
            public void onDisconnected(Call call, CallException error) {
                if (wakeLock.isHeld()) {
                    wakeLock.release();
                }
                Log.e(LOG_TAG, String.format("outgoingCallListener onDisconnected error: %d, %s",
                        error.getErrorCode(), error.getMessage()));
                WritableMap params = Arguments.createMap();
                String callSid = "";
                if (call != null) {
                    callSid = call.getCallSid();
                    params.putString("call_sid", callSid);
                    params.putString("call_state", call.getState().name());
                    params.putString("err", error.getMessage());
                }
                if (callSid != null && activeCall != null && activeCall.getCallSid() != null && activeCall.getCallSid().equals(callSid)) {
                    activeCall = null;
                }
                sendEvent("connectionDidDisconnect", params);
                notificationHelper.removeHangupNotification(getReactApplicationContext());
                toNumber = "";
                toName = "";
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
        if (action == null) {
            return;
        }
        if (action.equals(ACTION_INCOMING_CALL)) {
            activeCallInvite = intent.getParcelableExtra(INCOMING_CALL_INVITE);
            if (activeCallInvite == null) {
                return;
            }
            if (!activeCallInvite.isCancelled()) {
                ringtone.play();

                KeyguardManager.KeyguardLock lock = keyguardManager.newKeyguardLock(LOG_TAG);
                lock.disableKeyguard();
                wakeLock.acquire();

                if (getReactApplicationContext().getCurrentActivity() != null) {
                    Window window = getReactApplicationContext().getCurrentActivity().getWindow();
                    window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
                }
                // send a JS event ONLY if the app's importance is FOREGROUND or SERVICE
                // at startup the app would try to fetch the activeIncoming calls
                int appImportance = notificationHelper.getApplicationImportance(getReactApplicationContext());
                if (appImportance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                        appImportance == RunningAppProcessInfo.IMPORTANCE_SERVICE) {

                    WritableMap params = Arguments.createMap();
                    params.putString("call_sid", activeCallInvite.getCallSid());
                    params.putString("call_from", activeCallInvite.getFrom());
                    params.putString("call_to", activeCallInvite.getTo());
                    params.putString("call_state", activeCallInvite.getState().name());
                    sendEvent("deviceDidReceiveIncoming", params);
                }
                lock.reenableKeyguard();

            } else {
                // this block is executed when the callInvite is cancelled and:
                //   - the call is answered (activeCall != null)
                //   - the call is rejected

                ringtone.stop();

                notificationManager.cancel(intent.getIntExtra(NOTIFICATION_ID, 0));

                if (wakeLock.isHeld()) {
                    wakeLock.release();
                }

                if (activeCall != null) {
                    Log.d(LOG_TAG, "activeCallInvite was answered. Call "+ activeCall);
                    return;
                }

                if (activeCallInvite != null) {
                    Log.d(LOG_TAG, "activeCallInvite was cancelled by " + activeCallInvite.getFrom());

                    int appImportance = notificationHelper.getApplicationImportance(getReactApplicationContext());
                    if (appImportance != RunningAppProcessInfo.IMPORTANCE_BACKGROUND) {
                        WritableMap params = Arguments.createMap();
                        params.putString("call_sid",  activeCallInvite.getCallSid());
                        params.putString("call_from",  activeCallInvite.getFrom());
                        params.putString("call_to",  activeCallInvite.getTo());
                        params.putString("call_state",  activeCallInvite.getState().name());
                        sendEvent("connectionDidDisconnect", params);
                    }

                    if (!callCancelledManually) {
                        notificationHelper.createMissedCallNotification(getReactApplicationContext(), activeCallInvite);
                    }

                    callCancelledManually = false;
                }
                clearIncomingNotification(activeCallInvite);
            }
        } else if (action.equals(ACTION_MISSED_CALL)) {
            SharedPreferences sharedPref = getReactApplicationContext().getSharedPreferences(PREFERENCE_KEY, Context.MODE_PRIVATE);
            SharedPreferences.Editor sharedPrefEditor = sharedPref.edit();
            sharedPrefEditor.remove(MISSED_CALLS_GROUP);
            sharedPrefEditor.commit();
        } else {
            Log.d(LOG_TAG, "handleIncomingCallIntent unhandled action " + action);
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
                handleIncomingCallIntent(intent);
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

    private void clearIncomingNotification(CallInvite callInvite) {
        if (callInvite != null && callInvite.getCallSid() != null) {
            // remove incoming call notification
            String notificationKey = INCOMING_NOTIFICATION_PREFIX + callInvite.getCallSid();
            int notificationId = 0;
            if (TwilioVoiceModule.callNotificationMap.containsKey(notificationKey)) {
                notificationId = TwilioVoiceModule.callNotificationMap.get(notificationKey);
            }
            notificationHelper.removeIncomingCallNotification(getReactApplicationContext(), null, notificationId);
            TwilioVoiceModule.callNotificationMap.remove(notificationKey);
        }
        activeCallInvite = null;
    }

    @ReactMethod
    public void accept() {
        ringtone.stop();
        if (activeCallInvite != null){
            if (activeCallInvite.getState() == CallInvite.State.PENDING) {
                activeCallInvite.accept(getReactApplicationContext(), callListener);
                clearIncomingNotification(activeCallInvite);
            } else {
                // when the user answers a call from a notification before the react-native App
                // is completely initialised, and the first event has been skipped
                // re-send connectionDidConnect message to JS
                WritableMap params = Arguments.createMap();
                params.putString("call_sid",   activeCallInvite.getCallSid());
                params.putString("call_from",  activeCallInvite.getFrom());
                params.putString("call_to",    activeCallInvite.getTo());
                params.putString("call_state", activeCallInvite.getState().name());
                notificationHelper.createHangupLocalNotification(getReactApplicationContext(),
                        activeCallInvite.getCallSid(),
                        activeCallInvite.getFrom());
                sendEvent("connectionDidConnect", params);
            }
        } else {
            sendEvent("connectionDidDisconnect", null);
        }
    }

    @ReactMethod
    public void reject() {
        callCancelledManually = true;
        ringtone.stop();
        if (activeCallInvite != null){
            activeCallInvite.reject(getReactApplicationContext());
            clearIncomingNotification(activeCallInvite);
        } else {
            sendEvent("connectionDidDisconnect", null);
        }
    }

    @ReactMethod
    public void ignore() {
        callCancelledManually = true;
        ringtone.stop();
        if (activeCallInvite != null){
            clearIncomingNotification(activeCallInvite);
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
        activeCall = VoiceClient.call(getReactApplicationContext(), accessToken, twiMLParams, callListener);
    }

    @ReactMethod
    public void disconnect() {
        if (activeCall != null) {
            activeCall.disconnect();
            activeCall = null;
        }
    }

    @ReactMethod
    public void setMuted(Boolean muteValue) {
        if (activeCall != null) {
            activeCall.mute(muteValue);
        }
    }

    @ReactMethod
    public void sendDigits(String digits) {
        if (activeCall != null) {
            activeCall.sendDigits(digits);
        }
    }

    // TODO rename getIncomingCall() to getActiveCall()
    @ReactMethod
    public void getIncomingCall(Promise promise) {
        if (activeCall != null) {
            Log.d(LOG_TAG, "Active call found: "+activeCall);
            WritableMap params = Arguments.createMap();
            params.putString("call_sid",   activeCall.getCallSid());
            params.putString("call_from",  activeCall.getFrom());
            params.putString("call_to",    activeCall.getTo());
            params.putString("call_state", activeCall.getState().name());
            promise.resolve(params);
            return;
        }
        if (activeCallInvite != null) {
            Log.d(LOG_TAG, "Active call invite found: "+activeCallInvite);
            WritableMap params = Arguments.createMap();
            params.putString("call_sid",   activeCallInvite.getCallSid());
            params.putString("call_from",  activeCallInvite.getFrom());
            params.putString("call_to",    activeCallInvite.getTo());
            params.putString("call_state", activeCallInvite.getState().name());
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
