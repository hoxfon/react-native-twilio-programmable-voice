//
//  ViewController.m
//  Twilio Voice with Quickstart - Objective-C
//
//  Copyright © 2016-2018 Twilio, Inc. All rights reserved.
//

#import "RNTwilioVoice.h"
#import <React/RCTLog.h>

@import AVFoundation;
@import PushKit;
@import CallKit;
@import TwilioVoice;

@interface RNTwilioVoice () <PKPushRegistryDelegate, TVONotificationDelegate, TVOCallDelegate, CXProviderDelegate, UITextFieldDelegate, AVAudioPlayerDelegate>

@property (nonatomic, strong) NSString *deviceTokenString;

@property (nonatomic, strong) PKPushRegistry *voipRegistry;
@property (nonatomic, strong) void(^incomingPushCompletionCallback)(void);
@property (nonatomic, strong) void(^callKitCompletionCallback)(BOOL);
@property (nonatomic, strong) TVODefaultAudioDevice *audioDevice;
@property (nonatomic, strong) NSMutableDictionary *activeCallInvites;
@property (nonatomic, strong) NSMutableDictionary *activeCalls;

// activeCall represents the last connected call
@property (nonatomic, strong) TVOCallInvite *activeCallInvite;
@property (nonatomic, strong) TVOCall *activeCall;

@property (nonatomic, strong) CXProvider *callKitProvider;
@property (nonatomic, strong) CXCallController *callKitCallController;
@property (nonatomic, assign) BOOL userInitiatedDisconnect;

@property (nonatomic, assign) BOOL playCustomRingback;
@property (nonatomic, strong) AVAudioPlayer *ringtonePlayer;

@end

@implementation RNTwilioVoice {
  NSMutableDictionary *_settings;
  NSMutableDictionary *_callParams;
  NSString *_tokenUrl;
  NSString *_token;
}

NSString * const StatePending = @"PENDING";
NSString * const StateConnecting = @"CONNECTING";
NSString * const StateConnected = @"CONNECTED";
NSString * const StateDisconnected = @"DISCONNECTED";
NSString * const StateRejected = @"REJECTED";

- (dispatch_queue_t)methodQueue
{
  return dispatch_get_main_queue();
}

RCT_EXPORT_MODULE()

- (NSArray<NSString *> *)supportedEvents
{
  return @[@"connectionDidConnect", @"connectionDidDisconnect", @"callRejected", @"deviceReady", @"deviceNotReady"];
}

@synthesize bridge = _bridge;

- (instancetype)init
{
  self = [super init];
    
   /*
    * The important thing to remember when providing a TVOAudioDevice is that the device must be set
    * before performing any other actions with the SDK (such as connecting a Call, or accepting an incoming Call).
    * In this case we've already initialized our own `TVODefaultAudioDevice` instance which we will now set.
    */
  self.audioDevice = [TVODefaultAudioDevice audioDevice];
  TwilioVoice.audioDevice = self.audioDevice;

  return self;
}

- (void)initPushRegistry {

  self.voipRegistry = [[PKPushRegistry alloc] initWithQueue:dispatch_get_main_queue()];
  self.voipRegistry.delegate = self;
  self.voipRegistry.desiredPushTypes = [NSSet setWithObject:PKPushTypeVoIP];
  [self configureCallKit];
}

- (void)configureCallKit {
    CXProviderConfiguration *configuration = [[CXProviderConfiguration alloc] initWithLocalizedName:params[@"appName"]];
    configuration.maximumCallGroups = 1;
    configuration.maximumCallsPerCallGroup = 1;

    if (_settings[@"imageName"]) {
      configuration.iconTemplateImageData = UIImagePNGRepresentation([UIImage imageNamed:_settings[@"imageName"]]);
    }
    if (_settings[@"ringtoneSound"]) {
      configuration.ringtoneSound = _settings[@"ringtoneSound"];
    }

    _callKitProvider = [[CXProvider alloc] initWithConfiguration:configuration];
    [_callKitProvider setDelegate:self queue:nil];

    _callKitCallController = [[CXCallController alloc] init];

    NSLog(@"RNTWilioVoice CallKit Initialized");
    
    [self sendEventWithName:@"deviceReady" body:nil];
}

