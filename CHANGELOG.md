[Release Section](https://github.com/hoxfon/react-native-twilio-programmable-voice/releases)

## 3.10.0
- fix crash on Oreo abandonAudioFocusRequest()

## 3.9.0
- update com.google.gms:google-services to 3.1.2
- use latest API 26 support library

## 3.8.0
- Android: Twilio Voice SDK 2.0.2

## 3.7.0
- Android: use proximity sensor to lock screen during calls
- Android: send event to JavaScript for headset plugged in
- Android: fix unset audio focus for Android O

## 3.6.0
- Android: Twilio Voice SDK 2.0.0-beta24
- Implement Android O notification channels
- Android: ensure that audio settings are set back to normal when the app destroys
- Android:fix audio settings when set speakers on/off
- Android: prevent other apps to emit sound when a call is in progress

## 3.5.0
- Android: Twilio Voice SDK 2.0.0-beta20
- Implement Call.Listener onConnectFailure()

## 3.4.0
- Fix iOS HEADER_SEARCH_PATHS

## 3.3.0
- Android: Twilio Voice SDK 2.0.0-beta18
- Adapt setAudioFocus() for Android O

## 3.2.0
- Android: add compatibility for react native >= 0.47

## 3.1.1
- iOS: ensure the proximity sensor is enabled when starting a call

## 3.1.0
Make the iOS initialization process the same as Android
- iOS: call event `deviceReady` only when the accessToken registration is successful
- iOS: implement event `deviceNotReady` called when the accessToken registration is not successful

## 3.0.0
Breaking changes:
- initWitToken returns an object with a property `initialized` instead of `initilized`
- iOS event `connectionDidConnect` returns the same properties as Android
move property `to` => `call_to`
move property `from` => `call_from`

New iOS
- iOS: the library is compatible with Twilio Voice SDK 2.0.0-beta15
- iOS use CallKit reportOutgoingCallWithUUID when initializing calls

New Android
- add properties `call_to` and `call_from` to Android event `connectionDidConnect`

## 2.11.2
- Make sure CallKit session is ended when the call is terminated by the callee - @SimonRobinson

## 2.11.1
- Make sure CallKit session is ended on fail - @Pagebakers

## 2.11.0
- Android: Twilio Voice SDK 2.0.0-beta17

## 2.10.0
- Android: Twilio Voice SDK 2.0.0-beta16

## 2.9.0
- make sure the Android build uses the latest version 10 of firebase.messaging to avoid dependencies conflicts crashes

## 2.8.0
- iOS: prevent CallKit to be initialised more than once

## 2.7.0
- iOS: correct handling of calls disconnection

## 2.6.0
- iOS: implementing getActiveCall()

## 2.5.2
- iOS: initWithToken() now returns the same value as Android

## 2.5.1
- iOS: handle call failure and pass to JS the most descriptive error

## 2.5.0
- iOS: Twilio Voice SDK 2.0.0-beta13

## 2.4.0
- Android: Twilio Voice SDK 2.0.0-beta15
- use buildToolsVersion "25.0.2"
- use targetSdkVersion 25
- fix registerActionReceiver() called twice
- fix reject() intent not sending CONNECTION_STOP to JavaScript
- fix ingore() not not sending CONNECTION_STOP to JavaScript when there is not activeInviteCall

## 2.3.2
- Android: Twilio Voice SDK 2.0.0-beta14

## 2.3.1
- iOS: call TwilioVoice audioSessionDeactivated on didDeactivateAudioSession
- iOS: performEndCallActionWithUUID when call is disconnected from the app

## 2.3.0
- Android: Twilio Voice SDK 2.0.0-beta13
- iOS:     Twilio Voice SDK 2.0.0-beta11

## 2.2.0
- iOS: Twilio Voice SDK 2.0.0-beta10

## 2.1.0
- Android: Twilio Voice SDK 2.0.0-beta11

## 2.0.2
- Android: fix library for RN 0.45.1

## 2.0.1
- ios: send connectionDidDisconnect when the call invite terminates

## 2.0.0
- ios implementation with CallKit

## 1.1.0
- use Twilio Voice SDK 2.0.0-beta8

## 1.0.1
- Android: use incoming call notification full screen

## 1.0.0
- use Twilio beta 5
- removed requestPermissions, react-native API should be used instead
- renamed getIncomingCall() to getActiveCall()
- set the audio of the call as MODE_IN_COMMUNICATION

## 0.6.3
- fix crash when activityManager is null
- add call_from and call_to to the event connectionDidDisconnect

## 0.6.2
- Android: fix. Clear callInvite when the caller hangs up

## 0.6.1
- Android: fix gradle import beta4

## 0.6.0
- Android: use Twilio Voice SDK Beta4

## 0.5.5
- improve logic for starting the MainActivity when receiving a call. The Intent flags depends on the App importance (fixes the 0.5.3 for Android 6.0)
- make sure all wakelock are released after being acquired, and the keyguard re-enabled

## 0.5.4
- set incoming call Intent flag depending on App importance (App status)

## 0.5.3
- Prevent incoming call from starting a new task: use (Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP) for the Intent
- Prevent the incoming call Intent to be broadcast when the app is in the foreground

## 0.5.2
- allow custom notification icons

## 0.5.1
- fix showing incoming call
- add pendingIntent to clear missed calls number
- prevent double incoming call cancelled message

## 0.5.0

- start activity as soon as a call notification arrives
- wakes up the device when a call arrives
- use ringtone, removing notification sound
- handle gracefully when a call is accepted twice
- don't create a missed calls when the call is rejected or ignored manually

## 0.4.1

- simplify life-cycle of local hangup notification
- remove crash on disconnect

## 0.4.0

- add notification for missed calls
- add chronometer for ongoing calls
- use FLAG_UPDATE_CURRENT rather than FLAG_ONE_SHOT for pending intents

## 0.3.5

- add protections from null callSid

## 0.3.4

- fix wrong boolean, from the previous refactoring

## 0.3.3

- add null checks on all `call` variables before invoking getCallSid()
- remove unused import
- follow Android Studio linting instructions

## 0.3.2

- fix typo in setMuted()

## 0.3.1

- avoid registering the same event listener multiple times

## 0.3.0

- Check if Google Play Services are available before initialising Twilio for receiving calls.
- Method initWithToken returns a Promise to let the application know if the initialisation did succeed.


## 0.2.2

- fix the instruction to setup the `AndroidManifest.xml` file
