package com.flavors.template;

import com.flavors.platform.idempotency.IdempotencyService;
import com.flavors.platform.security.CurrentUser;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sample endpoints that show the platform at work. They carry no business logic: delete them when you
 * copy the template, keep the tests' ideas.
 */
@RestController
class TemplateController {

    record WhoAmI(long id, String role, boolean mfa, List<String> authorities) {
    }

    private final TemplatePingService pings;
    private final IdempotencyService idempotency;

    TemplateController(TemplatePingService pings, IdempotencyService idempotency) {
        this.pings = pings;
        this.idempotency = idempotency;
    }

    /** Any signed-in user (a pending admin without the second factor is refused by the platform rules). */
    @GetMapping("/api/template/whoami")
    WhoAmI whoAmI(@AuthenticationPrincipal CurrentUser user) {
        return new WhoAmI(user.id(), user.role().name(), user.mfa(),
                user.authorities().stream().map(Object::toString).toList());
    }

    /** Only an admin whose token has the mfa claim ({@code ROLE_ADMIN_MFA}). */
    @GetMapping("/api/admin/template/ping")
    String adminPing() {
        return "pong";
    }

    /**
     * Changes state: one audit row and one outbox event in a single transaction. With an
     * {@code Idempotency-Key} header a retry returns the first answer without writing again.
     */
    @PostMapping("/api/template/ping")
    ResponseEntity<TemplatePingService.PingResult> ping(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @jakarta.validation.Valid @RequestBody PingRequest request) {
        if (key == null) {
            return ResponseEntity.ok(pings.ping(request.note()));
        }
        return ResponseEntity.ok(idempotency.execute("template-ping", key, request.note(),
                () -> pings.ping(request.note()), TemplatePingService.PingResult.class));
    }

    record PingRequest(@jakarta.validation.constraints.NotBlank String note) {
    }
}
