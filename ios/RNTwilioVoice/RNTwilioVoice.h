#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RNTwilioVoice : RCTEventEmitter <RCTBridgeModule>
- (void) initTwilioVoice: (NSString *)appName;
@end
