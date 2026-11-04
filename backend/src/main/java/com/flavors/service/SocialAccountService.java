package com.flavors.service;

import com.flavors.dto.SocialAccountResponse;
import java.util.List;

/**
 * Connecting, listing and disconnecting a creator's social accounts (EEC-33, UC-F19.4), and pulling their
 * figures again (used by the daily refresh, EEC-37). The creator is always the logged-in user.
 */
public interface SocialAccountService {

    /**
     * Step 1: the Instagram page where she logs in and grants access.
     *
     * @param state a random value the controller keeps in her session and checks when Instagram sends her back
     */
    String instagramAuthorizationUrl(long creatorId, String state);

    /**
     * Steps 2–3: completes the connection with the code Instagram sent back. Saves the account and its encrypted
     * token and stores the first figures, with the time of the pull. If anything fails, nothing is saved (EX-F19.4.1).
     * Connecting an account she connected before reconnects it.
     */
    SocialAccountResponse connectInstagram(long creatorId, String code);

    List<SocialAccountResponse> myAccounts(long creatorId);

    /** Forgets the token; the figures stay and the account shows 'Needs reconnection' (FR-F19.16). */
    void disconnect(long creatorId, long accountId);

    /**
     * Pulls fresh figures with the stored token. If the pull fails, or the token was revoked, the last figures
     * and their time stay and the account is flagged 'Needs reconnection' (TC-F19.4.3, EX-F1.3.1).
     *
     * @return true if the pull worked
     */
    boolean refresh(long accountId);
}
