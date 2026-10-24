package com.flavors.common.security;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The logged-in user, as Spring Security keeps it in the session. Controllers get it with
 * {@code @AuthenticationPrincipal CurrentUser user}.
 *
 * <p>It is stored in the session table (Spring Session JDBC) with Java serialization, so keep it
 * small and change its fields with care: sessions saved by an older version must still load.
 */
public final class CurrentUser implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long id;
    private final String email;
    private final Role role;
    private final boolean enabled;
    private String passwordHash;

    public CurrentUser(long id, String email, Role role, String passwordHash, boolean enabled) {
        this.id = id;
        this.email = email;
        this.role = role;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
    }

    public long getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    /** False for suspended accounts, so Spring Security refuses their login (FR-F19.12). */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /** Called by Spring Security after login, so the password hash is not kept in the session. */
    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
