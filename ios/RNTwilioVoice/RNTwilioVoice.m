//
//  TwilioVoice.m
//

#import "RNTwilioVoice.h"
#import <React/RCTLog.h>

@import AVFoundation;
@import PushKit;
@import CallKit;
@import TwilioVoice;

@interface RNTwilioVoice () <PKPushRegistryDelegate, TVONotificationDelegate, TVOCallDelegate, CXProviderDelegate>
@property (nonatomic, strong) NSString *deviceTokenString;

@property (nonatomic, strong) PKPushRegistry *voipRegistry;
@property (nonatomic, strong) TVOCallInvite *callInvite;
@property (nonatomic, strong) TVOCall *call;
@property (nonatomic, strong) void(^callKitCompletionCallback)(BOOL);
@property (nonatomic, strong) CXProvider *callKitProvider;
@property (nonatomic, strong) CXCallController *callKitCallController;
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

- (void)dealloc {
  if (self.callKitProvider) {
    [self.callKitProvider invalidate];
  }

  [[NSNotificationCenter defaultCenter] removeObserver:self];
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

RCT_EXPORT_METHOD(configureCallKit: (NSDictionary *)params) {
  if (self.callKitCallController == nil) {
    _settings = [[NSMutableDictionary alloc] initWithDictionary:params];
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

    NSLog(@"CallKit Initialized");

    self.callKitCallController = [[CXCallController alloc] init];
  }
}

RCT_EXPORT_METHOD(connect: (NSDictionary *)params) {
  NSLog(@"Calling phone number %@", [params valueForKey:@"To"]);

//  [TwilioVoice setLogLevel:TVOLogLevelVerbose];

  UIDevice* device = [UIDevice currentDevice];
  device.proximityMonitoringEnabled = YES;

  if (self.call && self.call.state == TVOCallStateConnected) {
    [self.call disconnect];
  } else {
    NSUUID *uuid = [NSUUID UUID];
    NSString *handle = [params valueForKey:@"To"];
    _callParams = [[NSMutableDictionary alloc] initWithDictionary:params];
    [self performStartCallActionWithUUID:uuid handle:handle];
  }
}

RCT_EXPORT_METHOD(disconnect) {
  NSLog(@"Disconnecting call");
  [self performEndCallActionWithUUID:self.call.uuid];
}

RCT_EXPORT_METHOD(setMuted: (BOOL *)muted) {
  NSLog(@"Mute/UnMute call");
  self.call.muted = muted;
}

RCT_EXPORT_METHOD(setSpeakerPhone: (BOOL *)speaker) {
  [self toggleAudioRoute:speaker];
}

RCT_EXPORT_METHOD(sendDigits: (NSString *)digits){
  if (self.call && self.call.state == TVOCallStateConnected) {
    NSLog(@"SendDigits %@", digits);
    [self.call sendDigits:digits];
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
  if (self.callInvite) {
    if (self.callInvite.callSid){
      [params setObject:self.callInvite.callSid forKey:@"call_sid"];
    }
    if (self.callInvite.from){
      [params setObject:self.callInvite.from forKey:@"from"];
    }
    if (self.callInvite.to){
      [params setObject:self.callInvite.to forKey:@"to"];
    }
    if (self.callInvite.state == TVOCallInviteStatePending) {
      [params setObject:StatePending forKey:@"call_state"];
    } else if (self.callInvite.state == TVOCallInviteStateCanceled) {
      [params setObject:StateDisconnected forKey:@"call_state"];
    } else if (self.callInvite.state == TVOCallInviteStateRejected) {
      [params setObject:StateRejected forKey:@"call_state"];
    }
    resolve(params);
  } else if (self.call) {
    if (self.call.sid) {
      [params setObject:self.call.sid forKey:@"call_sid"];
    }
    if (self.call.to){
      [params setObject:self.call.to forKey:@"call_to"];
    }
    if (self.call.from){
      [params setObject:self.call.from forKey:@"call_from"];
    }
    if (self.call.state == TVOCallStateConnected) {
      [params setObject:StateConnected forKey:@"call_state"];
    } else if (self.call.state == TVOCallStateConnecting) {
      [params setObject:StateConnecting forKey:@"call_state"];
    } else if (self.call.state == TVOCallStateDisconnected) {
      [params setObject:StateDisconnected forKey:@"call_state"];
    }
    resolve(params);
  } else{
    reject(@"no_call", @"There was no active call", nil);
  }
}

- (void)initPushRegistry {
  self.voipRegistry = [[PKPushRegistry alloc] initWithQueue:dispatch_get_main_queue()];
  self.voipRegistry.delegate = self;
  self.voipRegistry.desiredPushTypes = [NSSet setWithObject:PKPushTypeVoIP];
}

- (NSString *)fetchAccessToken {
  if (_tokenUrl) {
    NSString *accessToken = [NSString stringWithContentsOfURL:[NSURL URLWithString:_tokenUrl]
                                                     encoding:NSUTF8StringEncoding
                                                        error:nil];
    return accessToken;
  } else {
    return _token;
  }
}

#pragma mark - PKPushRegistryDelegate
- (void)pushRegistry:(PKPushRegistry *)registry didUpdatePushCredentials:(PKPushCredentials *)credentials forType:(NSString *)type {
  NSLog(@"pushRegistry:didUpdatePushCredentials:forType");

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
                                                   NSLog(@"An error occurred while registering: %@", [error localizedDescription]);
                                                   NSMutableDictionary *params = [[NSMutableDictionary alloc] init];
                                                   [params setObject:[error localizedDescription] forKey:@"err"];

                                                   [self sendEventWithName:@"deviceNotReady" body:params];
                                                 } else {
                                                   NSLog(@"Successfully registered for VoIP push notifications.");
                                                   [self sendEventWithName:@"deviceReady" body:nil];
                                                 }
                                               }];
  }
}

