package com.flavors.controller;

import com.flavors.dto.CurrentUser;
import com.flavors.dto.DeliveryProofResponse;
import com.flavors.dto.ProofDraftRequest;
import com.flavors.exception.InvalidDeliveryProofException;
import com.flavors.service.DeliveryProofService;
import com.flavors.service.ProofUpload;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Role-scoped delivery-proof endpoints. SecurityConfig enforces the role prefix before these run. */
@RestController
@RequestMapping("/api")
public class DeliveryProofController {

    private final DeliveryProofService service;

    public DeliveryProofController(DeliveryProofService service) {
        this.service = service;
    }

    @GetMapping("/creator/campaigns/{campaignId}/proof")
    DeliveryProofResponse creatorProof(@AuthenticationPrincipal CurrentUser user,
                                       @PathVariable long campaignId) {
        return service.getForCreator(user.getId(), campaignId);
    }

    @PutMapping(value = "/creator/campaigns/{campaignId}/proof/draft",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    DeliveryProofResponse saveDraft(@AuthenticationPrincipal CurrentUser user,
                                    @PathVariable long campaignId,
                                    @RequestPart(value = "proof", required = false) ProofDraftRequest proof,
                                    @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return service.saveDraft(user.getId(), campaignId, proof, uploads(files));
    }

    @PostMapping(value = "/creator/campaigns/{campaignId}/proof/submit",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    DeliveryProofResponse submit(@AuthenticationPrincipal CurrentUser user,
                                 @PathVariable long campaignId,
                                 @RequestPart(value = "proof", required = false) ProofDraftRequest proof,
                                 @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return service.submit(user.getId(), campaignId, proof, uploads(files));
    }

    @GetMapping("/brand/campaigns/{campaignId}/proof")
    DeliveryProofResponse brandProof(@AuthenticationPrincipal CurrentUser user,
                                     @PathVariable long campaignId) {
        return service.getForBrand(user.getId(), campaignId);
    }

    @GetMapping("/admin/campaigns/{campaignId}/proof")
    DeliveryProofResponse adminProof(@PathVariable long campaignId) {
        return service.getForAdmin(campaignId);
    }

    private static List<ProofUpload> uploads(List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        return files.stream().map(file -> {
            try {
                return new ProofUpload(file.getOriginalFilename(), file.getContentType(), file.getBytes());
            } catch (IOException e) {
                throw new InvalidDeliveryProofException("A screenshot could not be read.");
            }
        }).toList();
    }
}
