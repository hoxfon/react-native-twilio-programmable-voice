package com.hoxfon.react.RNTwilioVoice;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import com.facebook.react.bridge.JSApplicationIllegalArgumentException;
import com.facebook.react.bridge.AssertionException;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReadableMap;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableMap;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import com.google.firebase.iid.FirebaseInstanceId;
import com.twilio.voice.AcceptOptions;
import com.twilio.voice.Call;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.CancelledCallInvite;
import com.twilio.voice.ConnectOptions;
import com.twilio.voice.LogLevel;
import com.twilio.voice.RegistrationException;
import com.twilio.voice.RegistrationListener;
import com.twilio.voice.Voice;

import java.util.HashMap;
import java.util.Map;

import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_CONNECTION_DID_CONNECT;
import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_CONNECTION_DID_DISCONNECT;
import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_DEVICE_DID_RECEIVE_INCOMING;
import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_DEVICE_NOT_READY;
import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_DEVICE_READY;
import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_CALL_STATE_RINGING;
import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_CALL_INVITE_CANCELLED;
import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_CONNECTION_IS_RECONNECTING;
import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_CONNECTION_DID_RECONNECT;

public class TwilioVoiceModule extends ReactContextBaseJavaModule implements ActivityEventListener, LifecycleEventListener {

    public static String TAG = "RNTwilioVoice";

    private static final int MIC_PERMISSION_REQUEST_CODE = 1;

    private AudioManager audioManager;
    private int savedAudioMode = AudioManager.MODE_NORMAL;

    private boolean isReceiverRegistered = false;
    private VoiceBroadcastReceiver voiceBroadcastReceiver;

    // Empty HashMap, contains parameters for the Outbound call
    private HashMap<String, String> twiMLParams = new HashMap<>();

    private CallNotificationManager callNotificationManager;
    private ProximityManager proximityManager;

    private String accessToken;

    private String toNumber = "";
    private String toName = "";

    static Map<String, Integer> callNotificationMap;

    private RegistrationListener registrationListener = registrationListener();
    private Call.Listener callListener = callListener();

    private CallInvite activeCallInvite;
    private Call activeCall;

    private AudioFocusRequest focusRequest;
    private HeadsetManager headsetManager;
    private EventManager eventManager;
    private int existingCallInviteIntent;

    public TwilioVoiceModule(ReactApplicationContext reactContext,
    boolean shouldAskForMicPermission) {
        super(reactContext);

        if (BuildConfig.DEBUG) {
            Voice.setLogLevel(LogLevel.DEBUG);
        } else {
            Voice.setLogLevel(LogLevel.ERROR);
        }
        reactContext.addActivityEventListener(this);
        reactContext.addLifecycleEventListener(this);

        eventManager = new EventManager(reactContext);
        callNotificationManager = new CallNotificationManager();
        proximityManager = new ProximityManager(reactContext, eventManager);
        headsetManager = new HeadsetManager(eventManager);

        /*
         * Setup the broadcast receiver to be notified of GCM Token updates
         * or incoming call messages in this Activity.
         */
        voiceBroadcastReceiver = new VoiceBroadcastReceiver();
        registerReceiver();

        TwilioVoiceModule.callNotificationMap = new HashMap<>();

        /*
         * Needed for setting/abandoning audio focus during a call
         */
        audioManager = (AudioManager) reactContext.getSystemService(Context.AUDIO_SERVICE);

        /*
         * Ensure the microphone permission is enabled
         */
        if (shouldAskForMicPermission && !checkPermissionForMicrophone()) {
            requestPermissionForMicrophone();
        }
    }

    @Override
    public void onHostResume() {
        /*
         * Enable changing the volume using the up/down keys during a conversation
         */
        getCurrentActivity().setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        registerReceiver();

        Intent intent = getCurrentActivity().getIntent();
        if (intent == null || intent.getAction() == null) {
            return;
        }
        int currentCallInviteIntent = intent.hashCode();
        String action = intent.getAction();
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onHostResume(). Action: " + action + ". Intent: " + intent.getExtras());
        }

