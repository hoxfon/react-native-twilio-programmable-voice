[Release Section](https://github.com/hoxfon/react-native-twilio-programmable-voice/releases)

## 4.3.1

- Android: ensure that intent action is not null

## 4.3.0

- iOS add handler to hold/unhold call

## 4.2.0

- iOS
  - Added ability to pass caller's name to CallKit in push notification

## 4.1.0

- iOS
  - Adds ability to handle DTMF codes in CallKit

## 4.0.2

- iOS
  - fix: always disable proximityMonitor on iOS when the call is disconnected

## 4.0.0

- Android
  - implement new autolinking react native API
  - update Firebase Messaging to 17.3.4 which simplifies how to obtain the FCM token
  - Android X migration
  - use gradle 5.4.1
  - use API 28
  - upgrade com.twilio:voice-android to 4.3.0
  - implement `hold` to hold a call
  - new event `callInviteCancelled`
  - new event `callStateRinging`
  - new method `getCallInvite`
  - implement call ringing Twilio event
  - remove `call_state` from CallInvite
- iOS
  - implement new autolinking react native API
  - update Twilio Voice SDK to v5.2.0
  - remove method `initWithAccessTokenUrl`, please use `initWithAccessToken` instead
  - event parity with Android `deviceDidReceiveIncoming`
  - new event `callInviteCancelled`
  - new event `callStateRinging`
  - new event `connectionIsReconnecting`
  - new event `connectionDidReconnect`
  - convert params for `connectionDidConnect` to => `call_to`, from => `call_from`
  - convert params for `connectionDidDisconnect` to => `call_to`, from => `call_from`, `error` => `err`

- throw an error when listening to events that do not exist

## 3.21.3

- iOS: Upgrade TwilioVoice pod to version 2.1

## 3.21.2

- Switch from rnpm to react-native.config.js

## 3.21.1

- Android: fix crash when asking for microphone permission before an activity is displayed

## 3.21.0

- Android: allow to pass arbitrary parameters to call voice.call() as it is on iOS

## 3.20.1

- iOS: fix crash when callSid is nil for CallInviteCanceled

## 3.20.0

- Android: option to opt out microphone permission request

## 3.19.0

- upgrade com.twilio:voice-android to 2.0.7
- upgrade firebase-messaging to 17.+

## 3.18.1

- Validate token type before calling native module

## 3.18.0

- avoid keeping the screen on when a call is received with a locked device
- remove PowerManager wakelock pattern

## 3.17.0

- disconnect any existing calls when the app is terminated
- Android: Twilio Voice SDK 2.0.6
- iOS: Twilio Voice SDK 2.0.4

## 3.16.0

- Android: Twilio Voice SDK 2.0.5

## 3.15.0

- init notifications channel before showing call in progress notification
- add cocoapods support to install iOS native package

## 3.14.0

- Android: start up the app in fullscreen for incoming calls
- Android: pass call params to disconnect event when ignoring a call
- Android: pass call params to disconnect event when rejecting a call

## 3.13.1

- Android: Twilio Voice SDK 2.0.4

## 3.13.0

- iOS: the library is compatible with Twilio Voice SDK 2.0.2

## 3.12.0

- iOS: the library is compatible with Twilio Voice SDK 2.0.0-beta21
- iOS: handle events when a call is put on hold

## 3.11.0

- iOS: the library is compatible with Twilio Voice SDK 2.0.0-beta20

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
- iOS: Twilio Voice SDK 2.0.0-beta11

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
