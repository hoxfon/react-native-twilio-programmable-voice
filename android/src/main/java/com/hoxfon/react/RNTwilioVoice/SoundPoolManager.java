package com.hoxfon.react.RNTwilioVoice;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;

public class SoundPoolManager {

    private boolean playing = false;
    private static SoundPoolManager instance;
    private Ringtone ringtone = null;

    private SoundPoolManager(Context context) {
        Uri ringtoneSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        ringtone = RingtoneManager.getRingtone(context, ringtoneSound);
        AudioAttributes alarmAttribute = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        ringtone.setAudioAttributes(alarmAttribute);
    }

    public static SoundPoolManager getInstance(Context context) {
        if (instance == null) {
            instance = new SoundPoolManager(context);
        }
        return instance;
    }

    public void playRinging() {
        if (!playing) {
            ringtone.play();
            playing = true;
        }
    }

    public void stopRinging() {
        if (playing) {
            ringtone.stop();
            playing = false;
        }
    }

    public void playDisconnect() {
        if (!playing) {
            ringtone.stop();
            playing = false;
        }
    }

}
