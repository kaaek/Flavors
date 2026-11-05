package com.flavors.mapper;

import com.flavors.dto.CurrentUser;
import com.flavors.dto.MeResponse;
import org.springframework.stereotype.Component;

/** Converts the logged-in user into what the API sends out. Controllers use mappers, so they never build DTOs by hand. */
@Component
public class MeMapper {

    public MeResponse toResponse(CurrentUser user) {
        return new MeResponse(user.getId(), user.getUsername(), user.getRole());
    }
}
