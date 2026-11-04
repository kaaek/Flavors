package com.flavors.service;

import java.util.List;

/**
 * The only part of the app that talks to Instagram (SPIKE-1 researched it; EEC-88 moved it to Unified Login). Keeping every call here
 * means the rest of the app, and every test, can work with a stand-in instead of the real Instagram.
 *
 * <p>Failures are thrown as {@code SocialPlatformException}; a revoked or expired token as its subclass
 * {@code SocialTokenInvalidException}. Exception messages never contain a token.
 */
public interface InstagramClient {

    /** The value stored in {@code social_account.platform}. */
    String PLATFORM = "instagram";

    /** False until the Meta app's id, secret, configuration id and redirect URL are set (see the README). */
    boolean isConfigured();

    /** The Meta page where the creator logs in, picks her Instagram account and grants access (EEC-33 step 2). */
    String authorizationUrl(String state);

    /**
     * Trades the one-time code Meta sent back for an access token. It is a system-user token belonging to her
     * business portfolio. It doesn't expire when the configuration was made with expiry "Never" (the README says to);
     * a token that stops working (she removed the app, or moved the account) fails with
     * {@code SocialTokenInvalidException} on the next call.
     */
    String exchangeCode(String code);

    /** The Instagram accounts she shared with the app; one token can cover several. */
    List<AccountRef> assignedAccounts(String accessToken);

    /** Reads this account's follower count and the figures of its recent posts. */
    Snapshot fetchSnapshot(String accessToken, String externalId);

    /** An Instagram professional account: its id on Instagram and its username. */
    record AccountRef(String id, String username) {
    }

    /** What one pull returns. */
    record Snapshot(String externalId, String username, long followers, List<PostFigures> posts) {
    }

    /** One post's engagement (FR-F13.11: views, likes, comments and shares). */
    record PostFigures(String externalPostId, long views, long likes, long comments, long shares) {
    }
}
