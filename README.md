# react-native-twilio-programmable-voice

This is a React-Native wrapper for [Twilio Programmable Voice SDK](https://www.twilio.com/voice) which lets you make and receive calls from your React-Native App. This module is not affiliated with nor officially maintained by Twilio, and it is maintained by open source contributors.

## Twilio Programmable Voice SDK

- Android 4.5.0 (bundled within the module)
- iOS 5.1.0 (specified by the app's own podfile)

## Breaking changes in v4.0.0

The module implements [react-native autolinking](https://github.com/react-native-community/cli/blob/master/docs/autolinking.md) as many other native libraries > react-native 0.60.0, therefore it doesn't need to be linked manually.

Android: update Firebase Messaging to 17.6.+. Remove the following block from your application's `AndroidManifest.xml` if you are migrating from v3.

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

Android X is supported.

Data passed to the event `deviceDidReceiveIncoming` does not contain the key `call_state`, because state of Call Invites was removed in Twilio Android and iOS SDK v3.0.0

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

iOS application can now receive the following events, that in v3 where only dispatched to Android:

- deviceDidReceiveIncoming
- callInviteCancelled
- callStateRinging
- connectionIsReconnecting
- connectionDidReconnect

## Breaking changes in v3.0.0

- initWitToken returns an object with a property `initialized` instead of `initilized`
- iOS event `connectionDidConnect` returns the same properties as Android
move property `to` => `call_to`
move property `from` => `call_from`

## Installation

Before starting, we recommend you get familiar with [Twilio Programmable Voice SDK](https://www.twilio.com/docs/api/voice-sdk).
It's easier to integrate this module into your react-native app if you follow the Quick start tutorial from Twilio, because it makes very clear which setup steps are required.

```bash
npm install react-native-twilio-programmable-voice --save
```

- **React Native 0.60+**

[CLI autolink feature](https://github.com/react-native-community/cli/blob/master/docs/autolinking.md) links the module while building the app.

- **React Native <= 0.59**

```bash
react-native link react-native-twilio-programmable-voice
```

### iOS Installation

If you can't or don't want to use autolink, you can also manually link the library using the instructions below (click on the arrow to show them):

<details>
<summary>Manually link the library on iOS</summary>

Follow the [instructions in the React Native documentation](https://facebook.github.io/react-native/docs/linking-libraries-ios#manual-linking) to manually link the framework

After you have linked the library with `react-native link react-native-twilio-programmable-voice`
check that `libRNTwilioVoice.a` is present under YOUR_TARGET > Build Phases > Link Binaries With Libraries. If it is not present you can add it using the + sign at the bottom of that list.
</details>

Edit your `Podfile` to include TwilioVoice framework

```ruby
source 'https://github.com/cocoapods/specs'

# min version for TwilioVoice to work
platform :ios, '10.0'

target <YOUR_TARGET> do
    ...
    pod 'TwilioVoice', '~> 5.2.0'
    ...
end
```

```bash
cd ios/ && pod install
```

#### CallKit

The iOS library works through [CallKit](https://developer.apple.com/reference/callkit) and handling calls is much simpler than the  Android implementation as CallKit handles the inbound calls answering, ignoring, or rejecting. Outbound calls must be controlled by custom React-Native screens and controls.

To pass caller's name to CallKit via Voip push notification add custom parameter 'CallerName' to Twilio Dial verb.

```xml
    <Dial>
    <Client>
        <Identity>Client</Identity>
        <Parameter name="CallerName">NAME TO DISPLAY</Parameter>
    </Client>
    </Dial>
```

#### VoIP Service Certificate

Twilio Programmable Voice for iOS utilizes Apple's VoIP Services and VoIP "Push Notifications" instead of FCM. You will need a VoIP Service Certificate from Apple to receive calls. Follow [the official Twilio instructions](https://github.com/twilio/voice-quickstart-ios#7-create-voip-service-certificate) to complete this step.

## Android Installation

Setup FCM

You must download the file `google-services.json` from the Firebase console.
It contains keys and settings for all your applications under Firebase. This library obtains the resource `senderID` for registering for remote GCM from that file.

#### `android/build.gradle`

```groovy
buildscript {
    dependencies {
        // override the google-service version if needed
        // https://developers.google.com/android/guides/google-services-plugin
        classpath 'com.google.gms:google-services:4.3.3'
    }
}

// this plugin looks for google-services.json in your project
apply plugin: 'com.google.gms.google-services'
```

#### `AndroidManifest.xml`

```xml
    <uses-permission android:name="android.permission.VIBRATE" />

    <application ....>
        <!-- Twilio Voice -->
        <!-- [START fcm_listener] -->
        <service
            android:name="com.hoxfon.react.RNTwilioVoice.fcm.VoiceFirebaseMessagingService">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>
        <!-- [END fcm_listener] -->
```

If you can't or don't want to use autolink, you can also manually link the library using the instructions below (click on the arrow to show them):

<details>
<summary>Manually link the library on Android</summary>

Make the following changes:

#### `android/settings.gradle`

```groovy
include ':react-native-twilio-programmable-voice'
project(':react-native-twilio-programmable-voice').projectDir = new File(rootProject.projectDir, '../node_modules/react-native-twilio-programmable-voice/android')
```

#### `android/app/build.gradle`

```groovy
dependencies {
   implementation project(':react-native-twilio-programmable-voice')
}
```

#### `android/app/src/main/.../MainApplication.java`
On top, where imports are:

```java
import com.hoxfon.react.RNTwilioVoice.TwilioVoicePackage;  // <--- Import Package
```

Add the `TwilioVoicePackage` class to your list of exported packages.

```java
@Override
protected List<ReactPackage> getPackages() {
    return Arrays.asList(
            new MainReactPackage(),
            new TwilioVoicePackage()         // <---- Add the package
            // new TwilioVoicePackage(false) // <---- pass false if you don't want to ask for microphone permissions
    );
}
```
</details>

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

function initTelephonyWithToken(token) {
    TwilioVoice.initWithAccessToken(token)

    // iOS only, configure CallKit
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
    //     call_state: 'CONNECTED' | 'ACCEPTED' | 'CONNECTING' | 'RINGING' | 'DISCONNECTED' | 'CANCELLED',
    //     call_from: string, // "+441234567890"
    //     call_to: string,   // "client:bob"
    // }
})
TwilioVoice.addEventListener('connectionIsReconnecting', function(data) {
    // {
    //     call_sid: string,  // Twilio call sid
    //     call_from: string, // "+441234567890"
    //     call_to: string,   // "client:bob"
    // }
})
TwilioVoice.addEventListener('connectionDidReconnect', function(data) {
    // {
    //     call_sid: string,  // Twilio call sid
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
    //         call_state: 'CONNECTED' | 'ACCEPTED' | 'CONNECTING' | 'RINGING' | 'DISCONNECTED' | 'CANCELLED',
    //         call_from: string, // "+441234567890"
    //         call_to: string,   // "client:bob"
    //         err?: string,
    //     }
})
TwilioVoice.addEventListener('callStateRinging', function(data: mixed) {
    //   {
    //       call_sid: string,  // Twilio call sid
    //       call_state: 'CONNECTED' | 'ACCEPTED' | 'CONNECTING' | 'RINGING' | 'DISCONNECTED' | 'CANCELLED',
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
TwilioVoice.hold(holdValue)

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

## Help wanted

There is no need to ask permissions to contribute. Just open an issue or provide a PR. Everybody is welcome to contribute.

ReactNative success is directly linked to its module ecosystem. One way to make an impact is helping contributing to this module or another of the many community lead ones.

![help wanted](images/vjeux_tweet.png "help wanted")

## Twilio Voice SDK reference

[iOS changelog](https://www.twilio.com/docs/voice/voip-sdk/ios/changelog)
[Android changelog](https://www.twilio.com/docs/voice/voip-sdk/android/3x-changelog)

## Credits

[voice-quickstart-android](https://github.com/twilio/voice-quickstart-android)

[voice-quickstart-ios](https://github.com/twilio/voice-quickstart-ios)

[react-native-push-notification](https://github.com/zo0r/react-native-push-notification)

## License

MIT