- (void)dealloc {
    if (self.callKitProvider) {
        [self.callKitProvider invalidate];
    }
}

- (NSString *)fetchAccessToken {
//    NSString *accessTokenEndpointWithIdentity = [NSString stringWithFormat:@"%@?identity=%@", kAccessTokenEndpoint, kIdentity];
//    NSString *accessTokenURLString = [kYourServerBaseURLString stringByAppendingString:accessTokenEndpointWithIdentity];

  if (_tokenUrl) {
    NSString *accessToken = [NSString stringWithContentsOfURL:[NSURL URLWithString:accessTokenURLString]
                                                     encoding:NSUTF8StringEncoding
                                                        error:nil];
    return accessToken;
  } else {
    return _token;
  }
    
}

- (void)checkRecordPermission:(void(^)(BOOL permissionGranted))completion {
    AVAudioSessionRecordPermission permissionStatus = [[AVAudioSession sharedInstance] recordPermission];
    switch (permissionStatus) {
        case AVAudioSessionRecordPermissionGranted:
            // Record permission already granted.
            completion(YES);
            break;
        case AVAudioSessionRecordPermissionDenied:
            // Record permission denied.
            completion(NO);
            break;
        case AVAudioSessionRecordPermissionUndetermined:
        {
            // Requesting record permission.
            // Optional: pop up app dialog to let the users know if they want to request.
            [[AVAudioSession sharedInstance] requestRecordPermission:^(BOOL granted) {
                completion(granted);
            }];
            break;
        }
        default:
            completion(NO);
            break;
    }
}

#pragma mark - PKPushRegistryDelegate
- (void)pushRegistry:(PKPushRegistry *)registry didUpdatePushCredentials:(PKPushCredentials *)credentials forType:(NSString *)type {
    NSLog(@"RNTwilioVoice pushRegistry:didUpdatePushCredentials:forType:");

    if ([type isEqualToString:PKPushTypeVoIP]) {
        const unsigned *tokenBytes = [credentials.token bytes];
        self.deviceTokenString = [NSString stringWithFormat:@"<%08x %08x %08x %08x %08x %08x %08x %08x>",
                                  ntohl(tokenBytes[0]), ntohl(tokenBytes[1]), ntohl(tokenBytes[2]),
                                  ntohl(tokenBytes[3]), ntohl(tokenBytes[4]), ntohl(tokenBytes[5]),
                                  ntohl(tokenBytes[6]), ntohl(tokenBytes[7])];
        NSString *accessToken = [self fetchAccessToken];

        [TwilioVoice registerWithAccessToken:accessToken
                                deviceToken:self.deviceTokenString
                                completion:^(NSError *error) {
          if (error) {
            NSLog(@"RNTwilioVoice An error occurred while registering: %@", [error localizedDescription]);
            [self sendEventWithName:@"deviceNotReady" body:params];
          } else {
            NSLog(@"RNTwilioVoice Successfully registered for VoIP push notifications.");
            [self sendEventWithName:@"deviceReady" body:nil];
          }
         }];
    }
}

- (void)pushRegistry:(PKPushRegistry *)registry didInvalidatePushTokenForType:(PKPushType)type {
    NSLog(@"RNTwilioVoice pushRegistry:didInvalidatePushTokenForType:");

    if ([type isEqualToString:PKPushTypeVoIP]) {
        NSString *accessToken = [self fetchAccessToken];

        [TwilioVoice unregisterWithAccessToken:accessToken
                                   deviceToken:self.deviceTokenString
                                    completion:^(NSError * _Nullable error) {
            if (error) {
                NSLog(@"RNTwilioVoice An error occurred while unregistering: %@", [error localizedDescription]);
            }
            else {
                NSLog(@"RNTwilioVoice Successfully unregistered for VoIP push notifications.");
            }
        }];

        self.deviceTokenString = nil;
    }
}

/**
 * Try using the `pushRegistry:didReceiveIncomingPushWithPayload:forType:withCompletionHandler:` method if
 * your application is targeting iOS 11. According to the docs, this delegate method is deprecated by Apple.
 */
