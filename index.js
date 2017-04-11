import {
	NativeModules,
	NativeEventEmitter,
	Platform
} from 'react-native'

const TwilioVoice = NativeModules.TwilioVoice

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
	initWithToken(token) {
		return TwilioVoice.initWithAccessToken(token)
	},
	initWithTokenUrl(url) {
		if (Platform.OS !== 'ios') return
		TwilioVoice.initWithAccessTokenUrl(url)
	},
	connect(params = {}) {
		TwilioVoice.connect(params)
	},
	disconnect() {
		TwilioVoice.disconnect()
	},
	accept() {
		if (Platform.OS !== 'android') return
		TwilioVoice.accept()
	},
	reject() {
		if (Platform.OS !== 'android') return
		TwilioVoice.reject()
	},
	ignore() {
		if (Platform.OS !== 'android') return
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
		if (Platform.OS !== 'android') return
		TwilioVoice.requestPermissions(senderId)
	},
	getIncomingCall() {
		if (Platform.OS !== 'android') return
		return TwilioVoice.getIncomingCall()
	},
	configureCallKit(params = {}) {
		if (Platform.OS !== 'ios') return
		TwilioVoice.configureCallKit(params)
	},
	unregister() {
		if (Platform.OS !== 'ios') return
		TwilioVoice.unregister()
	},
	addEventListener(type, handler) {
		if (_eventHandlers[type].has(handler)) return
		_eventHandlers[type].set(handler, NativeAppEventEmitter.addListener(type, rtn => { handler(rtn) }))
	},
	removeEventListener(type, handler) {
		if (!_eventHandlers[type].has(handler)) return
		_eventHandlers[type].get(handler).remove()
		_eventHandlers[type].delete(handler)
	}
}

export default Twilio
