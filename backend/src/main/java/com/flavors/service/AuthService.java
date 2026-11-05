package com.flavors.service;

import com.flavors.dto.CurrentUser;

public interface AuthService {

    CurrentUser authenticate(String email, String password);

    void recordLogout(CurrentUser user);
}
