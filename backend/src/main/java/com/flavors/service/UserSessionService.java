package com.flavors.service;

public interface UserSessionService {

    void invalidateAll(String principalName);
}
