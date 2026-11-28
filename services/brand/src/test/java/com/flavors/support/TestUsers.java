package com.flavors.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.flavors.platform.security.CurrentUser;
import com.flavors.platform.security.CurrentUserAuthentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Signs a request in as a platform {@link CurrentUser}, the way a validated access token would. */
public final class TestUsers {

    private TestUsers() {
    }

    public static RequestPostProcessor user(CurrentUser user) {
        Jwt token = Jwt.withTokenValue("test-token").header("alg", "RS256")
                .subject(String.valueOf(user.id())).claim("role", user.role().name()).build();
        return authentication(new CurrentUserAuthentication(user, token));
    }
}
