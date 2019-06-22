package com.hoxfon.react.RNTwilioVoice;

import android.app.Activity;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;
import com.facebook.react.bridge.JSApplicationIllegalArgumentException;
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
import com.twilio.voice.Call;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.CancelledCallInvite;
import com.twilio.voice.ConnectOptions;
import com.twilio.voice.LogLevel;
import com.twilio.voice.MessageListener;
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

public class TwilioVoiceModule extends ReactContextBaseJavaModule implements ActivityEventListener, LifecycleEventListener {

    public static String TAG = "RNTwilioVoice";

    // Empty HashMap, contains parameters for the Outbound call
    private HashMap<String, String> twiMLParams = new HashMap<>();
    private String accessToken;
    private CallInvite activeCallInvite;
    private String toNumber = "";
    private String toName = "";
    static Map<String, Integer> callNotificationMap;
    private RegistrationListener registrationListener = registrationListener();
    private Call.Listener callListener = callListener();
    private Call activeCall;

    // this variable determines when to create missed calls notifications
    private Boolean callAccepted = false;
    private EventManager eventManager;

    public TwilioVoiceModule(ReactApplicationContext reactContext) {
        super(reactContext);
        if (BuildConfig.DEBUG) {
            Voice.setLogLevel(LogLevel.DEBUG);
        } else {
            Voice.setLogLevel(LogLevel.ERROR);
        }
        reactContext.addActivityEventListener(this);
        reactContext.addLifecycleEventListener(this);

        eventManager = new EventManager(reactContext);
        TwilioVoiceModule.callNotificationMap = new HashMap<>();
    }

    @Override
    public void onHostResume() {

    }

    @Override
    public void onHostPause() {

    }

    @Override
    public void onHostDestroy() {
        disconnect();
    }

    @Override
    public String getName() {
        return TAG;
    }

