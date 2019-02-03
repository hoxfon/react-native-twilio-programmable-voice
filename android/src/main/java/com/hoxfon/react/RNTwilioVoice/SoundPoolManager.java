package com.hoxfon.react.RNTwilioVoice;

import android.content.Context;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.SoundPool;
import android.media.AudioManager;
import android.os.Build;
import android.net.Uri;

import static android.content.Context.AUDIO_SERVICE;

public class SoundPoolManager {

    private boolean playing = false;
    private static SoundPoolManager instance;
    private Ringtone ringtone = null;
    private SoundPool soundPool;
    private int disconnectSoundId;
    private AudioManager audioManager;
    private float actualVolume;
    private float maxVolume;
    private float volume;

    private SoundPoolManager(Context context) {
        audioManager = (AudioManager) context.getSystemService(AUDIO_SERVICE);
        //actualVolume = (float) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        //maxVolume = (float) audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        //volume = actualVolume / maxVolume;
        Uri ringtoneSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        ringtone = RingtoneManager.getRingtone(context, ringtoneSound);
        int maxStreams = 1;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            soundPool = new SoundPool.Builder().setMaxStreams(maxStreams).build();
        } else {
            soundPool = new SoundPool(maxStreams, AudioManager.STREAM_MUSIC, 0);
        }
        disconnectSoundId = soundPool.load(context, R.raw.disconnect, 1);
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
        if (playing) {
            ringtone.stop();
        }
        soundPool.play(disconnectSoundId, 1f, 1f, 1, 0, 1f);
        playing = false;
    }

}
