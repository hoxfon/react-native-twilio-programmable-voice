//
//  TVOCall.h
//  TwilioVoice
//
//  Copyright © 2016 Twilio. All rights reserved.
//

#import <Foundation/Foundation.h>

@protocol TVOCallDelegate;

/**
 * Enumeration indicating the state of the call.
 */
typedef NS_ENUM(NSUInteger, TVOCallState) {
    TVOCallStateConnecting = 0, ///< The call is connecting.
    TVOCallStateConnected,      ///< The call is connected.
    TVOCallStateDisconnected    ///< The call is disconnected.
};


/**
 * The `TVOCall` object represents a call. `TVOCall` objects are not created directly; they
 * are returned by the `<[TVOCallInvite acceptWithDelegate:]>` method or the
 * `<[VoiceClient call:params:delegate:]>` method.
 */
@interface TVOCall : NSObject


/**
 * @name Properties
 */

/**
 * The `<TVOCallDelegate>` object that will receive call state updates.
 *
 * @see TVOCallDelegate
 */
@property (nonatomic, weak, nullable) id<TVOCallDelegate> delegate;

/**
 * `From` value of the call. ***Note:*** This may be `nil` if the call object was created
 * by calling the `<[VoiceClient call:params:delegate:]>` method.
 */
@property (nonatomic, strong, readonly, nonnull) NSString *from;

/**
 * `To` value of the call. ***Note:*** This may be `nil` if the call object was created
 * by calling the `<[VoiceClient call:params:delegate:]>` method.
 */
@property (nonatomic, strong, readonly, nonnull) NSString *to;

/**
 * `Call SID` value of the call.
 */
@property (nonatomic, strong, readonly, nonnull) NSString *callSid;

/**
 * Property that defines if the call is muted.
 *
 * Setting the property will only take effect if the `<state>` is `TVOCallConnected`.
 */
@property (nonatomic, assign, getter=isMuted) BOOL muted;

/**
 * State of the call.
 *
 * @see TVOCallState
 */
@property (nonatomic, assign, readonly) TVOCallState state;


/**
 * @name General Call Actions
 */

/**
 * Disconnects the call.
 *
 * Calling this method on a `TVOCall` that does not have the `<state>` of `TVOCallStateConnected` 
 * will have no effect.
 */
- (void)disconnect;

/**
 * Send a string of digits.
 *
 * Calling this method on a `TVOCall` that does not have the `<state>` of `TVOCallStateConnected`
 * will have no effect.
 *
 * @param digits A string of characters to be played. Valid values are '0' - '9', '*', '#', and 'w'.
 *               Each 'w' will cause a 500 ms pause between digits sent.
 */
- (void)sendDigits:(nonnull NSString *)digits;


- (null_unspecified instancetype)init __attribute__((unavailable("Calls cannot be instantiated directly. See `TVOCallInvite acceptWithDelegate:` or `VoiceClient call:params:delegate:`")));

@end


/**
 * CallKit Call Actions
 */
@interface TVOCall (CallKitIntegration)

/**
 * UUID of the call.
 *
 * Use this UUID for CallKit methods.
 */
@property (nonatomic, strong, nonnull) NSUUID *uuid;

@end
