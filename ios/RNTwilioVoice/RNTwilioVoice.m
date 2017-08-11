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
@property (nonatomic, strong) CXProvider *callKitProvider;
@property (nonatomic, strong) CXCallController *callKitCallController;
@end

@implementation RNTwilioVoice {
  NSMutableDictionary *_settings;
  NSMutableDictionary *_callParams;
  NSString *_tokenUrl;
  NSString *_token;
}

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
  return @[@"connectionDidConnect", @"connectionDidDisconnect", @"callRejected", @"deviceReady"];
}

@synthesize bridge = _bridge;

- (void)dealloc {
  if (self.callKitProvider) {
    [self.callKitProvider invalidate];
  }
}

RCT_EXPORT_METHOD(initWithAccessToken:(NSString *)token) {
  _token = token;
  [self initPushRegistry];
}

RCT_EXPORT_METHOD(initWithAccessTokenUrl:(NSString *)tokenUrl) {
  _tokenUrl = tokenUrl;
  [self initPushRegistry];
}

RCT_EXPORT_METHOD(configureCallKit: (NSDictionary *)params) {
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

  RCTLogInfo(@"CallKit Initialized");

  _callKitCallController = [[CXCallController alloc] init];
  [self sendEventWithName:@"deviceReady" body:nil];
}

RCT_EXPORT_METHOD(connect: (NSDictionary *)params) {
  RCTLogInfo(@"Calling phone number %@", [params valueForKey:@"To"]);


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
  RCTLogInfo(@"Disconnecting call");
  [self performEndCallActionWithUUID:self.call.uuid];
}

RCT_EXPORT_METHOD(setMuted: (BOOL *)muted) {
  RCTLogInfo(@"Mute/UnMute call");
  self.call.muted = muted;
}

RCT_EXPORT_METHOD(setSpeakerPhone: (BOOL *)speaker) {
  [self routeAudioToSpeaker:speaker];
}

RCT_EXPORT_METHOD(sendDigits: (NSString *)digits){
  if (self.call && self.call.state == TVOCallStateConnected) {
    RCTLogInfo(@"SendDigits %@", digits);
    [self.call sendDigits:digits];
  }
}

RCT_EXPORT_METHOD(unregister){
  NSLog(@"unregister");
  NSString *accessToken = [self fetchAccessToken];

  [[TwilioVoice sharedInstance] unregisterWithAccessToken:accessToken
                                              deviceToken:self.deviceTokenString
                                               completion:^(NSError * _Nullable error) {
                                                 if (error) {
                                                   NSLog(@"An error occurred while unregistering: %@", [error localizedDescription]);
                                                 }
                                                 else {
                                                   NSLog(@"Successfully unregistered for VoIP push notifications.");
                                                 }
                                               }];

  self.deviceTokenString = nil;
}



- (void)initPushRegistry {
  self.voipRegistry = [[PKPushRegistry alloc] initWithQueue:dispatch_get_main_queue()];
  self.voipRegistry.delegate = self;
  self.voipRegistry.desiredPushTypes = [NSSet setWithObject:PKPushTypeVoIP];
}

