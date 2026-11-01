package com.flavors.controller;
import com.flavors.mapper.SavedCreatorMapper;
import com.flavors.config.SecurityConfig;
import com.flavors.dto.CurrentUser;
import com.flavors.enums.Role;
import com.flavors.service.SavedCreatorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.flavors.exception.ApiExceptionHandler;
import com.flavors.exception.SavedCreatorNotFoundException;
import com.flavors.exception.CreatorNotApprovedException;
import static org.mockito.Mockito.doThrow;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SavedCreatorController.class)
@Import({SecurityConfig.class, SavedCreatorMapper.class,ApiExceptionHandler.class})
class SavedCreatorControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SavedCreatorService service;

    CurrentUser brand = new CurrentUser(
            10L, "brand@example.com", Role.BRAND, "password", true
    );

    CurrentUser creator = new CurrentUser(
            5L, "creator@example.com", Role.CREATOR, "password", true
    );

    @Test
    void brandCanSaveCreator() throws Exception {
        mvc.perform(post("/api/brand/saved-creators/5")
                        .with(user(brand))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(service).saveCreator(10L, 5L);
    }

    @Test
    void brandCanRemoveCreator() throws Exception {
        mvc.perform(delete("/api/brand/saved-creators/5")
                        .with(user(brand))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(service).removeCreator(10L, 5L);
    }

    @Test
    void unauthenticatedUserCannotSave() throws Exception {
        mvc.perform(post("/api/brand/saved-creators/5")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void creatorCannotSave() throws Exception {
        mvc.perform(post("/api/brand/saved-creators/5")
                        .with(user(creator))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void usesLoggedInBrandId() throws Exception {
        mvc.perform(post("/api/brand/saved-creators/8")
                        .with(user(brand))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(service).saveCreator(10L, 8L);
    }
    
    @Test
    void creatorNotFoundReturns404() throws Exception {
        doThrow(new SavedCreatorNotFoundException("Creator not found"))
                .when(service).saveCreator(10L, 99L);
    
        mvc.perform(post("/api/brand/saved-creators/99")
                        .with(user(brand))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }
    
    @Test
    void unapprovedCreatorReturns409() throws Exception {
        doThrow(new CreatorNotApprovedException())
                .when(service).saveCreator(10L, 5L);
    
        mvc.perform(post("/api/brand/saved-creators/5")
                        .with(user(brand))
                        .with(csrf()))
                .andExpect(status().isConflict());
    }
}