- (void)pushRegistry:(PKPushRegistry *)registry didReceiveIncomingPushWithPayload:(PKPushPayload *)payload forType:(NSString *)type {
    NSLog(@"RNTwilioVoice pushRegistry:didReceiveIncomingPushWithPayload:forType:");
    if ([type isEqualToString:PKPushTypeVoIP]) {
        
        // The Voice SDK will use main queue to invoke `cancelledCallInviteReceived:error` when delegate queue is not passed
        if (![TwilioVoice handleNotification:payload.dictionaryPayload delegate:self delegateQueue:nil]) {
            NSLog(@"RNTwilioVoice This is not a valid Twilio Voice notification.");
        }
    }
}

/**
 * This delegate method is available on iOS 11 and above. Call the completion handler once the
 * notification payload is passed to the `TwilioVoice.handleNotification()` method.
 */
- (void)pushRegistry:(PKPushRegistry *)registry
didReceiveIncomingPushWithPayload:(PKPushPayload *)payload
             forType:(PKPushType)type
withCompletionHandler:(void (^)(void))completion {
    NSLog(@"RNTwilioVoice pushRegistry:didReceiveIncomingPushWithPayload:forType:withCompletionHandler:");

    // Save for later when the notification is properly handled.
    self.incomingPushCompletionCallback = completion;

    
    if ([type isEqualToString:PKPushTypeVoIP]) {
        // The Voice SDK will use main queue to invoke `cancelledCallInviteReceived:error` when delegate queue is not passed
        if (![TwilioVoice handleNotification:payload.dictionaryPayload delegate:self delegateQueue:nil]) {
            NSLog(@"RNTwilioVoice This is not a valid Twilio Voice notification.");
        }
    }
    if ([[NSProcessInfo processInfo] operatingSystemVersion].majorVersion < 13) {
        // Save for later when the notification is properly handled.
        self.incomingPushCompletionCallback = completion;
    } else {
        /**
        * The Voice SDK processes the call notification and returns the call invite synchronously. Report the incoming call to
        * CallKit and fulfill the completion before exiting this callback method.
        */
        completion();
    }
}

- (void)incomingPushHandled {
    if (self.incomingPushCompletionCallback) {
        self.incomingPushCompletionCallback();
        self.incomingPushCompletionCallback = nil;
    }
}

#pragma mark - TVONotificationDelegate
- (void)callInviteReceived:(TVOCallInvite *)callInvite {
    
    /**
     * Calling `[TwilioVoice handleNotification:delegate:]` will synchronously process your notification payload and
     * provide you a `TVOCallInvite` object. Report the incoming call to CallKit upon receiving this callback.
     */

    NSLog(@"RNTwilioVoice callInviteReceived:");
    
    // Always report to CallKit
    [self reportIncomingCallFrom:from withUUID:callInvite.uuid];
    self.activeCallInvites[[callInvite.uuid UUIDString]] = callInvite;
    self.activeCallInvite = callInvite;
    if ([[NSProcessInfo processInfo] operatingSystemVersion].majorVersion < 13) {
        [self incomingPushHandled];
    }
}

- (void)cancelledCallInviteReceived:(TVOCancelledCallInvite *)cancelledCallInvite error:(NSError *)error {
    
    /**
     * The SDK may call `[TVONotificationDelegate callInviteReceived:error:]` asynchronously on the dispatch queue
     * with a `TVOCancelledCallInvite` if the caller hangs up or the client encounters any other error before the called
     * party could answer or reject the call.
     */
    
    NSLog(@"RNTwilioVoice cancelledCallInviteReceived:");
    
    TVOCallInvite *callInvite;
    for (NSString *uuid in self.activeCallInvites) {
        TVOCallInvite *activeCallInvite = [self.activeCallInvites objectForKey:uuid];
        if ([cancelledCallInvite.callSid isEqualToString:activeCallInvite.callSid]) {
            callInvite = activeCallInvite;
            break;
        }
    }

    NSMutableDictionary *params = [[NSMutableDictionary alloc] init];
    NSString* errMsg = [error localizedDescription];
    if (error.localizedFailureReason) {
      errMsg = [error localizedFailureReason];
    }
    [params setObject:errMsg forKey:@"error"];
    if (callInvite.sid) {
      [params setObject:callInvite.sid forKey:@"call_sid"];
    }
    if (callInvite.to){
      [params setObject:callInvite.to forKey:@"call_to"];
    }
    if (callInvite.from){
      [params setObject:callInvite.from forKey:@"call_from"];
    }
    if (callInvite.state == TVOCallStateDisconnected) {
      [params setObject:StateDisconnected forKey:@"call_state"];
    }
    [self sendEventWithName:@"connectionDidDisconnect" body:params];
    
    if (callInvite) {
        [self performEndCallActionWithUUID:callInvite.uuid];
    }
}

