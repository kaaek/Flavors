package com.flavors.platform.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The caller, built from the claims of the access token that identity issued ({@code sub}, {@code role},
 * {@code mfa}). Controllers receive it with {@code @AuthenticationPrincipal CurrentUser user}.
 *
 * <p>Unlike the monolith's session-stored {@code CurrentUser}, it is rebuilt from the token on every
 * request and never serialized, so its fields can change freely.
 *
 * @param id    the user's id ({@code sub})
 * @param email the {@code email} claim when identity includes one, otherwise null
 * @param role  the account type ({@code role})
 * @param mfa   true when this token was issued after the second factor ({@code mfa}); only meaningful
 *              for admins
 */
public record CurrentUser(long id, String email, Role role, boolean mfa) {

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_MFA = "mfa";
    public static final String CLAIM_EMAIL = "email";
    /** Granted to an admin only once the second factor passed (rule at the monolith's SecurityConfig). */
    public static final String ADMIN_MFA_AUTHORITY = "ROLE_ADMIN_MFA";

    public static CurrentUser fromJwt(Jwt jwt) {
        long id = Long.parseLong(jwt.getSubject());
        Role role = Role.valueOf(jwt.getClaimAsString(CLAIM_ROLE));
        boolean mfa = Boolean.TRUE.equals(jwt.getClaim(CLAIM_MFA));
        return new CurrentUser(id, jwt.getClaimAsString(CLAIM_EMAIL), role, mfa);
    }

    /** Same meaning as the monolith's {@code CurrentUser.getUsername()}: what the audit log shows as actor. */
    public String getUsername() {
        return email != null ? email : "user:" + id;
    }

    public long getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    /** {@code ROLE_<role>}, plus {@code ROLE_ADMIN_MFA} only for an admin whose token has the mfa claim set. */
    public List<GrantedAuthority> authorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(role.authority()));
        if (role == Role.ADMIN && mfa) {
            authorities.add(new SimpleGrantedAuthority(ADMIN_MFA_AUTHORITY));
        }
        return List.copyOf(authorities);
    }
}
