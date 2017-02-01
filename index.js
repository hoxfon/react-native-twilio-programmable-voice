import {
    NativeModules,
    NativeEventEmitter
} from 'react-native'

const TwilioVoice = NativeModules.TwilioVoice

const NativeAppEventEmitter = new NativeEventEmitter(TwilioVoice)

const _eventHandlers = {
    deviceReady: new Map(),
    deviceNotReady: new Map(),
    deviceDidReceiveIncoming: new Map(),
    connectionDidConnect: new Map(),
    connectionDidDisconnect: new Map(),
};

const Twilio = {
    initWithToken(token) {
        return TwilioVoice.initWithAccessToken(token)
    },
    connect(params = {}) {
        TwilioVoice.connect(params)
    },
    disconnect() {
        TwilioVoice.disconnect()
    },
    accept() {
        TwilioVoice.accept()
    },
    reject() {
        TwilioVoice.reject()
    },
    ignore() {
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
        TwilioVoice.requestPermissions(senderId)
    },
    getIncomingCall() {
        return TwilioVoice.getIncomingCall()
    },
    addEventListener (type, handler) {
        if (_eventHandlers[type].has(handler)) {
            return
        }
        _eventHandlers[type].set(handler, NativeAppEventEmitter.addListener(
            type, (rtn) => {
                handler(rtn)
            }
        ))
    },
    removeEventListener (type, handler) {
        if (!_eventHandlers[type].has(handler)) {
            return
        }
        _eventHandlers[type].get(handler).remove()
        _eventHandlers[type].delete(handler)
    }
}

export default Twilio