#pragma mark - TVOCallDelegate
- (void)callDidStartRinging:(TVOCall *)call {
    NSLog(@"RNTwilioVoice callDidStartRinging:");
    
    /*
     When [answerOnBridge](https://www.twilio.com/docs/voice/twiml/dial#answeronbridge) is enabled in the
     <Dial> TwiML verb, the caller will not hear the ringback while the call is ringing and awaiting to be
     accepted on the callee's side. The application can use the `AVAudioPlayer` to play custom audio files
     between the `[TVOCallDelegate callDidStartRinging:]` and the `[TVOCallDelegate callDidConnect:]` callbacks.
     */
    //if (self.playCustomRingback) {
    //    [self playRingback];
    //}
}

- (void)callDidConnect:(TVOCall *)call {
    NSLog(@"RNTwilioVoice callDidConnect:");
    
   /* if (self.playCustomRingback) {
        [self stopRingback];
    }*/

    self.callKitCompletionCallback(YES);

    NSMutableDictionary *callParams = [[NSMutableDictionary alloc] init];
    [callParams setObject:call.sid forKey:@"call_sid"];
    if (call.state == TVOCallStateConnecting) {
      [callParams setObject:StateConnecting forKey:@"call_state"];
    } else if (call.state == TVOCallStateConnected) {
      [callParams setObject:StateConnected forKey:@"call_state"];
    }

    if (call.from){
      [callParams setObject:call.from forKey:@"from"];
    }
    if (call.to){
      [callParams setObject:call.to forKey:@"to"];
    }
    [self sendEventWithName:@"connectionDidConnect" body:callParams];

    [self toggleAudioRoute:YES];
}

- (void)call:(TVOCall *)call isReconnectingWithError:(NSError *)error {
    NSLog(@"RNTwilioVoice Call is reconnecting");
}

- (void)callDidReconnect:(TVOCall *)call {
    NSLog(@"RNTwilioVoice Call reconnected");
}

- (void)call:(TVOCall *)call didFailToConnectWithError:(NSError *)error {
    NSLog(@"RNTwilioVoice Call failed to connect: %@", error);
    
    self.callKitCompletionCallback(NO);
    [self performEndCallActionWithUUID:call.uuid];
    [self callDisconnected:call];
}

- (void)call:(TVOCall *)call didDisconnectWithError:(NSError *)error {
    if (error) {
        NSLog(@"RNTwilioVoice Call failed: %@", error);
    } else {
        NSLog(@"RNTwilioVoice Call disconnected");
    }

    NSMutableDictionary *params = [[NSMutableDictionary alloc] init];
    NSString* errMsg = [error localizedDescription];
    if (error.localizedFailureReason) {
      errMsg = [error localizedFailureReason];
    }
    [params setObject:errMsg forKey:@"error"];
    if (self.call.sid) {
      [params setObject:self.call.sid forKey:@"call_sid"];
    }
    if (self.call.to){
      [params setObject:self.call.to forKey:@"call_to"];
    }
    if (self.call.from){
      [params setObject:self.call.from forKey:@"call_from"];
    }
    if (self.call.state == TVOCallStateDisconnected) {
      [params setObject:StateDisconnected forKey:@"call_state"];
    }
    [self sendEventWithName:@"connectionDidDisconnect" body:params];

    if (!self.userInitiatedDisconnect) {
        CXCallEndedReason reason = CXCallEndedReasonRemoteEnded;
        if (error) {
            reason = CXCallEndedReasonFailed;
        }
        
        [self.callKitProvider reportCallWithUUID:call.uuid endedAtDate:[NSDate date] reason:reason];
    }

    [self callDisconnected:call];
}

