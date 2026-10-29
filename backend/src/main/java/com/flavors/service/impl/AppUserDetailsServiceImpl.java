package com.flavors.service.impl;

import com.flavors.dto.CurrentUser;
import com.flavors.entity.AppUser;
import com.flavors.enums.UserStatus;
import com.flavors.repository.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads accounts for Spring Security's login check. Spring compares the password with the
 * PasswordEncoder from SecurityConfig; the login endpoint itself comes with UC-F19.3.
 */
@Service
public class AppUserDetailsServiceImpl implements UserDetailsService {

    private final AppUserRepository users;

    public AppUserDetailsServiceImpl(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUser loadUserByUsername(String email) {
        AppUser user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account with this email"));
        // A suspended account loads as disabled, so Spring Security refuses the login with a
        // DisabledException; UC-F19.3 turns that into "your account is suspended" (EX-F19.3.2).
        return new CurrentUser(user.getId(), user.getEmail(), user.getRole(), user.getPasswordHash(),
                user.getStatus() == UserStatus.ACTIVE);
    }
}
