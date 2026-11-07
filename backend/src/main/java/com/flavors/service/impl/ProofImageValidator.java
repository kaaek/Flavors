package com.flavors.service.impl;

import com.flavors.exception.InvalidDeliveryProofException;
import com.flavors.service.ProofUpload;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Strict, dependency-free validation for the raster screenshot formats the browser can safely display. */
final class ProofImageValidator {

    static final int MAX_BYTES = 5 * 1024 * 1024;

    private ProofImageValidator() {
    }

    /** Shares the upload's array (see {@link ProofUpload}); nothing modifies it after validation. */
    record ValidatedImage(byte[] bytes, String contentType, String extension) {
    }

    static ValidatedImage validate(ProofUpload upload) {
        byte[] bytes = upload.bytes();
        if (bytes.length == 0) {
            throw invalid("is empty");
        }
        if (bytes.length > MAX_BYTES) {
            throw invalid("is larger than 5 MB");
        }

        Type actual = detect(bytes);
        if (actual == null) {
            throw invalid("must be a PNG, JPEG, GIF or WebP image");
        }
        String declared = upload.declaredContentType();
        if (declared != null && !declared.isBlank() && !actual.matches(declared)) {
            throw invalid("does not match its declared image type");
        }
        return new ValidatedImage(bytes, actual.contentType, actual.extension);
    }

    private static InvalidDeliveryProofException invalid(String reason) {
        // Never reflect an untrusted client filename into an HTTP error or log message.
        return new InvalidDeliveryProofException("A screenshot " + reason + ".");
    }

    private static Type detect(byte[] bytes) {
        if (startsWith(bytes, new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})) {
            return Type.PNG;
        }
        if (startsWith(bytes, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})) {
            return Type.JPEG;
        }
        if (startsWith(bytes, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                || startsWith(bytes, "GIF89a".getBytes(StandardCharsets.US_ASCII))) {
            return Type.GIF;
        }
        if (bytes.length >= 12
                && Arrays.equals(Arrays.copyOfRange(bytes, 0, 4), "RIFF".getBytes(StandardCharsets.US_ASCII))
                && Arrays.equals(Arrays.copyOfRange(bytes, 8, 12), "WEBP".getBytes(StandardCharsets.US_ASCII))) {
            return Type.WEBP;
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length && Arrays.equals(Arrays.copyOf(bytes, prefix.length), prefix);
    }

    private enum Type {
        PNG("image/png", "png"),
        JPEG("image/jpeg", "jpg"),
        GIF("image/gif", "gif"),
        WEBP("image/webp", "webp");

        private final String contentType;
        private final String extension;

        Type(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        boolean matches(String declared) {
            return contentType.equalsIgnoreCase(declared.trim())
                    || (this == JPEG && "image/jpg".equalsIgnoreCase(declared.trim()));
        }
    }
}
