package com.flavors.controller;

import com.flavors.dto.CurrentUser;
import com.flavors.service.DeliveryProofService;
import com.flavors.service.ProofFileContent;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated proxy for private screenshots; storage credentials and object keys never reach the browser. */
@RestController
@RequestMapping("/api/proof-files")
public class ProofFileController {

    private final DeliveryProofService service;

    public ProofFileController(DeliveryProofService service) {
        this.service = service;
    }

    @GetMapping("/{fileId}")
    ResponseEntity<byte[]> download(@AuthenticationPrincipal CurrentUser user, @PathVariable long fileId) {
        ProofFileContent file = service.download(user.getId(), user.getRole(), fileId);
        byte[] bytes = file.bytes();
        MediaType contentType = MediaType.parseMediaType(file.contentType());
        String extension = switch (file.contentType()) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "bin";
        };
        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(bytes.length)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("proof-" + fileId + "." + extension).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(bytes);
    }
}