        if (action.equals(Intent.ACTION_MAIN)) {
            return;
        }

        if (action.equals(Constants.ACTION_ACCEPT) && currentCallInviteIntent == existingCallInviteIntent) {
            return;
        }

        if (action.equals(Constants.ACTION_INCOMING_CALL_NOTIFICATION) && currentCallInviteIntent == existingCallInviteIntent) {
            return;
        }

        existingCallInviteIntent = currentCallInviteIntent;
        handleStartActivityIntent(intent);
    }

    @Override
    public void onHostPause() {
        // the library needs to listen for events even when the app is paused
//        unregisterReceiver();
    }

    @Override
    public void onHostDestroy() {
        disconnect();
        callNotificationManager.removeHangupNotification(getReactApplicationContext());
        unsetAudioFocus();
    }

    @Override
    public String getName() {
        return TAG;
    }

    @Override
    public void onNewIntent(Intent intent) {
        // This is called only when the App is in the foreground
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onNewIntent(). Intent: " + intent.toString());
        }
        handleStartActivityIntent(intent);
    }

    private RegistrationListener registrationListener() {
        return new RegistrationListener() {
            @Override
            public void onRegistered(@NonNull String accessToken, @NonNull String fcmToken) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "RegistrationListener().onRegistered(). FCM registered.");
                }
                eventManager.sendEvent(EVENT_DEVICE_READY, null);
            }

            @Override
            public void onError(@NonNull RegistrationException error,
                                @NonNull String accessToken,
                                @NonNull String fcmToken) {
                Log.e(TAG, String.format("RegistrationListener().onError(). Code: %d. %s", error.getErrorCode(), error.getMessage()));
                WritableMap params = Arguments.createMap();
                params.putString(Constants.ERROR, error.getMessage());
                eventManager.sendEvent(EVENT_DEVICE_NOT_READY, params);
            }
        };
    }

    private Call.Listener callListener() {
        return new Call.Listener() {
            /*
             * This callback is emitted once before the Call.Listener.onConnected() callback when
             * the callee is being alerted of a Call. The behavior of this callback is determined by
             * the answerOnBridge flag provided in the Dial verb of your TwiML application
             * associated with this client. If the answerOnBridge flag is false, which is the
             * default, the Call.Listener.onConnected() callback will be emitted immediately after
             * Call.Listener.onRinging(). If the answerOnBridge flag is true, this will cause the
             * call to emit the onConnected callback only after the call is answered.
             * See answeronbridge for more details on how to use it with the Dial TwiML verb. If the
             * twiML response contains a Say verb, then the call will emit the
             * Call.Listener.onConnected callback immediately after Call.Listener.onRinging() is
             * raised, irrespective of the value of answerOnBridge being set to true or false
             */
            @Override
            public void onRinging(@NonNull Call call) {
                // TODO test this with JS app
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Call.Listener().onRinging(). Call state: " + call.getState() + ". Call: "+ call.toString());
                }
                WritableMap params = Arguments.createMap();
                if (call != null) {
                    params.putString(Constants.CALL_SID,  call.getSid());
                    params.putString(Constants.CALL_FROM, call.getFrom());
                }
                eventManager.sendEvent(EVENT_CALL_STATE_RINGING, params);
            }

            @Override
            public void onConnected(@NonNull Call call) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Call.Listener().onConnected(). Call state: " + call.getState());
                }
                setAudioFocus();
                proximityManager.startProximitySensor();
                headsetManager.startWiredHeadsetEvent(getReactApplicationContext());

                WritableMap params = Arguments.createMap();
                params.putString(Constants.CALL_SID, call.getSid());
                params.putString(Constants.CALL_STATE, call.getState().name());
                params.putString(Constants.CALL_FROM, call.getFrom());
                params.putString(Constants.CALL_TO, call.getTo());
                String caller = "Show call details in the app";
                if (!toName.equals("")) {
                    caller = toName;
                } else if (!toNumber.equals("")) {
                    caller = toNumber;
                }
                activeCall = call;
                callNotificationManager.createHangupNotification(getReactApplicationContext(),
                        call.getSid(), caller);
                eventManager.sendEvent(EVENT_CONNECTION_DID_CONNECT, params);
                activeCallInvite = null;
            }

            /**
             * `onReconnecting()` callback is raised when a network change is detected and Call is already in `CONNECTED`
             * `Call.State`. If the call is in `CONNECTING` or `RINGING` when network change happened the SDK will continue
             * attempting to connect, but a reconnect event will not be raised.
             */
            @Override
            public void onReconnecting(@NonNull Call call, @NonNull CallException callException) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Call.Listener().onReconnecting(). Call state: " + call.getState());
                }
                WritableMap params = Arguments.createMap();
                params.putString(Constants.CALL_SID, call.getSid());
                params.putString(Constants.CALL_FROM, call.getFrom());
                params.putString(Constants.CALL_TO, call.getTo());
                eventManager.sendEvent(EVENT_CONNECTION_IS_RECONNECTING, params);
            }

            /**
             * The call is successfully reconnected after reconnecting attempt.
             */
            @Override
            public void onReconnected(@NonNull Call call) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Call.Listener().onReconnected(). Call state: " + call.getState());
                }
                WritableMap params = Arguments.createMap();
                params.putString(Constants.CALL_SID, call.getSid());
                params.putString(Constants.CALL_FROM, call.getFrom());
                params.putString(Constants.CALL_TO, call.getTo());
                eventManager.sendEvent(EVENT_CONNECTION_DID_RECONNECT, params);
            }

            @Override
            public void onDisconnected(@NonNull Call call, CallException error) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Call.Listener().onDisconnected(). Call state: " + call.getState());
                }
                unsetAudioFocus();
                proximityManager.stopProximitySensor();
                headsetManager.stopWiredHeadsetEvent(getReactApplicationContext());

                WritableMap params = Arguments.createMap();
                String callSid = "";
                callSid = call.getSid();
                params.putString(Constants.CALL_SID, callSid);
                params.putString(Constants.CALL_STATE, call.getState().name());
                params.putString(Constants.CALL_FROM, call.getFrom());
                params.putString(Constants.CALL_TO, call.getTo());
                if (error != null) {
                    Log.e(TAG, String.format("CallListener onDisconnected error: %d, %s",
                            error.getErrorCode(), error.getMessage()));
                    params.putString(Constants.ERROR, error.getMessage());
                }
                if (callSid != null && activeCall != null && activeCall.getSid() != null && activeCall.getSid().equals(callSid)) {
                    activeCall = null;
                }
                eventManager.sendEvent(EVENT_CONNECTION_DID_DISCONNECT, params);
                callNotificationManager.removeHangupNotification(getReactApplicationContext());
                toNumber = "";
                toName = "";
                activeCallInvite = null;
            }

            @Override
            public void onConnectFailure(@NonNull Call call, CallException error) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Call.Listener().onConnectFailure(). Call state: " + call.getState());
                }
                unsetAudioFocus();
                proximityManager.stopProximitySensor();

                Log.e(TAG, String.format("CallListener onConnectFailure error: %d, %s",
                    error.getErrorCode(), error.getMessage()));

                WritableMap params = Arguments.createMap();
                params.putString(Constants.ERROR, error.getMessage());
                String callSid = "";
                callSid = call.getSid();
                params.putString(Constants.CALL_SID, callSid);
                params.putString(Constants.CALL_STATE, call.getState().name());
                params.putString(Constants.CALL_FROM, call.getFrom());
                params.putString(Constants.CALL_TO, call.getTo());
                if (callSid != null && activeCall != null && activeCall.getSid() != null && activeCall.getSid().equals(callSid)) {
                    activeCall = null;
                }
                eventManager.sendEvent(EVENT_CONNECTION_DID_DISCONNECT, params);
                callNotificationManager.removeHangupNotification(getReactApplicationContext());
                toNumber = "";
                toName = "";
                activeCallInvite = null;
            }
        };
    }

    /**
     * Register the Voice broadcast receiver
     */
    private void registerReceiver() {
        if (isReceiverRegistered) {
            return;
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constants.ACTION_INCOMING_CALL);
        intentFilter.addAction(Constants.ACTION_CANCEL_CALL);
        LocalBroadcastManager.getInstance(getReactApplicationContext()).registerReceiver(
                voiceBroadcastReceiver, intentFilter);
        registerActionReceiver();
        isReceiverRegistered = true;
    }

    private void unregisterReceiver() {
        if (!isReceiverRegistered) {
            return;
        }
        LocalBroadcastManager.getInstance(getReactApplicationContext()).unregisterReceiver(voiceBroadcastReceiver);
        isReceiverRegistered = false;
    }

    private void removeMissedCalls() {
        SharedPreferences sharedPref = getReactApplicationContext().getSharedPreferences(
                Constants.PREFERENCE_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor sharedPrefEditor = sharedPref.edit();
        sharedPrefEditor.putInt(Constants.MISSED_CALLS_GROUP, 0);
        sharedPrefEditor.commit();
    }

    private class VoiceBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }
            String action = intent.getAction();
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "VoiceBroadcastReceiver.onReceive() action: " + action + ". Intent extra: " + intent.getExtras());
            }
            activeCallInvite = intent.getParcelableExtra(Constants.INCOMING_CALL_INVITE);

            switch (action) {
                // when a callInvite is received in the foreground
                case Constants.ACTION_INCOMING_CALL:
                    handleCallInviteNotification();
                    break;

                case Constants.ACTION_CANCEL_CALL:
                    handleCancelCall(intent);
                    break;

            }
        }
    }

    private void registerActionReceiver() {

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constants.ACTION_HANGUP_CALL);
        intentFilter.addAction(Constants.ACTION_CLEAR_MISSED_CALLS_COUNT);

        getReactApplicationContext().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "BroadcastReceiver.onReceive() action: " + action);
                }
                switch (action) {
                    case Constants.ACTION_HANGUP_CALL:
                        disconnect();
                        break;
                    case Constants.ACTION_CLEAR_MISSED_CALLS_COUNT:
                        removeMissedCalls();
                        break;
                }
            }
        }, intentFilter);
    }

    // removed @Override temporarily just to get it working on different versions of RN
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        onActivityResult(requestCode, resultCode, data);
    }

    // removed @Override temporarily just to get it working on different versions of RN
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Ignored, required to implement ActivityEventListener for RN 0.33
    }

    private void handleStartActivityIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        String action = intent.getAction();
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "handleStartActivityIntent() action: " + action);
        }
        activeCallInvite = intent.getParcelableExtra(Constants.INCOMING_CALL_INVITE);

        switch (action) {
            case Constants.ACTION_INCOMING_CALL_NOTIFICATION:
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "ACTION_INCOMING_CALL_NOTIFICATION handleStartActivityIntent");
                }
                WritableMap params = Arguments.createMap();
                params.putString(Constants.CALL_SID, activeCallInvite.getCallSid());
                params.putString(Constants.CALL_FROM, activeCallInvite.getFrom());
                params.putString(Constants.CALL_TO, activeCallInvite.getTo());
                eventManager.sendEvent(EVENT_DEVICE_DID_RECEIVE_INCOMING, params);
                break;

            case Constants.ACTION_MISSED_CALL:
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "ACTION_MISSED_CALL handleStartActivityIntent");
                }
                removeMissedCalls();
                break;

            case Constants.ACTION_CLEAR_MISSED_CALLS_COUNT:
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "ACTION_CLEAR_MISSED_CALLS_COUNT handleStartActivityIntent");
                }
                removeMissedCalls();
                break;

            case Constants.ACTION_FCM_TOKEN:
                registerForCallInvites();
                break;

            case Constants.ACTION_ACCEPT:
                acceptFromIntent(intent);
                break;

            case Constants.ACTION_OPEN_CALL_IN_PROGRESS:
                // the notification already brings the activity to the top
                break;

            default:
                Log.e(TAG, "received broadcast unhandled action " + action);
                break;
        }
    }

    private void handleCallInviteNotification() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "handleCallInviteNotification()");
        }
        if (activeCallInvite == null) {
            Log.e(TAG, "NO active call invite");
            return;
        }
        SoundPoolManager.getInstance(getReactApplicationContext()).playRinging();

        WritableMap params = Arguments.createMap();
        params.putString(Constants.CALL_SID, activeCallInvite.getCallSid());
        params.putString(Constants.CALL_FROM, activeCallInvite.getFrom());
        params.putString(Constants.CALL_TO, activeCallInvite.getTo());
        eventManager.sendEvent(EVENT_DEVICE_DID_RECEIVE_INCOMING, params);
    }

    private void handleCancelCall(Intent intent) {
        CancelledCallInvite cancelledCallInvite = intent.getParcelableExtra(Constants.CANCELLED_CALL_INVITE);

        ReactApplicationContext ctx = getReactApplicationContext();
        SoundPoolManager.getInstance(ctx).stopRinging();
        callNotificationManager.createMissedCallNotification(
                getReactApplicationContext(),
                cancelledCallInvite.getCallSid(),
                cancelledCallInvite.getFrom()
        );

        WritableMap params = Arguments.createMap();

        // TODO check whether the params should be passed anyway
        int appImportance = callNotificationManager.getApplicationImportance(ctx);
        if (appImportance <= RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
            params.putString(Constants.CALL_SID, cancelledCallInvite.getCallSid());
            params.putString(Constants.CALL_FROM, cancelledCallInvite.getFrom());
            params.putString(Constants.CALL_TO, cancelledCallInvite.getTo());
            String cancelledCallInviteErr = intent.getStringExtra(Constants.CANCELLED_CALL_INVITE_EXCEPTION);
            // pass this to the event even though in v5.0.2 it is always "Call Cancelled"
            if (cancelledCallInviteErr != null) {
                params.putString(Constants.ERROR, cancelledCallInviteErr);
            }
        }
        // TODO handle custom parameters
        eventManager.sendEvent(EVENT_CALL_INVITE_CANCELLED, params);
    }

    @ReactMethod
    public void initWithAccessToken(final String accessToken, Promise promise) {
        if (accessToken.equals("")) {
            promise.reject(new JSApplicationIllegalArgumentException("Invalid access token"));
            return;
        }

        if(!checkPermissionForMicrophone()) {
            promise.reject(new AssertionException("Allow microphone permission"));
            return;
        }

        TwilioVoiceModule.this.accessToken = accessToken;
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "initWithAccessToken()");
        }
        registerForCallInvites();
        WritableMap params = Arguments.createMap();
        params.putBoolean("initialized", true);
        promise.resolve(params);
    }

    /*
     * Register your FCM token with Twilio to receive incoming call invites
     *
     * If a valid google-services.json has not been provided or the FirebaseInstanceId has not been
     * initialized the fcmToken will be null.
     *
     */
    private void registerForCallInvites() {
        String fcmToken = FirebaseInstanceId.getInstance().getToken();
        if (fcmToken == null) {
            return;
        }
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "Registering with FCM");
        }
        Voice.register(accessToken, Voice.RegistrationChannel.FCM, fcmToken, registrationListener);
    }

    public void acceptFromIntent(Intent intent) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "acceptFromIntent()");
        }
        activeCallInvite = intent.getParcelableExtra(Constants.INCOMING_CALL_INVITE);
        if (activeCallInvite == null) {
            eventManager.sendEvent(EVENT_CALL_INVITE_CANCELLED, null);
            return;
        }

        SoundPoolManager.getInstance(getReactApplicationContext()).stopRinging();

        AcceptOptions acceptOptions = new AcceptOptions.Builder()
                .enableDscp(true)
                .build();
        activeCallInvite.accept(getReactApplicationContext(), acceptOptions, callListener);
    }

    @ReactMethod
    public void accept() {
        SoundPoolManager.getInstance(getReactApplicationContext()).stopRinging();
        if (activeCallInvite == null) {
            eventManager.sendEvent(EVENT_CALL_INVITE_CANCELLED, null);
            return;
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "accept()");
        }
        AcceptOptions acceptOptions = new AcceptOptions.Builder()
                .enableDscp(true)
                .build();
        activeCallInvite.accept(getReactApplicationContext(), acceptOptions, callListener);
    }

    @ReactMethod
    public void reject() {
        SoundPoolManager.getInstance(getReactApplicationContext()).stopRinging();
        WritableMap params = Arguments.createMap();
        if (activeCallInvite != null) {
            params.putString(Constants.CALL_SID,   activeCallInvite.getCallSid());
            params.putString(Constants.CALL_FROM,  activeCallInvite.getFrom());
            params.putString(Constants.CALL_TO,    activeCallInvite.getTo());
            activeCallInvite.reject(getReactApplicationContext());
        }
        eventManager.sendEvent(EVENT_CALL_INVITE_CANCELLED, params);
        activeCallInvite = null;
    }

    @ReactMethod
    public void ignore() {
        SoundPoolManager.getInstance(getReactApplicationContext()).stopRinging();
    }

    @ReactMethod
    public void connect(ReadableMap params) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "connect(). Params: "+params);
        }
        WritableMap errParams = Arguments.createMap();
        if (accessToken == null) {
            errParams.putString(Constants.ERROR, "Invalid access token");
            eventManager.sendEvent(EVENT_DEVICE_NOT_READY, errParams);
            return;
        }
        if (params == null) {
            errParams.putString(Constants.ERROR, "Invalid parameters");
            eventManager.sendEvent(EVENT_CONNECTION_DID_DISCONNECT, errParams);
            return;
        } else if (!params.hasKey("To")) {
            errParams.putString(Constants.ERROR, "Invalid To parameter");
            eventManager.sendEvent(EVENT_CONNECTION_DID_DISCONNECT, errParams);
            return;
        }
        toNumber = params.getString("To");
        if (params.hasKey("ToName")) {
            toName = params.getString("ToName");
        }

        twiMLParams.clear();

        ReadableMapKeySetIterator iterator = params.keySetIterator();
        while (iterator.hasNextKey()) {
            String key = iterator.nextKey();
            ReadableType readableType = params.getType(key);
            switch (readableType) {
                case Null:
                    twiMLParams.put(key, "");
                    break;
                case Boolean:
                    twiMLParams.put(key, String.valueOf(params.getBoolean(key)));
                    break;
                case Number:
                    // Can be int or double.
                    twiMLParams.put(key, String.valueOf(params.getDouble(key)));
                    break;
                case String:
                    twiMLParams.put(key, params.getString(key));
                    break;
                default:
                    Log.e(TAG, "Could not convert key: " + key + ". ReadableType: "+ readableType.toString());
                    break;
            }
        }

        ConnectOptions connectOptions = new ConnectOptions.Builder(accessToken)
                .enableDscp(true)
                .params(twiMLParams)
                .build();

        activeCall = Voice.connect(getReactApplicationContext(), connectOptions, callListener);
    }

    @ReactMethod
    public void disconnect() {
        if (activeCall != null) {
            activeCall.disconnect();
            activeCall = null;
        }
    }

    @ReactMethod
    public void setMuted(Boolean value) {
        if (activeCall != null) {
            activeCall.mute(value);
        }
    }

    @ReactMethod
    public void sendDigits(String digits) {
        if (activeCall != null) {
            activeCall.sendDigits(digits);
        }
    }

    @ReactMethod
    public void getActiveCall(Promise promise) {
        if (activeCall == null) {
            promise.resolve(null);
            return;
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "getActiveCall(). Active call state: " + activeCall.getState());
        }
        WritableMap params = Arguments.createMap();
        String toNum = activeCall.getTo();
        if (toNum == null) {
            toNum = toNumber;
        }
        params.putString(Constants.CALL_SID,   activeCall.getSid());
        params.putString(Constants.CALL_FROM,  activeCall.getFrom());
        params.putString(Constants.CALL_TO,    toNum);
        params.putString(Constants.CALL_STATE, activeCall.getState().name());
        promise.resolve(params);
    }

    @ReactMethod
    public void getCallInvite(Promise promise) {
        if (activeCallInvite == null) {
            promise.resolve(null);
            return;
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "getCallInvite(). Call invite: " + activeCallInvite);
        }
        WritableMap params = Arguments.createMap();
        params.putString(Constants.CALL_SID,   activeCallInvite.getCallSid());
        params.putString(Constants.CALL_FROM,  activeCallInvite.getFrom());
        params.putString(Constants.CALL_TO,    activeCallInvite.getTo());
        promise.resolve(params);
    }

    @ReactMethod
    public void setSpeakerPhone(Boolean value) {
        // TODO check whether it is necessary to call setAudioFocus again
//        setAudioFocus();
        audioManager.setSpeakerphoneOn(value);
    }

    @ReactMethod
    public void setOnHold(Boolean value) {
        if (activeCall != null) {
            activeCall.hold(value);
        }
    }

    private void setAudioFocus() {
        if (audioManager == null) {
            audioManager.setMode(savedAudioMode);
            audioManager.abandonAudioFocus(null);
            return;
        }
        savedAudioMode = audioManager.getMode();
        // Request audio focus before making any device switch
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                    @Override
                    public void onAudioFocusChange(int i) { }
                })
                .build();
            audioManager.requestAudioFocus(focusRequest);
        } else {
            int focusRequestResult = audioManager.requestAudioFocus(new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {}
            },
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
        /*
         * Start by setting MODE_IN_COMMUNICATION as default audio mode. It is
         * required to be in this mode when playout and/or recording starts for
         * best possible VoIP performance. Some devices have difficulties with speaker mode
         * if this is not set.
         */
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
    }

    private void unsetAudioFocus() {
        if (audioManager == null) {
            audioManager.setMode(savedAudioMode);
            audioManager.abandonAudioFocus(null);
            return;
        }
        audioManager.setMode(savedAudioMode);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest != null) {
                audioManager.abandonAudioFocusRequest(focusRequest);
            }
        } else {
            audioManager.abandonAudioFocus(null);
        }
    }

    private boolean checkPermissionForMicrophone() {
        int resultMic = ContextCompat.checkSelfPermission(getReactApplicationContext(), Manifest.permission.RECORD_AUDIO);
        return resultMic == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissionForMicrophone() {
        if (getCurrentActivity() == null) {
            return;
        }
        if (ActivityCompat.shouldShowRequestPermissionRationale(getCurrentActivity(), Manifest.permission.RECORD_AUDIO)) {
            // TODO
        } else {
            ActivityCompat.requestPermissions(getCurrentActivity(), new String[]{Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION_REQUEST_CODE);
        }
    }

    public static Bundle getActivityLaunchOption(Intent intent) {
        Bundle initialProperties = new Bundle();
        if (intent == null || intent.getAction() == null) {
            return initialProperties;
        }
        Bundle callBundle = new Bundle();
        switch (intent.getAction()) {
            case Constants.ACTION_INCOMING_CALL_NOTIFICATION:
                callBundle.putString(Constants.CALL_SID, intent.getStringExtra(Constants.CALL_SID));
                callBundle.putString(Constants.CALL_FROM, intent.getStringExtra(Constants.CALL_FROM));
                callBundle.putString(Constants.CALL_TO, intent.getStringExtra(Constants.CALL_TO));
                initialProperties.putBundle(Constants.CALL_INVITE_KEY, callBundle);
                break;

            case Constants.ACTION_ACCEPT:
                callBundle.putString(Constants.CALL_SID, intent.getStringExtra(Constants.CALL_SID));
                callBundle.putString(Constants.CALL_FROM, intent.getStringExtra(Constants.CALL_FROM));
                callBundle.putString(Constants.CALL_TO, intent.getStringExtra(Constants.CALL_TO));
                callBundle.putString(Constants.CALL_STATE, Constants.CALL_STATE_CONNECTED);
                initialProperties.putBundle(Constants.CALL_KEY, callBundle);
                break;
        }
        return initialProperties;
    }
}
