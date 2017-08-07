import {
    NativeModules,
    NativeEventEmitter,
    Platform,
} from 'react-native'

const TwilioVoice = NativeModules.RNTwilioVoice

const NativeAppEventEmitter = new NativeEventEmitter(TwilioVoice)

const _eventHandlers = {
    deviceReady: new Map(),
    deviceNotReady: new Map(),
    deviceDidReceiveIncoming: new Map(),
    connectionDidConnect: new Map(),
    connectionDidDisconnect: new Map(),
    //iOS specific
    callRejected: new Map(),
}

const Twilio = {
    async initWithToken(token) {
        const result = await TwilioVoice.initWithAccessToken(token)
        if (Platform.OS === 'ios') {
            return {
                // TODO fix the spell of initialized in the next breaking version
                initilized: true,
            }
        }
        return result
    },
    initWithTokenUrl(url) {
        if (Platform.OS === 'ios') {
            TwilioVoice.initWithAccessTokenUrl(url)
        }
    },
    connect(params = {}) {
        TwilioVoice.connect(params)
    },
    disconnect() {
        TwilioVoice.disconnect()
    },
    accept() {
        if (Platform.OS === 'ios') {
            return
        }
        TwilioVoice.accept()
    },
    reject() {
        if (Platform.OS === 'ios') {
            return
        }
        TwilioVoice.reject()
    },
    ignore() {
        if (Platform.OS === 'ios') {
            return
        }
        TwilioVoice.ignore()
    },
    setMuted(isMuted) {
        TwilioVoice.setMuted(isMuted)
    },
    setSpeakerPhone(value) {
        TwilioVoice.setSpeakerPhone(value)
    },
    sendDigits(digits) {
        TwilioVoice.sendDigits(digits)
    },
    requestPermissions(senderId) {
        if (Platform.OS === 'android') {
            TwilioVoice.requestPermissions(senderId)
        }
    },
    getActiveCall() {
        return TwilioVoice.getActiveCall()
    },
    configureCallKit(params = {}) {
        if (Platform.OS === 'ios') {
            TwilioVoice.configureCallKit(params)
        }
    },
    unregister() {
        if (Platform.OS === 'ios') {
            TwilioVoice.unregister()
        }
    },
    addEventListener(type, handler) {
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
