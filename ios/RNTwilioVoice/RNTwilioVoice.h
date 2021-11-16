#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RNTwilioVoice : RCTEventEmitter <RCTBridgeModule>

- (void)initPushRegistry;
- (void)initWithCachedAccessTokenUrl: (NSDictionary *)params;

@end