- (NSString *)fetchAccessToken {
  if(_tokenUrl) {
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
  NSLog(@"pushRegistry:didUpdatePushCredentials:forType:");

  if ([type isEqualToString:PKPushTypeVoIP]) {
    self.deviceTokenString = [credentials.token description];
    NSString *accessToken = [self fetchAccessToken];

    [[TwilioVoice sharedInstance] registerWithAccessToken:accessToken
                                              deviceToken:self.deviceTokenString
                                               completion:^(NSError *error) {
                                                 if (error) {
                                                   NSLog(@"An error occurred while registering: %@", [error localizedDescription]);
                                                 }
                                                 else {
                                                   NSLog(@"Successfully registered for VoIP push notifications.");
                                                 }
                                               }];
  }
}

- (void)pushRegistry:(PKPushRegistry *)registry didInvalidatePushTokenForType:(PKPushType)type {
  NSLog(@"pushRegistry:didInvalidatePushTokenForType:");

  if ([type isEqualToString:PKPushTypeVoIP]) {
    NSString *accessToken = [self fetchAccessToken];

    [[TwilioVoice sharedInstance] unregisterWithAccessToken:accessToken
                                                deviceToken:self.deviceTokenString
                                                 completion:^(NSError * _Nullable error) {
                                                   if (error) {
                                                     NSLog(@"An error occurred while unregistering: %@", [error localizedDescription]);
                                                   }
                                                   else {
                                                     NSLog(@"Successfully unregistered for VoIP push notifications.");
                                                   }
                                                 }];

    self.deviceTokenString = nil;
  }
}

- (void)pushRegistry:(PKPushRegistry *)registry didReceiveIncomingPushWithPayload:(PKPushPayload *)payload forType:(NSString *)type {
  NSLog(@"pushRegistry:didReceiveIncomingPushWithPayload:forType");
  if ([type isEqualToString:PKPushTypeVoIP]) {
    [[TwilioVoice sharedInstance] handleNotification:payload.dictionaryPayload
                                            delegate:self];
  }
}

#pragma mark - TVONotificationDelegate
- (void)callInviteReceived:(TVOCallInvite *)callInvite {
  NSLog(@"callInviteReceived");

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

- (void)callInviteCancelled:(TVOCallInvite *)callInvite {
  NSLog(@"callInviteCancelled:");

  [self performEndCallActionWithUUID:callInvite.uuid];

  NSMutableDictionary *params = [[NSMutableDictionary alloc] init];
  [params setObject:self.callInvite.callSid forKey:@"call_sid"];

  if (self.callInvite.from){
    [params setObject:self.callInvite.from forKey:@"from"];
  }
  if (self.callInvite.to){
    [params setObject:self.callInvite.to forKey:@"to"];
  }
  if (self.callInvite.state == TVOCallInviteStateCancelled) {
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
  NSMutableDictionary *callParams = [[NSMutableDictionary alloc] init];

  if (call.callSid) {
    NSLog(@"Call Sid %@", call.callSid);
    [callParams setObject:call.callSid forKey:@"call_sid"];
  }
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

- (void)callDidDisconnect:(TVOCall *)call {
  NSLog(@"connectionDidDisconnect:");

  NSMutableDictionary *params = [[NSMutableDictionary alloc] init];
  [params setObject:self.call.callSid forKey:@"call_sid"];
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

  if (self.call.state == TVOCallStateConnected) {
    [self performEndCallActionWithUUID:call.uuid];
  }
  self.call = nil;
}

- (void)call:(TVOCall *)call didFailWithError:(NSError *)error {
  NSLog(@"call:didFailWithError: %@", [error localizedDescription]);

  NSMutableDictionary *params = [[NSMutableDictionary alloc] init];
  [params setObject:error forKey:@"error"];
  [params setObject:self.call.callSid forKey:@"call_sid"];
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

  if (self.call.state == TVOCallStateConnected) {
    [self performEndCallActionWithUUID:call.uuid];
  }
  self.call = nil;
}

#pragma mark - AVAudioSession
- (void)routeAudioToSpeaker: (BOOL *)speaker {
  NSError *error = nil;
  NSLog(@"routeAudioToSpeaker");
  if(speaker) {
    if (![[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryPlayAndRecord
                                          withOptions:AVAudioSessionCategoryOptionDefaultToSpeaker
                                                error:&error]) {
      NSLog(@"Unable to reroute audio: %@", [error localizedDescription]);
    }
  } else {
    if (![[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryPlayAndRecord
                                                error:&error]) {
      NSLog(@"Unable to reroute audio: %@", [error localizedDescription]);
    }

  }
}

#pragma mark - CXProviderDelegate
- (void)providerDidReset:(CXProvider *)provider {
  NSLog(@"providerDidReset:");
}

- (void)providerDidBegin:(CXProvider *)provider {
  NSLog(@"providerDidBegin:");
}

- (void)provider:(CXProvider *)provider didActivateAudioSession:(AVAudioSession *)audioSession {
  NSLog(@"provider:didActivateAudioSession:");

  [[TwilioVoice sharedInstance] startAudioDevice];
}

- (void)provider:(CXProvider *)provider didDeactivateAudioSession:(AVAudioSession *)audioSession {
  NSLog(@"provider:didDeactivateAudioSession:");

  [[TwilioVoice sharedInstance] stopAudioDevice];
}

- (void)provider:(CXProvider *)provider timedOutPerformingAction:(CXAction *)action {
  NSLog(@"provider:timedOutPerformingAction:");
}

- (void)provider:(CXProvider *)provider performStartCallAction:(CXStartCallAction *)action {
  NSLog(@"provider:performStartCallAction");

  [[TwilioVoice sharedInstance] configureAudioSession];
  self.call = [[TwilioVoice sharedInstance] call:[self fetchAccessToken]
                                          params:_callParams
                                        delegate:self];

  if (!self.call) {
    [action fail];
  } else {
    self.call.uuid = action.callUUID;

    [action fulfillWithDateStarted:[NSDate date]];
  }
}

- (void)provider:(CXProvider *)provider performAnswerCallAction:(CXAnswerCallAction *)action {
  NSLog(@"provider:performAnswerCallAction");


  // RCP: Workaround from https://forums.developer.apple.com/message/169511 suggests configuring audio in the
  //      completion block of the `reportNewIncomingCallWithUUID:update:completion:` method instead of in
  //      `provider:performAnswerCallAction:` per the WWDC examples.
  // [[TwilioVoice sharedInstance] configureAudioSession];

  self.call = [self.callInvite acceptWithDelegate:self];
  if (self.call) {
    self.call.uuid = [action callUUID];
  }

  self.callInvite = nil;

  [action fulfill];
}

- (void)provider:(CXProvider *)provider performEndCallAction:(CXEndCallAction *)action {
  NSLog(@"provider:performEndCallAction:");

  [[TwilioVoice sharedInstance] stopAudioDevice];

  if (self.callInvite && self.callInvite.state == TVOCallInviteStatePending) {
    [self sendEventWithName:@"callRejected" body:@"callRejected"];
    [self.callInvite reject];
    self.callInvite = nil;
  } else if (self.call) {
    [self.call disconnect];
  }

  [action fulfill];
}

- (void)provider:(CXProvider *)provider performPlayDTMFCallAction:(CXPlayDTMFCallAction *)action {
  if (self.call && self.call.state == TVOCallStateConnected) {
    RCTLogInfo(@"SendDigits %@", action.digits);
    [self.call sendDigits:action.digits];
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
      callUpdate.supportsHolding = NO;
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
  callUpdate.supportsHolding = NO;
  callUpdate.supportsGrouping = NO;
  callUpdate.supportsUngrouping = NO;
  callUpdate.hasVideo = NO;

  [self.callKitProvider reportNewIncomingCallWithUUID:uuid update:callUpdate completion:^(NSError *error) {
    if (!error) {
      NSLog(@"Incoming call successfully reported");

      // RCP: Workaround per https://forums.developer.apple.com/message/169511
      [[TwilioVoice sharedInstance] configureAudioSession];
    }
    else {
      NSLog(@"Failed to report incoming call successfully: %@.", [error localizedDescription]);
    }
  }];
}

- (void)performEndCallActionWithUUID:(NSUUID *)uuid {
  if (uuid == nil) {
    return;
  }

  CXEndCallAction *endCallAction = [[CXEndCallAction alloc] initWithCallUUID:uuid];
  CXTransaction *transaction = [[CXTransaction alloc] initWithAction:endCallAction];

  [self.callKitCallController requestTransaction:transaction completion:^(NSError *error) {
    if (error) {
      NSLog(@"EndCallAction transaction request failed: %@", [error localizedDescription]);
    }
    else {
      NSLog(@"EndCallAction transaction request successful");
    }
  }];
}

@end
