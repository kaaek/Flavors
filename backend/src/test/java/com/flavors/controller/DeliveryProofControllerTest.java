package com.flavors.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flavors.config.SecurityConfig;
import com.flavors.dto.CurrentUser;
import com.flavors.dto.DeliveryProofResponse;
import com.flavors.enums.CampaignStatus;
import com.flavors.enums.ProofSubmissionStatus;
import com.flavors.enums.Role;
import com.flavors.exception.DeliveryProofConflictException;
import com.flavors.exception.DeliveryProofNotFoundException;
import com.flavors.exception.InvalidDeliveryProofException;
import com.flavors.service.DeliveryProofService;
import com.flavors.service.ProofFileContent;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({DeliveryProofController.class, ProofFileController.class})
@Import(SecurityConfig.class)
class DeliveryProofControllerTest {

    private static final CurrentUser LARA =
            new CurrentUser(7, "lara@example.com", Role.CREATOR, "{noop}x", true);
    private static final CurrentUser KARIM =
            new CurrentUser(9, "karim@example.com", Role.BRAND, "{noop}x", true);
    private static final CurrentUser ADMIN =
            new CurrentUser(11, "admin@example.com", Role.ADMIN, "{noop}x", true);

    private static final DeliveryProofResponse RESPONSE = new DeliveryProofResponse(
            42, "Summer burgers", CampaignStatus.ACTIVE, null,
            List.of(new DeliveryProofResponse.Submission(5, 1, ProofSubmissionStatus.DRAFT,
                    Instant.parse("2026-11-07T08:00:00Z"), null,
                    List.of("https://instagram.com/p/abc"),
                    List.of(new DeliveryProofResponse.File(8, "image/png", 4, "/api/proof-files/8")))));

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DeliveryProofService service;

    @Test
    void creatorLoadsOnlyHerCampaignProof() throws Exception {
        given(service.getForCreator(7, 42)).willReturn(RESPONSE);

        mvc.perform(get("/api/creator/campaigns/42/proof").with(user(LARA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId").value(42))
                .andExpect(jsonPath("$.campaignName").value("Summer burgers"))
                .andExpect(jsonPath("$.submissions[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.submissions[0].files[0].storageKey").doesNotExist())
                .andExpect(jsonPath("$.submissions[0].files[0].downloadUrl").value("/api/proof-files/8"));
    }

    @Test
    void saveDraftReadsJsonAndImagesFromMultipart() throws Exception {
        given(service.saveDraft(eq(7L), eq(42L), any(), any())).willReturn(RESPONSE);
        MockMultipartFile proof = new MockMultipartFile("proof", "", "application/json",
                "{\"links\":[\"https://instagram.com/p/abc\"],\"retainedFileIds\":[3]}".getBytes());
        MockMultipartFile image = new MockMultipartFile("files", "proof.png", "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47});

        mvc.perform(multipart("/api/creator/campaigns/42/proof/draft")
                        .file(proof).file(image).with(user(LARA)).with(csrf())
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissions[0].round").value(1));

        ArgumentCaptor<com.flavors.dto.ProofDraftRequest> request =
                ArgumentCaptor.forClass(com.flavors.dto.ProofDraftRequest.class);
        ArgumentCaptor<List<com.flavors.service.ProofUpload>> uploads = ArgumentCaptor.forClass(List.class);
        then(service).should().saveDraft(eq(7L), eq(42L), request.capture(), uploads.capture());
        assertThat(request.getValue().links()).containsExactly("https://instagram.com/p/abc");
        assertThat(request.getValue().retainedFileIds()).containsExactly(3L);
        assertThat(uploads.getValue()).singleElement().satisfies(upload -> {
            assertThat(upload.originalFilename()).isEqualTo("proof.png");
            assertThat(upload.declaredContentType()).isEqualTo("image/png");
        });
    }

    @Test
    void finalSubmissionReturnsCreated() throws Exception {
        given(service.submit(eq(7L), eq(42L), any(), any())).willReturn(RESPONSE);
        MockMultipartFile proof = new MockMultipartFile("proof", "", "application/json",
                "{\"links\":[\"https://instagram.com/p/abc\"]}".getBytes());

        mvc.perform(multipart("/api/creator/campaigns/42/proof/submit")
                        .file(proof).with(user(LARA)).with(csrf()))
                .andExpect(status().isCreated());
    }

    @Test
    void brandAndAdminUseReadOnlyRoleEndpoints() throws Exception {
        given(service.getForBrand(9, 42)).willReturn(RESPONSE);
        given(service.getForAdmin(42)).willReturn(RESPONSE);

        mvc.perform(get("/api/brand/campaigns/42/proof").with(user(KARIM)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/campaigns/42/proof").with(user(ADMIN)))
                .andExpect(status().isOk());

        then(service).should().getForBrand(9, 42);
        then(service).should().getForAdmin(42);
    }

    @Test
    void wrongRoleAndAnonymousRequestsNeverReachService() throws Exception {
        mvc.perform(get("/api/creator/campaigns/42/proof").with(user(KARIM)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/brand/campaigns/42/proof").with(user(LARA)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/campaigns/42/proof").with(user(LARA)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/creator/campaigns/42/proof"))
                .andExpect(status().isUnauthorized());

        then(service).shouldHaveNoInteractions();
    }

    @Test
    void writeRequiresCsrf() throws Exception {
        mvc.perform(multipart("/api/creator/campaigns/42/proof/submit").with(user(LARA)))
                .andExpect(status().isForbidden());

        then(service).shouldHaveNoInteractions();
    }

    @Test
    void domainFailuresUseStableProblemDetails() throws Exception {
        given(service.getForCreator(7, 1)).willThrow(new DeliveryProofNotFoundException());
        given(service.getForCreator(7, 2)).willThrow(new InvalidDeliveryProofException("Bad link."));
        given(service.getForCreator(7, 3)).willThrow(new DeliveryProofConflictException("Already sent."));

        mvc.perform(get("/api/creator/campaigns/1/proof").with(user(LARA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Delivery proof not found"));
        mvc.perform(get("/api/creator/campaigns/2/proof").with(user(LARA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Bad link."));
        mvc.perform(get("/api/creator/campaigns/3/proof").with(user(LARA)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Already sent."));
    }

    @Test
    void authorizedScreenshotIsPrivateAndCannotBeSniffed() throws Exception {
        given(service.download(7, Role.CREATOR, 8))
                .willReturn(new ProofFileContent(new byte[] {1, 2, 3}, "image/png"));

        mvc.perform(get("/api/proof-files/8").with(user(LARA)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(new byte[] {1, 2, 3}))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"proof-8.png\""));
    }
}
