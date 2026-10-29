package com.flavors.account;
 
/** CONNECTED, or NEEDS_RECONNECTION when the creator disconnects or a daily pull fails (EX-F1.3.1). */
public enum SocialAccountStatus {
    CONNECTED,
    NEEDS_RECONNECTION
}
 