# react-native-twilio-programmable-voice
This is a React Native wrapper for Twilio Programmable Voice SDK that lets you make and receive calls from your ReactNatvie App. This module is not curated or maintained by Twilio.

## Migrating Android from v1 to v2 (incoming call use FCM)

You will need to make changes both on your Twilio account using Twilio Web Console and on your react native app.
Twilio Programmable Voice Android SDK uses `FCM` since version 2.0.0.beta5.

Before you start, I strongly suggest that you read the list of Twilio changes from Android SDK v2.0.0 beta4 to beta5:
[Twilio example App: Migrating from GCM to FCM](https://github.com/twilio/voice-quickstart-android/blob/d7d4f0658e145eb94ab8f5e34f6fd17314e7ab17/README.md#migrating-from-gcm-to-fcm)

These are all the changes required:

- remove all the GCM related code from your `AndroidManifest.xml` and add the following code to receive `FCM` notifications
(I wasn't succesful in keeping react-native-fcm working at the same time. If you know how please open an issue to share).

```xml
    .....

    <!-- Twilio Voice -->
    <!-- [START fcm_listener] -->
    <service
        android:name="com.hoxfon.react.TwilioVoice.fcm.VoiceFirebaseMessagingService">
        <intent-filter>
            <action android:name="com.google.firebase.MESSAGING_EVENT" />
        </intent-filter>
    </service>
    <!-- [END fcm_listener] -->
    <!-- [START instanceId_listener] -->
    <service
        android:name="com.hoxfon.react.TwilioVoice.fcm.VoiceFirebaseInstanceIDService"
        android:exported="false">
        <intent-filter>
            <action android:name="com.google.android.gms.iid.InstanceID" />
        </intent-filter>
    </service>
    <!-- [END instanceId_listener] -->
    <!-- Twilio Voice -->
```

- log into your Firebase console. Naviagete to: Project settings > CLOUD MESSANGING. Copy your `Server key`
- in Twilio console add a new Push Credential, type `FCM`, fcm secret Firebase FCM `Server key`
- include in your project `google-services.json`; if you have not include it yet
- rename getIncomingCall() to getActiveCall()

If something doesn't work as expected or you want to make a request open an issue.

## Help wanted!

Please notice that the `iOS` part of `react-native-twilio-programmable-voice` is missing.

No need to ask permission to contribute. Just open an issue or provide a PR. Everybody is welcome to contribute.

ReactNative success is directly linked to its module ecosystem. One way to make an impact is helping contributing to this module or another of the many community lead ones.

![help wanted](images/vjeux_tweet.png "help wanted")

## Integrating the iOS part

The main job consists into follow the [official Twilio docs](https://www.twilio.com/docs/api/voice-sdk/ios/getting-started) and wrap the SDK into a ReactNative library.
Links:
- https://github.com/twilio/voice-callkit-quickstart-swift
- https://github.com/twilio/voice-callkit-quickstart-objc
- https://github.com/twilio/voice-quickstart-swift
- https://github.com/twilio/voice-quickstart-objc
- https://media.twiliocdn.com/sdk/ios/voice/releases/2.0.0-beta7/docs/

If you enjoyed using this module also try to think ways to make it better.

--

## Installation

Before starting, we recommend you get familiar with [Twilio Programmable Voice SDK](https://www.twilio.com/docs/api/voice-sdk).
It's easier to integrate this module into your react-native app if you follow the Quick start tutorial from Twilio, because it makes very clear which setup steps are required.


```
npm install react-native-twilio-programmable-voice --save
```

## iOS Installation

CallKit

The current iOS portion of this library works through CallKit.  Because of this the call flow is much simpler than on Android as CallKit handles the inbound calls answering, ignoring, or rejecting.
Because of CallKit, the only event listeners present are "connectionDidConnect", "connectionDidDisconnect", and "callRejected."

VoIP Service Certificate

Twilio Programmable Voice for iOS utilizes Apple's VoIP Services and VoIP "Push Notifications" instead of GCM or FCM.  You will need a VoIP Service Certificate from Apple to receive calls.


## Android Installation

Setup GCM or FCM

You must download the file `google-services.json` from the Firebase console.
It contains keys and settings for all your applications under Firebase. This library obtains the resource `senderID` for registering for remote GCM from that file.

**NOTE: To use a specific `play-service-gcm` version, update the `compile` instruction in your App's `android/app/build.gradle` (replace `10.2.0` with the version you prefer):**

```gradle
...

buildscript {
  ...
  dependencies {
    classpath 'com.google.gms:google-services:3.0.0'
  }
}

...

dependencies {
    ...

    compile project(':react-native-twilio-programmable-voice')
    compile ('com.google.android.gms:play-services-gcm:10.2.0') {
        force = true;
    }
}

// this plugin looks for google-services.json in your project
apply plugin: 'com.google.gms.google-services'
```

In your `AndroidManifest.xml`

```xml
    .....

    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.VIBRATE" />


    <application ....>

        ....

        <!-- Twilio Voice -->
        <!-- [START fcm_listener] -->
        <service
            android:name="com.hoxfon.react.TwilioVoice.fcm.VoiceFirebaseMessagingService">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>
        <!-- [END fcm_listener] -->
        <!-- [START instanceId_listener] -->
        <service
            android:name="com.hoxfon.react.TwilioVoice.fcm.VoiceFirebaseInstanceIDService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.android.gms.iid.InstanceID" />
            </intent-filter>
        </service>
        <!-- [END instanceId_listener] -->
        <!-- Twilio Voice -->

     .....

```

In `android/settings.gradle`

```gradle
...

include ':react-native-twilio-programmable-voice'
project(':react-native-twilio-programmable-voice').projectDir = file('../node_modules/react-native-twilio-programmable-voice/android')
```

Register module (in `MainApplication.java`)

```java
import com.hoxfon.react.TwilioVoice.TwilioVoicePackage;  // <--- Import Package

public class MainApplication extends Application implements ReactApplication {

    private final ReactNativeHost mReactNativeHost = new ReactNativeHost(this) {
        @Override
        protected boolean getUseDeveloperSupport() {
            return BuildConfig.DEBUG;
        }

        @Override
        protected List<ReactPackage> getPackages() {
            return Arrays.<ReactPackage>asList(
                new MainReactPackage(),
                new TwilioVoicePackage() // <---- Add the Package
            );
        }
    };
    ....
}
```

## Usage

```javascript
import TwilioVoice from 'react-native-twilio-programmable-voice'
...

// initialize the Programmable Voice SDK passing an access token obtained from the server.

async function initTelephony() {
    try {
        const accessToken = await getAccessTokenFromServer()
        const success = await TwilioVoice.initWithToken(accessToken)
    } catch (err) {
        console.err(err)
    }
}
 // iOS Only
function initTelephonyWithUrl(url) {
    TwilioVoice.initWithTokenUrl(url)

		try{
			TwilioVoice.configureCallKit({
				appName: 'TwilioVoiceExample',  //Required params
				imageName: 'my_image_name_in_bundle', //OPTIONAL
				ringtoneSound: 'my_ringtone_sound_filename_in_bundle' //OPTIONAL
			})
		} catch (err) {
			console.err(err)
		}
}


// add listeners
TwilioVoice.addEventListener('deviceReady', deviceReadyHandler)  // Android Only
TwilioVoice.addEventListener('deviceNotReady', deviceNotReadyHandler)  // Android Only
TwilioVoice.addEventListener('deviceDidReceiveIncoming', deviceDidReceiveIncomingHandler)  // Android Only
TwilioVoice.addEventListener('connectionDidConnect', connectionDidConnectHandler)
TwilioVoice.addEventListener('connectionDidDisconnect', connectionDidDisconnectHandler)
TwilioVoice.addEventListener('callRejected', callRejected)  // iOS Only

...

// start a call
TwilioVoice.connect({To: '+61234567890'})

// hangup
TwilioVoice.disconnect()

// accept an incoming call (Android only)
TwilioVoice.accept()

// reject an incoming call (Android only)
TwilioVoice.reject()

// ignore an incoming call (Android only)
TwilioVoice.ignore()

// mute or un-mute the call
// mutedValue must be a boolean
TwilioVoice.setMuted(mutedValue)

TwilioVoice.sendDigits(digits)

TwilioVoice.requestPermission(GCM_sender_id) // Android only

// should be called after the app is initialized
// to catch incoming call when the app was in the background
// Android Only
TwilioVoice.getActiveCall()
    .then(incomingCall => {
        if (incomingCall){
            _deviceDidReceiveIncoming(incomingCall)
        }
    })

```


## Credits

[voice-quickstart-android](https://github.com/twilio/voice-quickstart-android)

[react-native-push-notification](https://github.com/zo0r/react-native-push-notification)

[voice-callkit-quickstart-objc](https://github.com/twilio/voice-callkit-quickstart-objc)


## License

MIT
