package com.flavors.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static com.flavors.support.TestUsers.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flavors.dto.BrandProfileResponse;
import com.flavors.platform.security.CurrentUser;
import com.flavors.dto.UpdateBrandProfileRequest;
import com.flavors.platform.security.Role;
import com.flavors.support.WebSliceConfig;
import com.flavors.service.BrandProfileService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BrandProfileController.class)
@Import({WebSliceConfig.class})
class BrandProfileControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    BrandProfileService profile;

    CurrentUser brand = new CurrentUser(7L, "brand@example.com", Role.BRAND, false);
    CurrentUser creator = new CurrentUser(5L, "creator@example.com", Role.CREATOR, false);

    @Test
    void brandReadsItsProfile() throws Exception {
        when(profile.get(7)).thenReturn(new BrandProfileResponse("Karim's Kitchen", "Burgers", "Smash"));

        mvc.perform(get("/api/brand/profile").with(user(brand)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Karim's Kitchen"))
                .andExpect(jsonPath("$.cuisine").value("Burgers"))
                .andExpect(jsonPath("$.description").value("Smash"));
    }

    @Test
    void brandUpdatesItsOwnProfile() throws Exception {
        when(profile.update(7, new UpdateBrandProfileRequest("Burgers", "Smash")))
                .thenReturn(new BrandProfileResponse("Karim's Kitchen", "Burgers", "Smash"));

        mvc.perform(patch("/api/brand/profile")
                        .with(user(brand))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cuisine\":\"Burgers\",\"description\":\"Smash\"}")
                        .cookie(new Cookie("XSRF-TOKEN", "token"))
                        .header("X-XSRF-TOKEN", "token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuisine").value("Burgers"));
        verify(profile).update(7, new UpdateBrandProfileRequest("Burgers", "Smash"));
    }

    @Test
    void tooLongCuisineIs400AndCreatorIs403() throws Exception {
        mvc.perform(patch("/api/brand/profile")
                        .with(user(brand))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cuisine\":\"" + "x".repeat(51) + "\"}")
                        .cookie(new Cookie("XSRF-TOKEN", "token"))
                        .header("X-XSRF-TOKEN", "token"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/brand/profile").with(user(creator))).andExpect(status().isForbidden());

        verifyNoInteractions(profile);
    }
}