- (void)callDisconnected:(TVOCall *)call {
    if ([call isEqual:self.activeCall]) {
        self.activeCall = nil;
    }
    [self.activeCalls removeObjectForKey:call.uuid.UUIDString];
    
    self.userInitiatedDisconnect = NO;
}

#pragma mark - AVAudioSession
- (void)toggleAudioRoute:(BOOL)toSpeaker {
    // The mode set by the Voice SDK is "VoiceChat" so the default audio route is the built-in receiver. Use port override to switch the route.
    self.audioDevice.block =  ^ {
        // We will execute `kDefaultAVAudioSessionConfigurationBlock` first.
        kTVODefaultAVAudioSessionConfigurationBlock();
        
        // Overwrite the audio route
        AVAudioSession *session = [AVAudioSession sharedInstance];
        NSError *error = nil;
        if (toSpeaker) {
            if (![session overrideOutputAudioPort:AVAudioSessionPortOverrideSpeaker error:&error]) {
                NSLog(@"Unable to reroute audio: %@", [error localizedDescription]);
            }
        } else {
            if (![session overrideOutputAudioPort:AVAudioSessionPortOverrideNone error:&error]) {
                NSLog(@"Unable to reroute audio: %@", [error localizedDescription]);
            }
        }
    };
    self.audioDevice.block();
}

#pragma mark - CXProviderDelegate
- (void)providerDidReset:(CXProvider *)provider {
    NSLog(@"providerDidReset:");
    self.audioDevice.enabled = YES;
}

- (void)providerDidBegin:(CXProvider *)provider {
    NSLog(@"providerDidBegin:");
}

- (void)provider:(CXProvider *)provider didActivateAudioSession:(AVAudioSession *)audioSession {
    NSLog(@"provider:didActivateAudioSession:");
    self.audioDevice.enabled = YES;
}

- (void)provider:(CXProvider *)provider didDeactivateAudioSession:(AVAudioSession *)audioSession {
    NSLog(@"provider:didDeactivateAudioSession:");
}

- (void)provider:(CXProvider *)provider timedOutPerformingAction:(CXAction *)action {
    NSLog(@"provider:timedOutPerformingAction:");
}

- (void)provider:(CXProvider *)provider performStartCallAction:(CXStartCallAction *)action {
    NSLog(@"provider:performStartCallAction:");
    
    [self toggleUIState:NO showCallControl:NO];
    [self startSpin];

    self.audioDevice.enabled = NO;
    self.audioDevice.block();
    
    [self.callKitProvider reportOutgoingCallWithUUID:action.callUUID startedConnectingAtDate:[NSDate date]];
    
    __weak typeof(self) weakSelf = self;
    [self performVoiceCallWithUUID:action.callUUID client:nil completion:^(BOOL success) {
        __strong typeof(self) strongSelf = weakSelf;
        if (success) {
            [strongSelf.callKitProvider reportOutgoingCallWithUUID:action.callUUID connectedAtDate:[NSDate date]];
            [action fulfill];
        } else {
            [action fail];
        }
    }];
}

- (void)provider:(CXProvider *)provider performAnswerCallAction:(CXAnswerCallAction *)action {
    NSLog(@"provider:performAnswerCallAction:");
    
    self.audioDevice.enabled = NO;
    self.audioDevice.block();
    
    [self performAnswerVoiceCallWithUUID:action.callUUID completion:^(BOOL success) {
        if (success) {
            [action fulfill];
        } else {
            [action fail];
        }
    }];
    
    [action fulfill];
}