- (void)pushRegistry:(PKPushRegistry *)registry didInvalidatePushTokenForType:(PKPushType)type {
  NSLog(@"pushRegistry:didInvalidatePushTokenForType");

  if ([type isEqualToString:PKPushTypeVoIP]) {
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
}

- (void)pushRegistry:(PKPushRegistry *)registry didReceiveIncomingPushWithPayload:(PKPushPayload *)payload forType:(NSString *)type {
  NSLog(@"pushRegistry:didReceiveIncomingPushWithPayload:forType");

  if ([type isEqualToString:PKPushTypeVoIP]) {
    [TwilioVoice handleNotification:payload.dictionaryPayload
                                            delegate:self];
  }
}

#pragma mark - TVONotificationDelegate
- (void)callInviteReceived:(TVOCallInvite *)callInvite {
    if (callInvite.state == TVOCallInviteStatePending) {
      [self handleCallInviteReceived:callInvite];
    } else if (callInvite.state == TVOCallInviteStateCanceled) {
      [self handleCallInviteCanceled:callInvite];
    }
}

- (void)handleCallInviteReceived:(TVOCallInvite *)callInvite {
  NSLog(@"callInviteReceived:");
  if (self.callInvite && self.callInvite == TVOCallInviteStatePending) {
    NSLog(@"Already a pending incoming call invite.");
    NSLog(@"  >> Ignoring call from %@", callInvite.from);
    return;
  } else if (self.call) {
    NSLog(@"Already an active call.");
    NSLog(@"  >> Ignoring call from %@", callInvite.from);
    return;
  }

  self.callInvite = callInvite;

  [self reportIncomingCallFrom:callInvite.from withUUID:callInvite.uuid];
}

- (void)handleCallInviteCanceled:(TVOCallInvite *)callInvite {
  NSLog(@"callInviteCanceled");

  [self performEndCallActionWithUUID:callInvite.uuid];

  NSMutableDictionary *params = [[NSMutableDictionary alloc] init];
  if (self.callInvite.callSid){
    [params setObject:self.callInvite.callSid forKey:@"call_sid"];
  }

  if (self.callInvite.from){
    [params setObject:self.callInvite.from forKey:@"from"];
  }
  if (self.callInvite.to){
    [params setObject:self.callInvite.to forKey:@"to"];
  }
  if (self.callInvite.state == TVOCallInviteStateCanceled) {
    [params setObject:StateDisconnected forKey:@"call_state"];
  } else if (self.callInvite.state == TVOCallInviteStateRejected) {
    [params setObject:StateRejected forKey:@"call_state"];
  }
  [self sendEventWithName:@"connectionDidDisconnect" body:params];

  self.callInvite = nil;
}

- (void)notificationError:(NSError *)error {
  NSLog(@"notificationError: %@", [error localizedDescription]);
}

#pragma mark - TVOCallDelegate
- (void)callDidConnect:(TVOCall *)call {
  self.call = call;
  self.callKitCompletionCallback(YES);
  self.callKitCompletionCallback = nil;

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
}

- (void)call:(TVOCall *)call didFailToConnectWithError:(NSError *)error {
  NSLog(@"Call failed to connect: %@", error);

  self.callKitCompletionCallback(NO);
  [self performEndCallActionWithUUID:call.uuid];
  [self callDisconnected:error];
}

- (void)call:(TVOCall *)call didDisconnectWithError:(NSError *)error {
  NSLog(@"Call disconnected with error: %@", error);

  [self performEndCallActionWithUUID:call.uuid];
  [self callDisconnected:error];
}

- (void)callDisconnected:(NSError *)error {
  NSMutableDictionary *params = [[NSMutableDictionary alloc] init];
  if (error) {
    NSString* errMsg = [error localizedDescription];
    if (error.localizedFailureReason) {
      errMsg = [error localizedFailureReason];
    }
    [params setObject:errMsg forKey:@"error"];
  }
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

  self.call = nil;
  self.callKitCompletionCallback = nil;
}

#pragma mark - AVAudioSession
- (void)toggleAudioRoute: (BOOL *)toSpeaker {
  // The mode set by the Voice SDK is "VoiceChat" so the default audio route is the built-in receiver.
  // Use port override to switch the route.
  NSError *error = nil;
  NSLog(@"toggleAudioRoute");

  if (toSpeaker) {
    if (![[AVAudioSession sharedInstance] overrideOutputAudioPort:AVAudioSessionPortOverrideSpeaker
                                                            error:&error]) {
      NSLog(@"Unable to reroute audio: %@", [error localizedDescription]);
    }
  } else {
    if (![[AVAudioSession sharedInstance] overrideOutputAudioPort:AVAudioSessionPortOverrideNone
                                                            error:&error]) {
      NSLog(@"Unable to reroute audio: %@", [error localizedDescription]);
    }
  }
}

#pragma mark - CXProviderDelegate
- (void)providerDidReset:(CXProvider *)provider {
  NSLog(@"providerDidReset");
  TwilioVoice.audioEnabled = YES;
}

- (void)providerDidBegin:(CXProvider *)provider {
  NSLog(@"providerDidBegin");
}

- (void)provider:(CXProvider *)provider didActivateAudioSession:(AVAudioSession *)audioSession {
  NSLog(@"provider:didActivateAudioSession");
  TwilioVoice.audioEnabled = YES;
}

- (void)provider:(CXProvider *)provider didDeactivateAudioSession:(AVAudioSession *)audioSession {
  NSLog(@"provider:didDeactivateAudioSession");
  TwilioVoice.audioEnabled = NO;
}

- (void)provider:(CXProvider *)provider timedOutPerformingAction:(CXAction *)action {
  NSLog(@"provider:timedOutPerformingAction");
}

- (void)provider:(CXProvider *)provider performStartCallAction:(CXStartCallAction *)action {
  NSLog(@"provider:performStartCallAction");

  [TwilioVoice configureAudioSession];
  TwilioVoice.audioEnabled = NO;

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
  NSLog(@"provider:performAnswerCallAction");

  // RCP: Workaround from https://forums.developer.apple.com/message/169511 suggests configuring audio in the
  //      completion block of the `reportNewIncomingCallWithUUID:update:completion:` method instead of in
  //      `provider:performAnswerCallAction:` per the WWDC examples.
  // [TwilioVoice configureAudioSession];

  NSAssert([self.callInvite.uuid isEqual:action.callUUID], @"We only support one Invite at a time.");

  TwilioVoice.audioEnabled = NO;
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
  NSLog(@"provider:performEndCallAction");

  TwilioVoice.audioEnabled = NO;

  if (self.callInvite && self.callInvite.state == TVOCallInviteStatePending) {
    [self sendEventWithName:@"callRejected" body:@"callRejected"];
    [self.callInvite reject];
    self.callInvite = nil;
  } else if (self.call) {
    [self.call disconnect];
  }

  [action fulfill];
}

- (void)provider:(CXProvider *)provider performSetHeldCallAction:(CXSetHeldCallAction *)action {
  if (self.call && self.call.state == TVOCallStateConnected) {
    [self.call setOnHold:action.isOnHold];
    [action fulfill];
  } else {
    [action fail];
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
      NSLog(@"StartCallAction transaction request failed: %@", [error localizedDescription]);
    } else {
      NSLog(@"StartCallAction transaction request successful");

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

- (void)reportIncomingCallFrom:(NSString *)from withUUID:(NSUUID *)uuid {
  CXHandle *callHandle = [[CXHandle alloc] initWithType:CXHandleTypeGeneric value:from];

  CXCallUpdate *callUpdate = [[CXCallUpdate alloc] init];
  callUpdate.remoteHandle = callHandle;
  callUpdate.supportsDTMF = YES;
  callUpdate.supportsHolding = YES;
  callUpdate.supportsGrouping = NO;
  callUpdate.supportsUngrouping = NO;
  callUpdate.hasVideo = NO;

  [self.callKitProvider reportNewIncomingCallWithUUID:uuid update:callUpdate completion:^(NSError *error) {
    if (!error) {
      NSLog(@"Incoming call successfully reported");

      // RCP: Workaround per https://forums.developer.apple.com/message/169511
      [TwilioVoice configureAudioSession];
    } else {
      NSLog(@"Failed to report incoming call successfully: %@.", [error localizedDescription]);
    }
  }];
}

- (void)performEndCallActionWithUUID:(NSUUID *)uuid {
  if (uuid == nil) {
    return;
  }

  UIDevice* device = [UIDevice currentDevice];
  device.proximityMonitoringEnabled = NO;

  CXEndCallAction *endCallAction = [[CXEndCallAction alloc] initWithCallUUID:uuid];
  CXTransaction *transaction = [[CXTransaction alloc] initWithAction:endCallAction];

  [self.callKitCallController requestTransaction:transaction completion:^(NSError *error) {
    if (error) {
      NSLog(@"EndCallAction transaction request failed: %@", [error localizedDescription]);
    } else {
      NSLog(@"EndCallAction transaction request successful");
    }
  }];
}

- (void)performVoiceCallWithUUID:(NSUUID *)uuid
                          client:(NSString *)client
                      completion:(void(^)(BOOL success))completionHandler {

    self.call = [TwilioVoice call:[self fetchAccessToken]
                                            params:_callParams
                                              uuid:uuid
                                          delegate:self];

    self.callKitCompletionCallback = completionHandler;
}

- (void)performAnswerVoiceCallWithUUID:(NSUUID *)uuid
                            completion:(void(^)(BOOL success))completionHandler {

    self.call = [self.callInvite acceptWithDelegate:self];
    self.callInvite = nil;
    self.callKitCompletionCallback = completionHandler;
}

- (void)handleAppTerminateNotification {
  NSLog(@"handleAppTerminateNotification called");

  if (self.call) {
    NSLog(@"handleAppTerminateNotification disconnecting an active call");
    [self.call disconnect];
  }
}

@end
