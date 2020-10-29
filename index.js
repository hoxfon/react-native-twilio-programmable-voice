import {
    NativeModules,
    NativeEventEmitter,
    Platform,
} from 'react-native'

import uuid from 'uuid'

import RNCallKeep from 'react-native-callkeep'
console.log('### RNCallKeep', RNCallKeep)
const ANDROID = 'android'
const IOS = 'ios'

const TwilioVoice = NativeModules.RNTwilioVoice

const NativeAppEventEmitter = new NativeEventEmitter(TwilioVoice)

const getNewUuid = () => uuid.v4().toLowerCase();

const _eventHandlers = {
    deviceReady: new Map(),
    deviceNotReady: new Map(),
    deviceDidReceiveIncoming: new Map(),
    connectionDidConnect: new Map(),
    connectionIsReconnecting: new Map(),
    connectionDidReconnect: new Map(),
    connectionDidDisconnect: new Map(),
    callStateRinging: new Map(),
    callInviteCancelled: new Map(),
    callRejected: new Map(),
}

const Twilio = {
    // initialize the library with Twilio access token
    // return {initialized: true} when the initialization started
    // Listen to deviceReady and deviceNotReady events to see whether
    // the initialization succeeded
    async initWithToken(token) {
        RNCallKeep.setup({
            ios: {
                appName: 'Hoxfon',
            },
            android: {
                alertTitle: 'Permissions required',
                alertDescription: 'This application needs to access your phone accounts',
                cancelButton: 'Cancel',
                okButton: 'ok',
            },
        })
        if (typeof token !== 'string') {
            return {
                initialized: false,
                err:         'Invalid token, token must be a string'
            }
        };

        const result = await TwilioVoice.initWithAccessToken(token)
        if (Platform.OS === ANDROID && result && result.initialized) {
            RNCallKeep.setAvailable(true)
        }
        // native react promise present only for Android
        // iOS initWithAccessToken doesn't return
        if (Platform.OS === IOS) {
            return {
                initialized: true,
            }
        }
        return result
    },
    connect(params = {}) {
        TwilioVoice.connect(params)
        const callUUID = getNewUuid()
        if (Platform.OS === ANDROID) {
            RNCallKeep.startCall(callUUID, params.To, "Paul Smith")
        }
        // TODO iOS
    },
    disconnect() {
        TwilioVoice.disconnect()
        if (Platform.OS === ANDROID) {
            RNCallKeep.setAvailable(false)
        }
    },
    accept() {
        if (Platform.OS === IOS) {
            return
        }
        TwilioVoice.accept()
    },
    reject() {
        if (Platform.OS === IOS) {
            return
        }
        TwilioVoice.reject()
    },
    ignore() {
        if (Platform.OS === IOS) {
            return
        }
        TwilioVoice.ignore()
    },
    setMuted: TwilioVoice.setMuted,
    setSpeakerPhone: TwilioVoice.setSpeakerPhone,
    sendDigits: TwilioVoice.sendDigits,
    hold: TwilioVoice.hold,
    requestPermissions(senderId) {
        if (Platform.OS === ANDROID) {
            TwilioVoice.requestPermissions(senderId)
        }
    },
    getActiveCall: TwilioVoice.getActiveCall,
    getCallInvite: TwilioVoice.getCallInvite,
    configureCallKit(params = {}) {
        if (Platform.OS === IOS) {
            TwilioVoice.configureCallKit(params)
        }
    },
    unregister() {
        if (Platform.OS === IOS) {
            TwilioVoice.unregister()
        }
    },
    addEventListener(type, handler) {
        if (!_eventHandlers.hasOwnProperty(type)) {
            throw new Error('Event handler not found: ' + type)
        }
        if (_eventHandlers[type])
        if (_eventHandlers[type].has(handler)) {
            return
        }
        _eventHandlers[type].set(handler, NativeAppEventEmitter.addListener(type, rtn => { handler(rtn) }))
    },
    removeEventListener(type, handler) {
        if (!_eventHandlers[type].has(handler)) {
            return
        }
        _eventHandlers[type].get(handler).remove()
        _eventHandlers[type].delete(handler)
    }
}

export default Twilio
