package com.flavors.controller;

import com.flavors.dto.CurrentUser;
import com.flavors.dto.LoginRequest;
import com.flavors.dto.MeResponse;
import com.flavors.dto.MessageResponse;
import com.flavors.dto.PasswordResetConfirmRequest;
import com.flavors.dto.PasswordResetRequest;
import com.flavors.mapper.MeMapper;
import com.flavors.service.AuthService;
import com.flavors.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private static final String RESET_REQUEST_MESSAGE =
            "If an account uses that email, a password-reset link has been sent.";

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final SecurityContextRepository securityContexts;
    private final MeMapper mapper;

    public AuthController(AuthService authService, PasswordResetService passwordResetService,
                          SecurityContextRepository securityContexts, MeMapper mapper) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.securityContexts = securityContexts;
        this.mapper = mapper;
    }

    @PostMapping("/api/public/auth/login")
    MeResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
                     HttpServletResponse httpResponse) {
        CurrentUser user = authService.authenticate(request.email(), request.password());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                user, null, user.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        httpRequest.getSession(true);
        httpRequest.changeSessionId();
        securityContexts.saveContext(context, httpRequest, httpResponse);
        return mapper.toResponse(user);
    }

    @PostMapping("/api/auth/logout")
    MessageResponse logout(@AuthenticationPrincipal CurrentUser user, Authentication authentication,
                           HttpServletRequest request, HttpServletResponse response) {
        authService.recordLogout(user);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return new MessageResponse("Logged out.");
    }

    @PostMapping("/api/public/auth/password-reset/request")
    MessageResponse requestReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.email());
        return new MessageResponse(RESET_REQUEST_MESSAGE);
    }

    @PostMapping("/api/public/auth/password-reset/confirm")
    MessageResponse resetPassword(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.resetPassword(request.token(), request.password());
        return new MessageResponse("Password changed. You can now log in.");
    }
}
