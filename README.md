# react-native-twilio-programmable-voice
This is a React Native wrapper for Twilio Programmable Voice SDK that lets you make and receive calls from your ReactNatvie App. This module is not curated nor maintained, but inspired by Twilio.

# Twilio Programmable Voice SDK

- Android 3.3.0 (bundled within this library)
- iOS 2.1.0 (specified by the app's own podfile)

## Breaking changes in v4.0.0

- Android: remove the following block from your application's `AndroidManifest.xml`
```xml
    <!-- [START instanceId_listener] -->
    <service
        android:name="com.hoxfon.react.TwilioVoice.fcm.VoiceFirebaseInstanceIDService"
        android:exported="false">
        <intent-filter>
            <action android:name="com.google.android.gms.iid.InstanceID" />
        </intent-filter>
    </service>
    <!-- [END instanceId_listener] -->
```

Data passed to the event `deviceDidReceiveIncoming` does not contain the key `call_state`, because state of Call Invites was removed in Twilio Android v3.0.0

- iOS: params changes for `connectionDidConnect` and `connectionDidDisconnect`

    to => call_to
    from => call_from
    error => err

New features

Twilio Programmable Voice SDK v3.0.0 handles call invites directly and makes it easy to distinguish a call invites from an active call, which previously was confusing.
To ensure that an active call is displayed when the app comes to foreground you should use the promise `getActiveCall()`.
To ensure that a call invite is displayed when the app comes to foreground use the promise `getCallInvite()`. Please note that call invites don't have a `call_state` field.

You should use `hold()` to put a call on hold.

You can be notified when a call is `ringing` by listening for `callStateRinging` events.

## Breaking changes in v3.0.0

- initWitToken returns an object with a property `initialized` instead of `initilized`
- iOS event `connectionDidConnect` returns the same properties as Android
move property `to` => `call_to`
move property `from` => `call_from`

## Help wanted!

There is no need to ask permissions to contribute. Just open an issue or provide a PR. Everybody is welcome to contribute.

ReactNative success is directly linked to its module ecosystem. One way to make an impact is helping contributing to this module or another of the many community lead ones.

![help wanted](images/vjeux_tweet.png "help wanted")

## Installation

Before starting, we recommend you get familiar with [Twilio Programmable Voice SDK](https://www.twilio.com/docs/api/voice-sdk).
It's easier to integrate this module into your react-native app if you follow the Quick start tutorial from Twilio, because it makes very clear which setup steps are required.


```
npm install react-native-twilio-programmable-voice --save
react-native link react-native-twilio-programmable-voice
```

### iOS Installation - when projects made with react-native init
After you have linked the library with `react-native link react-native-twilio-programmable-voice`
check that `libRNTwilioVoice.a` is present under YOUR_TARGET > Build Phases > Link Binaries With Libraries. If it is not present you can add it using the + sign at the bottom of that list.

Edit your `Podfile` to include TwilioVoice framework

```
source 'https://github.com/cocoapods/specs'

# min version for TwilioVoice to work
platform :ios, '8.1'

target <YOUR_TARGET> do
    ...
    pod 'TwilioVoice', '~> 2.1.0'
    ...
end

```

run `pod install` from inside your project `ios` directory

### iOS Installation - when projects made without react-native init
Edit your `Podfile` to include TwilioVoice and RNTwilioVoice frameworks

```
source 'https://github.com/cocoapods/specs'

# min version for TwilioVoice to work
platform :ios, '8.1'

target <YOUR_TARGET> do
    ...
    pod 'TwilioVoice', '~> 2.1.0'
    pod 'RNTwilioVoice', path: '../node_modules/react-native-twilio-programmable-voice'
    ...
end

```

run `pod install` from inside your project `ios` directory

### CallKit

The current iOS part of this library works through [CallKit](https://developer.apple.com/reference/callkit). Because of this the call flow is much simpler than on Android as CallKit handles the inbound calls answering, ignoring, or rejecting.
Because of CallKit, the only event listeners present are "deviceReady", "connectionDidConnect", "connectionDidDisconnect", and "callRejected".

### VoIP Service Certificate

Twilio Programmable Voice for iOS utilizes Apple's VoIP Services and VoIP "Push Notifications" instead of FCM. You will need a VoIP Service Certificate from Apple to receive calls.


## Android Installation

Setup FCM

You must download the file `google-services.json` from the Firebase console.
It contains keys and settings for all your applications under Firebase. This library obtains the resource `senderID` for registering for remote GCM from that file.

**NOTE: To use a specific `play-service-gcm` version, update the `compile` instruction in your App's `android/app/build.gradle` (replace `10.+` with the version you prefer):**

```gradle
...

buildscript {
  ...
  dependencies {
    classpath 'com.google.gms:google-services:4.2.0'
  }
}

...

dependencies {
    ...

    compile project(':react-native-twilio-programmable-voice')
}

// this plugin looks for google-services.json in your project
apply plugin: 'com.google.gms.google-services'
```

In your `AndroidManifest.xml`

```xml
    .....
    <uses-permission android:name="android.permission.VIBRATE" />


    <application ....>

        ....

        <!-- Twilio Voice -->
        <!-- [START fcm_listener] -->
        <service
            android:name="com.hoxfon.react.RNTwilioVoice.fcm.VoiceFirebaseMessagingService">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>
        <!-- [END fcm_listener] -->
        <!-- [START instanceId_listener] -->
        <service
            android:name="com.hoxfon.react.RNTwilioVoice.fcm.VoiceFirebaseInstanceIDService"
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
import com.hoxfon.react.RNTwilioVoice.TwilioVoicePackage;  // <--- Import Package

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
                new TwilioVoicePackage()         // <---- Add the package
                // new TwilioVoicePackage(false) // <---- pass false if you don't want to ask for microphone permissions
            );
        }
    };
    ....
}
```

## Usage

```javascript
import TwilioVoice from 'react-native-twilio-programmable-voice'

// ...

// initialize the Programmable Voice SDK passing an access token obtained from the server.
// Listen to deviceReady and deviceNotReady events to see whether the initialization succeeded.
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
    try {
        TwilioVoice.configureCallKit({
            appName:       'TwilioVoiceExample',                  // Required param
            imageName:     'my_image_name_in_bundle',             // OPTIONAL
            ringtoneSound: 'my_ringtone_sound_filename_in_bundle' // OPTIONAL
        })
    } catch (err) {
        console.err(err)
    }
}
```

## Events

```javascript
// add listeners (flowtype notation)
TwilioVoice.addEventListener('deviceReady', function() {
    // no data
})
TwilioVoice.addEventListener('deviceNotReady', function(data) {
    // {
    //     err: string
    // }
})
TwilioVoice.addEventListener('connectionDidConnect', function(data) {
    // {
    //     call_sid: string,  // Twilio call sid
    //     call_state: 'PENDING' | 'CONNECTED' | 'ACCEPTED' | 'CONNECTING' | 'RINGING' | 'DISCONNECTED' | 'CANCELLED',
    //     call_from: string, // "+441234567890"
    //     call_to: string,   // "client:bob"
    // }
})
TwilioVoice.addEventListener('connectionDidDisconnect', function(data: mixed) {
    //   | null
    //   | {
    //       err: string
    //     }
    //   | {
    //         call_sid: string,  // Twilio call sid
    //         call_state: 'PENDING' | 'CONNECTED' | 'ACCEPTED' | 'CONNECTING' | 'RINGING' | 'DISCONNECTED' | 'CANCELLED',
    //         call_from: string, // "+441234567890"
    //         call_to: string,   // "client:bob"
    //         err?: string,
    //     }
})
TwilioVoice.addEventListener('callStateRinging', function(data: mixed) {
    //   {
    //       call_sid: string,  // Twilio call sid
    //       call_state: 'PENDING' | 'CONNECTED' | 'ACCEPTED' | 'CONNECTING' | 'RINGING' | 'DISCONNECTED' | 'CANCELLED',
    //       call_from: string, // "+441234567890"
    //       call_to: string,   // "client:bob"
    //   }
})
TwilioVoice.addEventListener('callInviteCancelled', function(data: mixed) {
    //   {
    //       call_sid: string,  // Twilio call sid
    //       call_from: string, // "+441234567890"
    //       call_to: string,   // "client:bob"
    //   }
})

// iOS Only
TwilioVoice.addEventListener('callRejected', function(value: 'callRejected') {})

// Android Only
TwilioVoice.addEventListener('deviceDidReceiveIncoming', function(data) {
    // {
    //     call_sid: string,  // Twilio call sid
    //     call_from: string, // "+441234567890"
    //     call_to: string,   // "client:bob"
    // }
})
// Android Only
TwilioVoice.addEventListener('proximity', function(data) {
    // {
    //     isNear: boolean
    // }
})
// Android Only
TwilioVoice.addEventListener('wiredHeadset', function(data) {
    // {
    //     isPlugged: boolean,
    //     hasMic: boolean,
    //     deviceName: string
    // }
})

// ...

// start a call
TwilioVoice.connect({To: '+61234567890'})

// hangup
TwilioVoice.disconnect()

// accept an incoming call (Android only, in iOS CallKit provides the UI for this)
TwilioVoice.accept()

// reject an incoming call (Android only, in iOS CallKit provides the UI for this)
TwilioVoice.reject()

// ignore an incoming call (Android only)
TwilioVoice.ignore()

// mute or un-mute the call
// mutedValue must be a boolean
TwilioVoice.setMuted(mutedValue)

// put a call on hold
TwilioVoice.setOnHold(holdValue)

// send digits
TwilioVoice.sendDigits(digits)

// Ensure that an active call is displayed when the app comes to foreground
TwilioVoice.getActiveCall()
    .then(activeCall => {
        if (activeCall){
            _displayActiveCall(activeCall)
        }
    })

// Ensure that call invites are displayed when the app comes to foreground
TwilioVoice.getCallInvite()
    .then(callInvite => {
        if (callInvite){
            _handleCallInvite(callInvite)
        }
    })

// Unregister device with Twilio (iOS only)
TwilioVoice.unregister()
```

## Twilio Voice SDK reference

[iOS changelog](https://www.twilio.com/docs/api/voice-sdk/ios/changelog)

[Android changelog](https://www.twilio.com/docs/api/voice-sdk/android/changelog)

## Credits

[voice-quickstart-android](https://github.com/twilio/voice-quickstart-android)

[react-native-push-notification](https://github.com/zo0r/react-native-push-notification)

[voice-quickstart-objc](https://github.com/twilio/voice-quickstart-objc)


## License

MIT
