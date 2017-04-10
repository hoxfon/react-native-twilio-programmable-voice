//
//  VoiceClient.h
//  TwilioVoice
//
//  Copyright © 2016 Twilio. All rights reserved.
//

#import <Foundation/Foundation.h>

#import "TVONotificationDelegate.h"

@class TVOCall;
@protocol TVOCallDelegate;

/**
 * Enumeration indicating the client’s logging level.
 */
typedef NS_ENUM(NSUInteger, TVOLogLevel) {
    TVOLogLevelOff = 0,     ///< Disables all SDK logging.
    TVOLogLevelError,       ///< Show errors only.
    TVOLogLevelWarn,        ///< Show warnings as well as all **Error** log messages.
    TVOLogLevelInfo,        ///< Show informational messages as well as all **Warn** log messages.
    TVOLogLevelDebug,       ///< Show debugging messages as well as all **Info** log messages.
    TVOLogLevelVerbose      ///< Show low-level debugging messages as well as all **Debug** log messages.
};

/**
 * `TwilioVoice` logging modules.
 */
typedef NS_ENUM(NSUInteger, TVOLogModule) {
    TVOLogModulePJSIP = 0,  ///< PJSIP Module
    TVOLogModuleNotify      ///< Twilio Notify Module
};

/**
 * The `VoiceClient` is the entry point for interaction with the Twilio service.
 */
@interface VoiceClient : NSObject

/**
 * Logging level of the SDK.
 *
 * @see TVOLogLevel
 */
@property (nonatomic, assign) TVOLogLevel logLevel;

/**
 * Call quality metrics publishing. `YES` to enable. `NO` to disable.
 */
@property (nonatomic, assign, getter=isPublishMetricsEnabled) BOOL publishMetricsEnabled;

/**
 * Returns the shared instance of the `VoiceClient`.
 *
 * @return The shared instance of the `VoiceClient`
 */
+ (nonnull VoiceClient *)sharedInstance;

/**
 * Returns the version of the SDK.
 *
 * @return The version of the SDK.
 */
- (nonnull NSString *)version;

/**
 * Sets logging level for an individual module.
 *
 * @param module The `<TVOLogModule>` for which the logging level is to be set.
 * @param level The `<TVOLogLevel>` level to be used by the module.
 *
 * @see TVOLogModule
 * @see TVOLogLevel
 */
- (void)setModule:(TVOLogModule)module
         logLevel:(TVOLogLevel)level;

/**
 * @name Managing VoIP Push Notifications
 */

/**
 * Registers for Twilio VoIP push notifications.
 *
 * @param accessToken Twilio Access Token.
 * @param deviceToken The push registry token for Apple VoIP Service.
 * @param completion Callback block to receive the result of the registration.
 */
- (void)registerWithAccessToken:(nonnull NSString *)accessToken
                    deviceToken:(nonnull NSString *)deviceToken
                     completion:(nullable void(^)(NSError * __nullable error))completion;

/**
 * Unregisters from Twilio VoIP push notifications.
 *
 * @param accessToken Twilio Access Token.
 * @param deviceToken The push registry token for Apple VoIP Service.
 * @param completion Callback block to receive the result of the unregistration.
 */
- (void)unregisterWithAccessToken:(nonnull NSString *)accessToken
                      deviceToken:(nonnull NSString *)deviceToken
                       completion:(nullable void(^)(NSError * __nullable error))completion;

/**
 * Processes the incoming VoIP push notification payload.
 *
 * @param payload Push notification payload.
 * @param delegate A `<TVONotificationDelegate>` to receive incoming call callbacks.
 *
 * @see TVONotificationDelegate
 */
- (void)handleNotification:(nonnull NSDictionary *)payload
                  delegate:(__nullable id<TVONotificationDelegate>)delegate;

/**
 * @name Making Outgoing Calls
 */

/**
 * Makes an outgoing call.
 *
 * @param accessToken Twilio Access Token.
 * @param twiMLParams Additional parameters to be passed to the TwiML application.
 * @param delegate A `<TVOCallDelegate>` to receive call state updates.
 *
 * @return A `<TVOCall>` object.
 *
 * @see TVOCall
 * @see TVOCallDelegate
 */
- (nullable TVOCall *)call:(nonnull NSString *)accessToken
                    params:(nullable NSDictionary <NSString *, NSString *> *)twiMLParams
                  delegate:(nullable id<TVOCallDelegate>)delegate;

@end


/**
 * CallKit Audio Session Handling
 */
@interface VoiceClient (CallKitIntegration)

/**
 * The application needs to use this method to set up the AVAudioSession with desired
 * configuration before letting the CallKit framework activate the audio session.
 */
- (void)configureAudioSession NS_AVAILABLE_IOS(10_0);

/**
 * The application needs to use this method to signal the SDK to start audio I/O units
 * when receiving the audio activation callback of CXProviderDelegate.
 */
- (void)startAudioDevice NS_AVAILABLE_IOS(10_0);

/**
 * The application needs to use this method to signal the SDK to stop audio I/O units
 * when receiving the deactivation or reset callbacks of CXProviderDelegate.
 */
- (void)stopAudioDevice NS_AVAILABLE_IOS(10_0);

@end