    public void onNewIntent(Intent intent) {
        // This is called only when the App is in the foreground
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onNewIntent " + intent.toString());
        }
    }

    private RegistrationListener registrationListener() {
        return new RegistrationListener() {
            @Override
            public void onRegistered(String accessToken, String fcmToken) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Successfully registered FCM");
                }
                eventManager.sendEvent(EVENT_DEVICE_READY, null);
            }

            @Override
            public void onError(RegistrationException error, String accessToken, String fcmToken) {
                Log.e(TAG, String.format("Registration Error: %d, %s", error.getErrorCode(), error.getMessage()));
                WritableMap params = Arguments.createMap();
                params.putString("err", error.getMessage());
                eventManager.sendEvent(EVENT_DEVICE_NOT_READY, params);
            }
        };
    }

    private Call.Listener callListener() {
        return new Call.Listener() {
            @Override
            public void onConnected(Call call) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "CALL CONNECTED callListener().onConnected call state = " + call.getState());
                }

                WritableMap params = Arguments.createMap();
                if (call != null) {
                    params.putString("call_sid", call.getSid());
                    params.putString("call_state", call.getState().name());
                    params.putString("call_from", call.getFrom());
                    params.putString("call_to", call.getTo());
                    activeCall = call;
                }
                eventManager.sendEvent(EVENT_CONNECTION_DID_CONNECT, params);
            }

            @Override
            public void onDisconnected(Call call, CallException error) {
                callAccepted = false;

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "call disconnected");
                }

                WritableMap params = Arguments.createMap();
                String callSid = "";
                if (call != null) {
                    callSid = call.getSid();
                    params.putString("call_sid", callSid);
                    params.putString("call_state", call.getState().name());
                    params.putString("call_from", call.getFrom());
                    params.putString("call_to", call.getTo());
                }
                if (error != null) {
                    Log.e(TAG, String.format("CallListener onDisconnected error: %d, %s",
                            error.getErrorCode(), error.getMessage()));
                    params.putString("err", error.getMessage());
                }
                if (callSid != null && activeCall != null && activeCall.getSid() != null && activeCall.getSid().equals(callSid)) {
                    activeCall = null;
                }
                eventManager.sendEvent(EVENT_CONNECTION_DID_DISCONNECT, params);
                toNumber = "";
                toName = "";
            }

            @Override
            public void onConnectFailure(Call call, CallException error) {
                callAccepted = false;
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "connect failure");
                }

                Log.e(TAG, String.format("CallListener onDisconnected error: %d, %s",
                        error.getErrorCode(), error.getMessage()));

                WritableMap params = Arguments.createMap();
                params.putString("err", error.getMessage());
                String callSid = "";
                if (call != null) {
                    callSid = call.getSid();
                    params.putString("call_sid", callSid);
                    params.putString("call_state", call.getState().name());
                    params.putString("call_from", call.getFrom());
                    params.putString("call_to", call.getTo());
                }
                if (callSid != null && activeCall != null && activeCall.getSid() != null && activeCall.getSid().equals(callSid)) {
                    activeCall = null;
                }
                eventManager.sendEvent(EVENT_CONNECTION_DID_DISCONNECT, params);
                toNumber = "";
                toName = "";
            }

            @Override
            public void onRinging(@NonNull Call call) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Ringing");
                }
            }
        };
    }


    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        onActivityResult(requestCode, resultCode, data);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Ignored, required to implement ActivityEventListener for RN 0.33
    }

    @ReactMethod
    public void initWithAccessToken(final String accessToken, final String fcmToken, Promise promise) {
        if (accessToken.equals("")) {
            promise.reject(new JSApplicationIllegalArgumentException("Invalid access token"));
            return;
        }

        TwilioVoiceModule.this.accessToken = accessToken;
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "initWithAccessToken ACTION_FCM_TOKEN");
        }
        registerForCallInvites(fcmToken);
        WritableMap params = Arguments.createMap();
        params.putBoolean("initialized", true);
        promise.resolve(params);
    }

    @ReactMethod
    public boolean handleCallInvite(ReadableMap notification) {

        Map<String, String> data = new HashMap<>();
        ReadableMapKeySetIterator iterator = notification.keySetIterator();
        while (iterator.hasNextKey()) {
            String key = iterator.nextKey();
            ReadableType readableType = notification.getType(key);
            switch (readableType) {
                case Null:
                    data.put(key, "");
                    break;
                case Boolean:
                    data.put(key, String.valueOf(notification.getBoolean(key)));
                    break;
                case Number:
                    // Can be int or double.
                    data.put(key, String.valueOf(notification.getDouble(key)));
                    break;
                case String:
                    data.put(key, notification.getString(key));
                    break;
                default:
                    Log.d(TAG, "Could not convert with key: " + key + ".");
                    break;
            }
        }

        boolean valid = Voice.handleMessage(data, new MessageListener() {

            @Override
            public void onCallInvite(final CallInvite callInvite) {
                WritableMap params = Arguments.createMap();
                params.putString("call_sid", callInvite.getCallSid());
                params.putString("call_from", callInvite.getFrom());
                params.putString("call_to", callInvite.getTo());
                params.putString("call_state", "PENDING");
                eventManager.sendEvent(EVENT_DEVICE_DID_RECEIVE_INCOMING, params);
                activeCallInvite = callInvite;
            }

            @Override
            public void onCancelledCallInvite(@NonNull CancelledCallInvite cancelledCallInvite) {
                WritableMap params = Arguments.createMap();
                params.putString("call_sid", cancelledCallInvite.getCallSid());
                params.putString("call_from", cancelledCallInvite.getFrom());
                params.putString("call_to", cancelledCallInvite.getTo());
                params.putString("call_state", "CANCELLED");
                eventManager.sendEvent(EVENT_CONNECTION_DID_DISCONNECT, params);
                activeCallInvite = null;
            }

        });
        return valid;
    }


    /*
     * Register your FCM token with Twilio to receive incoming call invites
     *
     * If a valid google-services.json has not been provided or the FirebaseInstanceId has not been
     * initialized the fcmToken will be null.
     *
     * In the case where the FirebaseInstanceId has not yet been initialized the
     * VoiceFirebaseInstanceIDService.onTokenRefresh should result in a LocalBroadcast to this
     * activity which will attempt registerForCallInvites again.
     *
     */
    private void registerForCallInvites(String fcmToken) {

        if (fcmToken != null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Registering with FCM");
            }
            Voice.register(accessToken, Voice.RegistrationChannel.FCM, fcmToken, registrationListener);
        }
    }

    @ReactMethod
    public void accept() {
        callAccepted = true;
        if (activeCallInvite != null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "accept() activeCallInvite.getState() PENDING");
            }
            activeCallInvite.accept(getReactApplicationContext(), callListener);
        } else {
            eventManager.sendEvent(EVENT_CONNECTION_DID_DISCONNECT, null);
        }
    }

    @ReactMethod
    public void reject() {
        callAccepted = false;
        WritableMap params = Arguments.createMap();
        if (activeCallInvite != null) {
            params.putString("call_sid", activeCallInvite.getCallSid());
            params.putString("call_from", activeCallInvite.getFrom());
            params.putString("call_to", activeCallInvite.getTo());
            params.putString("call_state", "REJECTED");
            activeCallInvite.reject(getReactApplicationContext());
        }
        eventManager.sendEvent(EVENT_CONNECTION_DID_DISCONNECT, params);
    }

    @ReactMethod
    public void ignore() {
        callAccepted = false;
        WritableMap params = Arguments.createMap();
        if (activeCallInvite != null) {
            params.putString("call_sid", activeCallInvite.getCallSid());
            params.putString("call_from", activeCallInvite.getFrom());
            params.putString("call_to", activeCallInvite.getTo());
            params.putString("call_state", "BUSY");
        }
        eventManager.sendEvent(EVENT_CONNECTION_DID_DISCONNECT, params);
    }

    @ReactMethod
    public void connect(ReadableMap params) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "connect params: " + params);
        }
        WritableMap errParams = Arguments.createMap();
        if (accessToken == null) {
            errParams.putString("err", "Invalid access token");
            eventManager.sendEvent(EVENT_DEVICE_NOT_READY, errParams);
            return;
        }
        if (params == null) {
            errParams.putString("err", "Invalid parameters");
            eventManager.sendEvent(EVENT_CONNECTION_DID_DISCONNECT, errParams);
            return;
        } else if (!params.hasKey("To")) {
            errParams.putString("err", "Invalid To parameter");
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
                    Log.d(TAG, "Could not convert with key: " + key + ".");
                    break;
            }
        }

        ConnectOptions connectOptions = new ConnectOptions.Builder(accessToken)
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

    @ReactMethod
    public void getActiveCall(Promise promise) {
        if (activeCall != null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Active call found state = " + activeCall.getState());
            }
            WritableMap params = Arguments.createMap();
            params.putString("call_sid", activeCall.getSid());
            params.putString("call_from", activeCall.getFrom());
            params.putString("call_to", activeCall.getTo());
            params.putString("call_state", activeCall.getState().name());
            promise.resolve(params);
            return;
        }
        if (activeCallInvite != null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Active call invite found state = PENDING");
            }
            WritableMap params = Arguments.createMap();
            params.putString("call_sid", activeCallInvite.getCallSid());
            params.putString("call_from", activeCallInvite.getFrom());
            params.putString("call_to", activeCallInvite.getTo());
            params.putString("call_state", "PENDING");
            promise.resolve(params);
            return;
        }
        promise.resolve(null);
    }
}