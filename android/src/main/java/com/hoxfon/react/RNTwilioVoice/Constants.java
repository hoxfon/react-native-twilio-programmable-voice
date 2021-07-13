package com.hoxfon.react.RNTwilioVoice;

import com.twilio.voice.Call;

public class Constants {
    public static final String MISSED_CALLS_GROUP = "MISSED_CALLS";
    public static final int MISSED_CALLS_NOTIFICATION_ID = 1;
    public static final int HANGUP_NOTIFICATION_ID = 11;
    public static final int CLEAR_MISSED_CALLS_NOTIFICATION_ID = 21;
    public static final String PREFERENCE_KEY = "com.hoxfon.react.RNTwilioVoice.PREFERENCE_FILE_KEY";

    public static final String CALL_SID_KEY = "CALL_SID";
    public static final String VOICE_CHANNEL_LOW_IMPORTANCE = "notification-channel-low-importance";
    public static final String VOICE_CHANNEL_HIGH_IMPORTANCE = "notification-channel-high-importance";
    public static final String INCOMING_CALL_INVITE = "INCOMING_CALL_INVITE";
    public static final String CANCELLED_CALL_INVITE = "CANCELLED_CALL_INVITE";
    public static final String CANCELLED_CALL_INVITE_EXCEPTION = "CANCELLED_CALL_INVITE_ERROR";
    public static final String INCOMING_CALL_NOTIFICATION_ID = "INCOMING_CALL_NOTIFICATION_ID";
    public static final String ACTION_ACCEPT = "com.hoxfon.react.RNTwilioVoice.ACTION_ACCEPT";
    public static final String ACTION_REJECT = "com.hoxfon.react.RNTwilioVoice.ACTION_REJECT";
    public static final String ACTION_MISSED_CALL = "MISSED_CALL";
    public static final String ACTION_HANGUP_CALL = "HANGUP_CALL";
    public static final String ACTION_INCOMING_CALL = "ACTION_INCOMING_CALL";
    public static final String ACTION_INCOMING_CALL_NOTIFICATION = "ACTION_INCOMING_CALL_NOTIFICATION";
    public static final String ACTION_CANCEL_CALL = "ACTION_CANCEL_CALL";
    public static final String ACTION_FCM_TOKEN = "ACTION_FCM_TOKEN";
    public static final String ACTION_CLEAR_MISSED_CALLS_COUNT = "CLEAR_MISSED_CALLS_COUNT";
    public static final String ACTION_OPEN_CALL_IN_PROGRESS = "CALL_IN_PROGRESS";
    public static final String ACTION_JS_ANSWER = "ACTION_JS_ANSWER";
    public static final String ACTION_JS_REJECT = "ACTION_JS_REJECT";

    public static final String CALL_SID = "call_sid";
    public static final String CALL_STATE = "call_state";
    public static final String CALL_FROM = "call_from";
    public static final String CALL_TO = "call_to";
    public static final String ERROR = "err";
    public static final String CALL_KEY = "call";
    public static final String CALL_INVITE_KEY = "callInvite";
    public static final String CALL_STATE_CONNECTED = Call.State.CONNECTED.toString();
    public static final String SELECTED_AUDIO_DEVICE = "selected_audio_device";
    public static final String CALLER_VERIFICATION_STATUS = "caller_verification";
    public static final String CALLER_VERIFICATION_VERIFIED = "verified";
    public static final String CALLER_VERIFICATION_UNVERIFIED = "unverified";
    public static final String CALLER_VERIFICATION_UNKNOWN = "unknown";
}
