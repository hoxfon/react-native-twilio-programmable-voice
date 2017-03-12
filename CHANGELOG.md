[Release Section](https://github.com/hoxfon/react-native-twilio-programmable-voice/releases)

## 0.4.1

- simplify lifecycle of local hangup notification
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