- (void)provider:(CXProvider *)provider performEndCallAction:(CXEndCallAction *)action {
    NSLog(@"RNTwilioVoice provider:performEndCallAction:");
    
    TVOCallInvite *callInvite = self.activeCallInvites[action.callUUID.UUIDString];
    TVOCall *call = self.activeCalls[action.callUUID.UUIDString];

    if (callInvite) {
        [callInvite reject];
        [self.activeCallInvites removeObjectForKey:callInvite.uuid.UUIDString];
        self.activeCallInvite = nil;
    } else if (call) {
        [call disconnect];
    } else {
        NSLog(@"RNTwilioVoice Unknown UUID to perform end-call action with");
    }

    [action fulfill];
}

- (void)provider:(CXProvider *)provider performSetHeldCallAction:(CXSetHeldCallAction *)action {
    NSLog(@"RNTwilioVoice provider:performSetHeldCallAction:");
    
    TVOCall *call = self.activeCalls[action.callUUID.UUIDString];
    if (call) {
        [call setOnHold:action.isOnHold];
        [action fulfill];
    } else {
        [action fail];
    }
}

- (void)provider:(CXProvider *)provider performSetMutedCallAction:(CXSetMutedCallAction *)action {
    NSLog(@"RNTwilioVoice provider:performSetMutedCallAction:");
    
    TVOCall *call = self.activeCalls[action.callUUID.UUIDString];
    if (call) {
        [call setMuted:action.isMuted];
        [action fulfill];
    } else {
        [action fail];
    }
}

- (void)provider:(CXProvider *)provider performPlayDTMFCallAction:(CXPlayDTMFCallAction *)action {
  NSLog(@"RNTwilioVoice provider:performPlayDTMFCallAction:");

  TVOCall *call = self.activeCalls[action.callUUID.UUIDString];
  if (call && call.state == TVOCallStateConnected) {
    RCTLogInfo(@"RNTwilioVoice SendDigits %@", action.digits);
    [call sendDigits:action.digits];
  }
}

#pragma mark - CallKit Actions
- (void)performStartCallActionWithUUID:(NSUUID *)uuid handle:(NSString *)handle {
    if (uuid == nil || handle == nil) {
        return;
    }

    CXHandle *callHandle = [[CXHandle alloc] initWithType:CXHandleTypeGeneric value:handle];
    CXStartCallAction *startCallAction = [[CXStartCallAction alloc] initWithCallUUID:uuid handle:callHandle];
    CXTransaction *transaction = [[CXTransaction alloc] initWithAction:startCallAction];

    [self.callKitCallController requestTransaction:transaction completion:^(NSError *error) {
        if (error) {
            NSLog(@"RNTwilioVoice StartCallAction transaction request failed: %@", [error localizedDescription]);
        } else {
            NSLog(@"RNTwilioVoice StartCallAction transaction request successful");

            CXCallUpdate *callUpdate = [[CXCallUpdate alloc] init];
            callUpdate.remoteHandle = callHandle;
            callUpdate.supportsDTMF = YES;
            callUpdate.supportsHolding = YES;
            callUpdate.supportsGrouping = NO;
            callUpdate.supportsUngrouping = NO;
            callUpdate.hasVideo = NO;

            [self.callKitProvider reportCallWithUUID:uuid updated:callUpdate];
        }
    }];
}

- (void)reportIncomingCallFrom:(NSString *) from withUUID:(NSUUID *)uuid {
    CXHandle *callHandle = [[CXHandle alloc] initWithType:CXHandleTypeGeneric value:from];

    CXCallUpdate *callUpdate = [[CXCallUpdate alloc] init];
    callUpdate.remoteHandle = callHandle;
    callUpdate.supportsDTMF = YES;
    callUpdate.supportsHolding = YES;
    callUpdate.supportsGrouping = NO;
    callUpdate.supportsUngrouping = NO;
    callUpdate.hasVideo = NO;

    if ([from containsString:@"client:"]) {
      callUpdate.localizedCallerName = @"Seguridad";
    } else {
      callUpdate.localizedCallerName = @"Visitante";
    }

    [self.callKitProvider reportNewIncomingCallWithUUID:uuid update:callUpdate completion:^(NSError *error) {
        if (!error) {
            NSLog(@"RNTwilioVoice Incoming call successfully reported.");
        }
        else {
            NSLog(@"RNTwilioVoice Failed to report incoming call successfully: %@.", [error localizedDescription]);
        }
    }];
}

