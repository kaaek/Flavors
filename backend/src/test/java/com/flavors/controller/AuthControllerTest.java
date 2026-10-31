package com.flavors.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flavors.config.SecurityConfig;
import com.flavors.dto.CurrentUser;
import com.flavors.enums.Role;
import com.flavors.exception.AuthExceptionHandler;
import com.flavors.exception.InvalidCredentialsException;
import com.flavors.exception.SuspendedAccountException;
import com.flavors.mapper.MeMapper;
import com.flavors.service.AuthService;
import com.flavors.service.PasswordResetService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, AuthExceptionHandler.class, MeMapper.class})
class AuthControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    PasswordResetService passwordResetService;

    @Test
    void successfulBrandLoginReturnsTheRole() throws Exception {
        when(authService.authenticate("karim@example.com", "right-password"))
                .thenReturn(new CurrentUser(7, "karim@example.com", Role.BRAND, null, true));

        mvc.perform(post("/api/public/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"karim@example.com","password":"right-password"}
                                """)
                        .cookie(new Cookie("XSRF-TOKEN", "token"))
                        .header("X-XSRF-TOKEN", "token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.role").value("BRAND"));
    }

    @Test
    void wrongCredentialsReturnOneGenericMessage() throws Exception {
        when(authService.authenticate(anyString(), anyString())).thenThrow(new InvalidCredentialsException());

        mvc.perform(post("/api/public/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"karim@example.com","password":"wrong"}
                                """)
                        .cookie(new Cookie("XSRF-TOKEN", "token"))
                        .header("X-XSRF-TOKEN", "token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("The email or password is incorrect."));
    }

    @Test
    void suspendedAccountIsExplained() throws Exception {
        when(authService.authenticate(anyString(), anyString())).thenThrow(new SuspendedAccountException());

        mvc.perform(post("/api/public/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"karim@example.com","password":"right-password"}
                                """)
                        .cookie(new Cookie("XSRF-TOKEN", "token"))
                        .header("X-XSRF-TOKEN", "token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("This account is suspended."));
    }

    @Test
    void logoutNeedsLoginAndEndsTheSession() throws Exception {
        CurrentUser karim = new CurrentUser(7, "karim@example.com", Role.BRAND, null, true);

        mvc.perform(post("/api/auth/logout")
                        .with(user(karim))
                        .cookie(new Cookie("XSRF-TOKEN", "token"))
                        .header("X-XSRF-TOKEN", "token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out."));

        verify(authService).recordLogout(karim);
    }

    @Test
    void resetRequestAlwaysReturnsTheGenericMessage() throws Exception {
        mvc.perform(post("/api/public/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody@example.com"}
                                """)
                        .cookie(new Cookie("XSRF-TOKEN", "token"))
                        .header("X-XSRF-TOKEN", "token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "If an account uses that email, a password-reset link has been sent."));

        verify(passwordResetService).requestReset("nobody@example.com");
    }

    @Test
    void resetConfirmationChangesThePassword() throws Exception {
        mvc.perform(post("/api/public/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"one-time-token","password":"new-password"}
                                """)
                        .cookie(new Cookie("XSRF-TOKEN", "token"))
                        .header("X-XSRF-TOKEN", "token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed. You can now log in."));

        verify(passwordResetService).resetPassword("one-time-token", "new-password");
    }
}
