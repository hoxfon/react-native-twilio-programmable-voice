#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RNTwilioVoice : RCTEventEmitter <RCTBridgeModule>
- (void) configCallKit: (NSDictionary *)params;
- (void) reRegisterWithTwilioVoice;
- (void) initPushRegistry;
@end