- (void)performEndCallActionWithUUID:(NSUUID *)uuid {
    CXEndCallAction *endCallAction = [[CXEndCallAction alloc] initWithCallUUID:uuid];
    CXTransaction *transaction = [[CXTransaction alloc] initWithAction:endCallAction];

    [self.callKitCallController requestTransaction:transaction completion:^(NSError *error) {
        if (error) {
            NSLog(@"RNTwilioVoice EndCallAction transaction request failed: %@", [error localizedDescription]);
        }
        else {
            NSLog(@"RNTwilioVoice EndCallAction transaction request successful");
        }
    }];
}

- (void)performVoiceCallWithUUID:(NSUUID *)uuid
                          client:(NSString *)client
                      completion:(void(^)(BOOL success))completionHandler {
    __weak typeof(self) weakSelf = self;
    TVOConnectOptions *connectOptions = [TVOConnectOptions optionsWithAccessToken:[self fetchAccessToken] block:^(TVOConnectOptionsBuilder *builder) {
        __strong typeof(self) strongSelf = weakSelf;
        builder.params = @{kTwimlParamTo: strongSelf.outgoingValue.text};
        builder.uuid = uuid;
    }];
    TVOCall *call = [TwilioVoice connectWithOptions:connectOptions delegate:self];
    if (call) {
        self.activeCall = call;
        self.activeCalls[call.uuid.UUIDString] = call;
    }
    self.callKitCompletionCallback = completionHandler;
}

- (void)performAnswerVoiceCallWithUUID:(NSUUID *)uuid
                            completion:(void(^)(BOOL success))completionHandler {
    TVOCallInvite *callInvite = self.activeCallInvites[uuid.UUIDString];
    NSAssert(callInvite, @"No CallInvite matches the UUID");
    
    TVOAcceptOptions *acceptOptions = [TVOAcceptOptions optionsWithCallInvite:callInvite block:^(TVOAcceptOptionsBuilder *builder) {
        builder.uuid = callInvite.uuid;
    }];

    TVOCall *call = [callInvite acceptWithOptions:acceptOptions delegate:self];

    if (self.activeCallInvite.uuid.UUIDString)

    if (!call) {
        completionHandler(NO);
    } else {
        self.callKitCompletionCallback = completionHandler;
        self.activeCall = call;
        self.activeCalls[call.uuid.UUIDString] = call;
    }

    [self.activeCallInvites removeObjectForKey:callInvite.uuid.UUIDString];
    self.activeCallInvite = nil;
    
    if ([[NSProcessInfo processInfo] operatingSystemVersion].majorVersion < 13) {
        [self incomingPushHandled];
    }
}

#pragma mark - Ringtone

- (void)playRingback {
    NSString *ringtonePath = [[NSBundle mainBundle] pathForResource:@"ringtone" ofType:@"wav"];
    if ([ringtonePath length] <= 0) {
        NSLog(@"Can't find sound file");
        return;
    }
    
    NSError *error;
    self.ringtonePlayer = [[AVAudioPlayer alloc] initWithContentsOfURL:[NSURL URLWithString:ringtonePath] error:&error];
    if (error != nil) {
        NSLog(@"Failed to initialize audio player: %@", error);
    } else {
        self.ringtonePlayer.delegate = self;
        self.ringtonePlayer.numberOfLoops = -1;
        
        self.ringtonePlayer.volume = 1.0f;
        [self.ringtonePlayer play];
    }
}

- (void)stopRingback {
    if (!self.ringtonePlayer.isPlaying) {
        return;
    }
    
    [self.ringtonePlayer stop];
}

- (void)audioPlayerDidFinishPlaying:(AVAudioPlayer *)player successfully:(BOOL)flag {
    if (flag) {
        NSLog(@"RNTwilioVoice Audio player finished playing successfully");
    } else {
        NSLog(@"RNTwilioVoice Audio player finished playing with some error");
    }
}

- (void)audioPlayerDecodeErrorDidOccur:(AVAudioPlayer *)player error:(NSError *)error {
    NSLog(@"RNTwilioVoice Decode error occurred: %@", error);
}


