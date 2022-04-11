//
//  TwilioVoice.h
//  TwilioVoice
//

#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RNTwilioVoice : RCTEventEmitter <RCTBridgeModule>
- (void)initPushRegistry;
- (void)configureCallKit: (NSDictionary *)params;
@end
