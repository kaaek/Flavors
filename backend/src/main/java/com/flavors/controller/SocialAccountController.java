package com.flavors.controller;

import com.flavors.dto.CurrentUser;
import com.flavors.dto.InstagramConnectResponse;
import com.flavors.dto.SocialAccountResponse;
import com.flavors.exception.CreatorProfileMissingException;
import com.flavors.exception.SocialAccountTakenException;
import com.flavors.exception.SocialConnectionUnavailableException;
import com.flavors.exception.SocialPlatformException;
import com.flavors.service.SocialAccountService;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Connect a social account (EEC-33, UC-F19.4). Only creators reach {@code /api/creator/**} (SecurityConfig),
 * and the creator is always the logged-in user.
 *
 * <p>Connecting is a round trip through Instagram:
 * <ol>
 *   <li>The page calls {@code POST /instagram/connect} and sends the browser to the URL it gets back.</li>
 *   <li>She logs in to Instagram and grants access (or cancels).</li>
 *   <li>Instagram sends the browser to {@code GET /instagram/callback}, which finishes the connection and
 *       sends her back to {@code /creator/social?instagram=<result>}, where the page shows the result.</li>
 * </ol>
 * A random {@code state}, kept in her session, proves the callback belongs to the connection she started.
 */
@RestController
@RequestMapping("/api/creator/social")
class SocialAccountController {

    /** Session attribute holding the state of the connection in progress. */
    static final String STATE_ATTRIBUTE = "flavors.instagram.state";

    /** The page that shows the outcome; the value after "=" says which one. */
    static final String RESULT_PAGE = "/creator/social?instagram=";

    private static final Logger LOGGER = LoggerFactory.getLogger(SocialAccountController.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SocialAccountService service;

    SocialAccountController(SocialAccountService service) {
        this.service = service;
    }

    @GetMapping
    List<SocialAccountResponse> list(@AuthenticationPrincipal CurrentUser user) {
        return service.myAccounts(user.getId());
    }

    @PostMapping("/instagram/connect")
    InstagramConnectResponse connect(@AuthenticationPrincipal CurrentUser user, HttpSession session) {
        String state = newState();
        String url = service.instagramAuthorizationUrl(user.getId(), state);
        session.setAttribute(STATE_ATTRIBUTE, state);
        return new InstagramConnectResponse(url);
    }

    /**
     * Where Instagram sends the browser back. It always answers with a redirect to the result page, because the
     * browser is navigating here, not the page calling the API.
     */
    @GetMapping("/instagram/callback")
    ResponseEntity<Void> callback(@AuthenticationPrincipal CurrentUser user, HttpSession session,
                                  @RequestParam(required = false) String code,
                                  @RequestParam(required = false) String state,
                                  @RequestParam(required = false) String error) {
        Object expected = session.getAttribute(STATE_ATTRIBUTE);
        // A state works once, so an old Instagram link can't be replayed.
        session.removeAttribute(STATE_ATTRIBUTE);

        // EX-F19.4.1: she cancelled or refused on Instagram. Nothing is saved.
        if (error != null) {
            return result("denied");
        }
        if (code == null || code.isBlank() || !sameState(expected, state)) {
            return result("expired");
        }
        try {
            service.connectInstagram(user.getId(), withoutTrailingMarker(code));
            return result("connected");
        } catch (SocialAccountTakenException | DataIntegrityViolationException e) {
            return result("taken");
        } catch (SocialConnectionUnavailableException e) {
            return result("unavailable");
        } catch (SocialPlatformException | CreatorProfileMissingException e) {
            // The page only says "try again"; this is where the real reason can be found. The message never has a
            // token, a URL or Meta's own text (InstagramClientImpl).
            LOGGER.warn("Connecting Instagram failed for creator {}: {}", user.getId(), e.getMessage());
            return result("failed");
        }
    }

    @DeleteMapping("/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void disconnect(@AuthenticationPrincipal CurrentUser user, @PathVariable long accountId) {
        service.disconnect(user.getId(), accountId);
    }

    private static ResponseEntity<Void> result(String outcome) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(RESULT_PAGE + outcome)).build();
    }

    private static String newState() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Compared in constant time, so the answer time doesn't leak how much of a guess was right. */
    private static boolean sameState(Object expected, String received) {
        return expected instanceof String s && received != null
                && MessageDigest.isEqual(s.getBytes(StandardCharsets.UTF_8), received.getBytes(StandardCharsets.UTF_8));
    }

    /** Instagram's docs: the code may end with "#_", which is not part of it. */
    private static String withoutTrailingMarker(String code) {
        return code.endsWith("#_") ? code.substring(0, code.length() - 2) : code;
    }
}