- (void)handleAppTerminateNotification {
  NSLog(@"RNTwilioVoice handleAppTerminateNotification called");

  //Disconnect all calls.
  if (self.activeCall) {
    NSLog(@"RNTwilioVoice handleAppTerminateNotification disconnecting an active call");
    [self.activeCall disconnect];
  }
}

RCT_EXPORT_METHOD(initWithAccessToken:(NSString *)token) {
  _token = token;
  [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(handleAppTerminateNotification) name:UIApplicationWillTerminateNotification object:nil];
  [self initPushRegistry];
}

RCT_EXPORT_METHOD(initWithAccessTokenUrl:(NSString *)tokenUrl) {
  _tokenUrl = tokenUrl;
  [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(handleAppTerminateNotification) name:UIApplicationWillTerminateNotification object:nil];
  [self initPushRegistry];
}

RCT_EXPORT_METHOD(connect: (NSDictionary *)params) {
  NSLog(@"Calling phone number %@", [params valueForKey:@"To"]);

  UIDevice* device = [UIDevice currentDevice];
  device.proximityMonitoringEnabled = YES;

  if (self.activeCall && self.activeCall.state == TVOCallStateConnected) {
    [self.activeCall disconnect];
  } else {
    NSUUID *uuid = [NSUUID UUID];
    NSString *handle = [params valueForKey:@"To"];
    _callParams = [[NSMutableDictionary alloc] initWithDictionary:params];
    [self performStartCallActionWithUUID:uuid handle:handle];
  }
}

RCT_EXPORT_METHOD(disconnect) {
  NSLog(@"Disconnecting call");
    self.userInitiatedDisconnect = YES;
  [self performEndCallActionWithUUID:self.activeCall.uuid];
}

RCT_EXPORT_METHOD(setMuted: (BOOL *)muted) {
  NSLog(@"Mute/UnMute call");
  self.activeCall.muted = muted;
}

RCT_EXPORT_METHOD(setSpeakerPhone: (BOOL *)speaker) {
  [self toggleAudioRoute:speaker];
}

RCT_EXPORT_METHOD(sendDigits: (NSString *)digits){
  if (self.activeCall && self.activeCall.state == TVOCallStateConnected) {
    NSLog(@"SendDigits %@", digits);
    [self.activeCall sendDigits:digits];
  }
}

RCT_EXPORT_METHOD(unregister){
  NSLog(@"unregister");
  NSString *accessToken = [self fetchAccessToken];

  [TwilioVoice unregisterWithAccessToken:accessToken
                             deviceToken:self.deviceTokenString
                              completion:^(NSError * _Nullable error) {
                                if (error) {
                                  NSLog(@"An error occurred while unregistering: %@", [error localizedDescription]);
                                } else {
                                  NSLog(@"Successfully unregistered for VoIP push notifications.");
                                }
                              }];

  self.deviceTokenString = nil;
}

RCT_REMAP_METHOD(getActiveCall,
                 resolver:(RCTPromiseResolveBlock)resolve
                 rejecter:(RCTPromiseRejectBlock)reject){
  NSMutableDictionary *params = [[NSMutableDictionary alloc] init];
  if (self.activeCallInvite) {
    if (self.callInvite.callSid){
      [params setObject:self.callInvite.callSid forKey:@"call_sid"];
    }
    if (self.callInvite.from){
      [params setObject:self.callInvite.from forKey:@"from"];
    }
    if (self.callInvite.to){
      [params setObject:self.callInvite.to forKey:@"to"];
    }
    [params setObject:StatePending forKey:@"call_state"];
   
    resolve(params);
  } else if (self.activeCall) {
    if (self.call.sid) {
      [params setObject:self.call.sid forKey:@"call_sid"];
    }
    if (self.call.to){
      [params setObject:self.call.to forKey:@"call_to"];
    }
    if (self.call.from){
      [params setObject:self.call.from forKey:@"call_from"];
    }
    [params setObject:StateConnected forKey:@"call_state"];
    resolve(params);
  } else{
    reject(@"no_call", @"There was no active call", nil);
  }
}

@end

