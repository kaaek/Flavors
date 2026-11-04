package com.flavors.service;

import java.util.List;

/**
 * The only part of the app that talks to Instagram (SPIKE-1 chose it for the MVP). Keeping every call here
 * means the rest of the app, and every test, can work with a stand-in instead of the real Instagram.
 *
 * <p>Failures are thrown as {@code SocialPlatformException}; a revoked or expired token as its subclass
 * {@code SocialTokenInvalidException}. Exception messages never contain a token.
 */
public interface InstagramClient {

    /** The value stored in {@code social_account.platform}. */
    String PLATFORM = "instagram";

    /** False until the Meta app's id, secret and redirect URL are set (see the README). */
    boolean isConfigured();

    /** The Instagram page where the creator logs in and grants access (EEC-33 main scenario, step 2). */
    String authorizationUrl(String state);

    /** Trades the one-time code Instagram sent back for a long-lived (60-day) access token. */
    String exchangeCode(String code);

    /** Reads the account's id, follower count and the figures of its recent posts. */
    Snapshot fetchSnapshot(String accessToken);

    /** What one pull returns. */
    record Snapshot(String externalId, String username, long followers, List<PostFigures> posts) {
    }

    /** One post's engagement (FR-F13.11: views, likes, comments and shares). */
    record PostFigures(String externalPostId, long views, long likes, long comments, long shares) {
    }
}
