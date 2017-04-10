//
//  TVOCallDelegate.h
//  TwilioVoice
//
//  Copyright © 2016 Twilio. All rights reserved.
//

@class TVOCall;

/**
 * Objects that adopt the `TVOCallDelegate` protocol will be informed of the
 * lifecycle events of calls.
 */
@protocol TVOCallDelegate <NSObject>

/**
 * @name Required Methods
 */

/**
 * Notifies the delegate that a call has connected.
 *
 * @param call The `<TVOCall>` that was connected.
 *
 * @see TVOCall
 */
- (void)callDidConnect:(nonnull TVOCall *)call;

/**
 * Notifies the delegate that a call has disconnected.
 *
 * @param call The `<TVOCall>` that was disconnected.
 *
 * @see TVOCall
 */
- (void)callDidDisconnect:(nonnull TVOCall *)call;

/**
 * Notifies the delegate that a call has encountered an error.
 *
 * @param call The `<TVOCall>` that encountered the error.
 * @param error The `NSError` that occurred.
 *
 * @see TVOCall
 */
- (void)call:(nonnull TVOCall *)call didFailWithError:(nonnull NSError *)error;

@end
