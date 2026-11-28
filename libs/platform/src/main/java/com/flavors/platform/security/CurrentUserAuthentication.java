package com.flavors.platform.security;

import java.util.Objects;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

/** An authenticated request: the principal is a {@link CurrentUser}, the credentials the validated token. */
public final class CurrentUserAuthentication extends AbstractAuthenticationToken {

    private final CurrentUser user;
    private final Jwt token;

    public CurrentUserAuthentication(CurrentUser user, Jwt token) {
        super(user.authorities());
        this.user = Objects.requireNonNull(user);
        this.token = Objects.requireNonNull(token);
        setAuthenticated(true);
    }

    @Override
    public Object getPrincipal() {
        return user;
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    public Jwt getToken() {
        return token;
    }

    @Override
    public String getName() {
        return String.valueOf(user.id());
    }
}
