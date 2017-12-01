package com.hoxfon.react.RNTwilioVoice;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_PROXIMITY;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.TAG;

public class ProximityManager {

    private static final String ERROR_PROXIMITY_SENSOR_NOT_SUPPORTED = "Proximity sensor is not supported.";
    private static final String ERROR_PROXIMITY_LOCK_NOT_SUPPORTED = "Proximity lock is not supported.";

    private SensorManager sensorManager;

    private Sensor proximitySensor;
    private SensorEventListener proximityListener;

    private WakeLock proximityWakeLock = null;
    private PowerManager powerManager;

    private EventManager eventManager;

    public ProximityManager(ReactApplicationContext context, EventManager em) {
        eventManager = em;
        powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        initProximityWakeLock();
    }

    private void initProximityWakeLock() {
        // Check if PROXIMITY_SCREEN_OFF_WAKE_LOCK is implemented, not part of public api.
        // PROXIMITY_SCREEN_OFF_WAKE_LOCK and isWakeLockLevelSupported are available from api 21
        try {
            boolean isSupported;
            int proximityScreenOffWakeLock;
            if (android.os.Build.VERSION.SDK_INT < 21) {
                Field field = PowerManager.class.getDeclaredField("PROXIMITY_SCREEN_OFF_WAKE_LOCK");
                proximityScreenOffWakeLock = (Integer) field.get(null);

                Method method = powerManager.getClass().getDeclaredMethod("getSupportedWakeLockFlags");
                int powerManagerSupportedFlags = (Integer) method.invoke(powerManager);
                isSupported = ((powerManagerSupportedFlags & proximityScreenOffWakeLock) != 0x0);
            } else {
                proximityScreenOffWakeLock = powerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK;
                isSupported = powerManager.isWakeLockLevelSupported(proximityScreenOffWakeLock);
            }
            if (isSupported) {
                proximityWakeLock = powerManager.newWakeLock(proximityScreenOffWakeLock, TAG);
                proximityWakeLock.setReferenceCounted(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get proximity screen locker.");
        }
    }

    private void turnScreenOn() {
        if (proximityWakeLock == null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, ERROR_PROXIMITY_LOCK_NOT_SUPPORTED);
            }
            return;
        }
        synchronized (proximityWakeLock) {
            if (proximityWakeLock.isHeld()) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "turnScreenOn()");
                }
                if (android.os.Build.VERSION.SDK_INT >= 21) {
                    proximityWakeLock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
                }
            }
        }
    }

    private void turnScreenOff() {
        if (proximityWakeLock == null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, ERROR_PROXIMITY_LOCK_NOT_SUPPORTED);
            }
            return;
        }
        synchronized (proximityWakeLock) {
            if (!proximityWakeLock.isHeld()) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "turnScreenOff()");
                }
                proximityWakeLock.acquire();
            }
        }
    }

    private void initProximitySensorEventListener() {
        if (proximityListener != null) {
            return;
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "initProximitySensorEventListener()");
        }
        proximityListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                if (sensorEvent.sensor.getType() == Sensor.TYPE_PROXIMITY) {
                    boolean isNear = false;
                    if (sensorEvent.values[0] < proximitySensor.getMaximumRange()) {
                        isNear = true;
                    }
                    if (isNear) {
                        turnScreenOff();
                    } else {
                        turnScreenOn();
                    }
                    WritableMap data = Arguments.createMap();
                    data.putBoolean("isNear", isNear);
                    eventManager.sendEvent(EVENT_PROXIMITY, data);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };
    }

    public void startProximitySensor() {
        if (proximitySensor == null) {
            Log.e(TAG, ERROR_PROXIMITY_SENSOR_NOT_SUPPORTED);
            return;
        }
        initProximitySensorEventListener();
        if (proximityListener != null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "register proximity listener");
            }
            // SensorManager.SENSOR_DELAY_FASTEST(0 ms),
            // SensorManager.SENSOR_DELAY_GAME(20 ms),
            // SensorManager.SENSOR_DELAY_UI(60 ms),
            // SensorManager.SENSOR_DELAY_NORMAL(200 ms)
            sensorManager.registerListener(
                proximityListener,
                proximitySensor,
                SensorManager.SENSOR_DELAY_NORMAL
            );
        }
    }

    public void stopProximitySensor() {
        if (proximitySensor == null) {
            Log.e(TAG, ERROR_PROXIMITY_SENSOR_NOT_SUPPORTED);
            return;
        }
        if (proximityListener != null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "unregister proximity listener");
            }
            sensorManager.unregisterListener(proximityListener);
            proximityListener = null;
        }
    }
}
