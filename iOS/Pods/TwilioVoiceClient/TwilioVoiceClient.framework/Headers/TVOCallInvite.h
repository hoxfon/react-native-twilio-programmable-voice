//
//  TVOCallInvite.h
//  TwilioVoice
//
//  Copyright © 2016 Twilio. All rights reserved.
//

#import <Foundation/Foundation.h>

@class TVOCall;
@protocol TVOCallDelegate;


/**
 * Enumeration indicating the state of the call invite.
 */
typedef NS_ENUM(NSUInteger, TVOCallInviteState) {
    TVOCallInviteStatePending = 0,      ///< The call invite is pending for action.
    TVOCallInviteStateAccepted,         ///< The call invite has been accepted.
    TVOCallInviteStateRejected,         ///< The call invite has been rejected.
    TVOCallInviteStateCancelled         ///< The call invite has been cancelled by the caller.
};


/**
 * The `TVOCallInvite` object represents an incoming call invite. `TVOCallInvite` objects are 
 * not created directly; they are returned by the `<[TVONotificationDelegate callInviteReceived:]>`
 * delegate method.
 */
@interface TVOCallInvite : NSObject


/**
 * @name Properties
 */

/**
 * `From` value of the call invite.
 */
@property (nonatomic, strong, readonly, nonnull) NSString *from;

/**
 * `To` value of the call invite.
 */
@property (nonatomic, strong, readonly, nonnull) NSString *to;

/**
 * `Call SID` value of the call invite.
 */
@property (nonatomic, strong, readonly, nonnull) NSString *callSid;

/**
 * State of the call invite.
 */
@property (nonatomic, assign, readonly) TVOCallInviteState state;

/**
 * @name Call Invite Actions
 */

/**
 * Accepts the incoming call invite.
 */
- (nullable TVOCall *)acceptWithDelegate:(nonnull id<TVOCallDelegate>)delegate;

/**
 * Rejects the incoming call invite.
 */
- (void)reject;


- (null_unspecified instancetype)init __attribute__((unavailable("Incoming calls cannot be instantiated directly. See `TVONotificationDelegate`")));

@end


/**
 * CallKit Call Actions
 */
@interface TVOCallInvite (CallKitIntegration)

/**
 * UUID of the call.
 *
 * Use this UUID for CallKit methods.
 */
@property (nonatomic, strong, readonly, nonnull) NSUUID *uuid;

@end
