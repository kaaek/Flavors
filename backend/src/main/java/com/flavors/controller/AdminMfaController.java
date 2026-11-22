package com.flavors.controller;

import com.flavors.dto.CurrentUser;
import com.flavors.dto.MeResponse;
import com.flavors.mapper.MeMapper;
import com.flavors.service.impl.AdminMfaService;
import com.flavors.service.impl.AdminTotp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** EEC-75: only a pending, password-verified admin session can reach these public paths. */
@RestController
@Validated
public class AdminMfaController {
    public static final String CHALLENGE = "ADMIN_MFA_PENDING";
    public static final String ENROLL_SECRET = "ADMIN_MFA_ENROLL_CIPHERTEXT";
    private static final long MAX_AGE = 300;
    private static final int MAX_ATTEMPTS = 5;

    public record PendingAdmin(long userId, String email, long createdAt, int failures) implements Serializable { }
    public record MfaCode(@NotBlank String code) { }
    public record RecoveryCode(@NotBlank String recoveryCode) { }
    public record Enrollment(String manualKey, String otpauthUri) { }
    public record EnrollmentComplete(MeResponse user, List<String> recoveryCodes) { }

    private final AdminMfaService mfa;
    private final AdminTotp totp;
    private final SecurityContextRepository contexts;
    private final MeMapper mapper;

    public AdminMfaController(AdminMfaService mfa, AdminTotp totp,
            SecurityContextRepository contexts, MeMapper mapper) {
        this.mfa = mfa; this.totp = totp; this.contexts = contexts; this.mapper = mapper;
    }

    /** Store password-verified identity only; NO authenticated Spring Security principal yet. */
    public static void startChallenge(HttpServletRequest request, CurrentUser user) {
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) oldSession.invalidate();
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute(CHALLENGE, new PendingAdmin(user.getId(), user.getUsername(),
                Instant.now().getEpochSecond(), 0));
        session.removeAttribute(ENROLL_SECRET);
        SecurityContextHolder.clearContext();
    }

    @PostMapping("/api/public/auth/mfa/enroll/start")
    ResponseEntity<Enrollment> start(HttpServletRequest request) {
        PendingAdmin pending = validPending(request);
        if (mfa.enrolled(pending.userId())) throw new IllegalStateException("MFA is already enrolled");
        HttpSession session = request.getSession(false);
        Object existing = session.getAttribute(ENROLL_SECRET);
        String secret;
        if (existing instanceof String ciphertext) {
            // Reopening setup must not rotate the key or extend the enrollment deadline.
            secret = totp.decrypt(ciphertext);
        } else {
            // Give first-time enrollment its own five-minute window, starting now.
            // Only the first start extends the password-verified challenge; repeated
            // requests cannot keep extending it indefinitely.
            secret = mfa.createEnrollmentSecret();
            session.setAttribute(ENROLL_SECRET, totp.encrypt(secret));
            session.setAttribute(CHALLENGE,
                    new PendingAdmin(pending.userId(), pending.email(),
                            Instant.now().getEpochSecond(), pending.failures()));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new Enrollment(mfa.manualKey(secret), mfa.provisioningUri(pending.email(), secret)));
    }

    @PostMapping("/api/public/auth/mfa/enroll/confirm")
    ResponseEntity<EnrollmentComplete> confirm(@Valid @RequestBody MfaCode input,
            HttpServletRequest request, HttpServletResponse response) {
        PendingAdmin pending = validPending(request);
        HttpSession session = request.getSession(false);
        Object cipher = session.getAttribute(ENROLL_SECRET);
        if (!(cipher instanceof String)) throw new IllegalArgumentException("Start enrollment first");
        // Count attempts BEFORE verification; successful enrollment clears the account limiter.
        failBudget(request, pending);
        String secret = totp.decrypt((String) cipher);
        List<String> recoveryCodes = mfa.enroll(pending.userId(), secret, input.code());
        CurrentUser admin = complete(request, response, pending);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new EnrollmentComplete(mapper.toResponse(admin), recoveryCodes));
    }

    @PostMapping("/api/public/auth/mfa/verify")
    MeResponse verify(@Valid @RequestBody MfaCode input, HttpServletRequest request,
            HttpServletResponse response) {
        PendingAdmin pending = validPending(request);
        if (!mfa.enrolled(pending.userId())) throw new IllegalArgumentException("Enroll an authenticator first");
        failBudget(request, pending);
        if (!mfa.verifyTotp(pending.userId(), input.code())) throw new IllegalArgumentException("Invalid authentication code");
        return mapper.toResponse(complete(request, response, pending));
    }

    @PostMapping("/api/public/auth/mfa/recovery")
    MeResponse recovery(@Valid @RequestBody RecoveryCode input, HttpServletRequest request,
            HttpServletResponse response) {
        PendingAdmin pending = validPending(request);
        if (!mfa.enrolled(pending.userId())) throw new IllegalArgumentException("MFA is not enrolled");
        failBudget(request, pending);
        if (!mfa.verifyRecovery(pending.userId(), input.recoveryCode())) throw new IllegalArgumentException("Invalid recovery code");
        return mapper.toResponse(complete(request, response, pending));
    }

    private PendingAdmin validPending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute(CHALLENGE);
        if (!(value instanceof PendingAdmin pending) || pending.failures() >= MAX_ATTEMPTS
                || Instant.now().getEpochSecond() - pending.createdAt() > MAX_AGE) {
            if (session != null) { session.removeAttribute(CHALLENGE); session.removeAttribute(ENROLL_SECRET); }
            throw new IllegalArgumentException("MFA challenge expired. Please log in again.");
        }
        mfa.checkAdmin(pending.userId());
        return pending;
    }

    private void failBudget(HttpServletRequest request, PendingAdmin pending) {
        // Failures count even if the following verification call throws.
        if (!mfa.allowAttempt(pending.userId())) {
            request.getSession(false).removeAttribute(CHALLENGE);
            throw new IllegalArgumentException("Too many attempts. Please try again later.");
        }
        request.getSession(false).setAttribute(CHALLENGE,
                new PendingAdmin(pending.userId(), pending.email(), pending.createdAt(), pending.failures() + 1));
    }

    private CurrentUser complete(HttpServletRequest request, HttpServletResponse response, PendingAdmin pending) {
        var user = mfa.checkAdmin(pending.userId());
        HttpSession session = request.getSession(false);
        session.removeAttribute(CHALLENGE);
        session.removeAttribute(ENROLL_SECRET);
        request.changeSessionId();
        CurrentUser current = new CurrentUser(user.getId(), user.getEmail(), user.getRole(), null, true);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(current, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_ADMIN_MFA")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return current;
    }
}