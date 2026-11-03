package com.flavors.service.impl;

import com.flavors.dto.CurrentUser;
import com.flavors.exception.InvalidCredentialsException;
import com.flavors.exception.SuspendedAccountException;
import com.flavors.service.AuditLogger;
import com.flavors.service.AuthService;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final AuditLogger auditLogger;

    public AuthServiceImpl(AuthenticationManager authenticationManager, AuditLogger auditLogger) {
        this.authenticationManager = authenticationManager;
        this.auditLogger = auditLogger;
    }

    @Override
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, SuspendedAccountException.class})
    public CurrentUser authenticate(String email, String password) {
        String normalizedEmail = email.trim();
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(normalizedEmail, password));
            return (CurrentUser) authentication.getPrincipal();
        } catch (DisabledException exception) {
            auditLogger.record("LOGIN_FAILED", "app_user", null,
                    Map.of("email", normalizedEmail, "reason", "SUSPENDED"));
            throw new SuspendedAccountException();
        } catch (AuthenticationException exception) {
            auditLogger.record("LOGIN_FAILED", "app_user", null,
                    Map.of("email", normalizedEmail, "reason", "BAD_CREDENTIALS"));
            throw new InvalidCredentialsException();
        }
    }

    @Override
    @Transactional
    public void recordLogout(CurrentUser user) {
        auditLogger.record("LOGOUT", "app_user", user.getId());
    }
}
