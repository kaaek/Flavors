package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.exception.InvalidDeliveryProofException;
import com.flavors.service.ProofUpload;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ProofImageValidatorTest {

    @Test
    void recognizesSupportedImageSignatures() {
        assertThat(validate(bytes(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a), "image/png").contentType())
                .isEqualTo("image/png");
        assertThat(validate(bytes(0xff, 0xd8, 0xff, 0x00), "image/jpeg").extension()).isEqualTo("jpg");
        assertThat(validate("GIF89a rest".getBytes(StandardCharsets.US_ASCII), "image/gif").extension())
                .isEqualTo("gif");
        assertThat(validate("RIFFxxxxWEBPrest".getBytes(StandardCharsets.US_ASCII), "image/webp").extension())
                .isEqualTo("webp");
    }

    @Test
    void rejectsEmptyOversizedUnsupportedAndSpoofedFiles() {
        assertThatThrownBy(() -> validate(new byte[0], "image/png"))
                .isInstanceOf(InvalidDeliveryProofException.class).hasMessageContaining("empty");
        assertThatThrownBy(() -> validate(new byte[ProofImageValidator.MAX_BYTES + 1], "image/png"))
                .isInstanceOf(InvalidDeliveryProofException.class).hasMessageContaining("larger than 5 MB");
        assertThatThrownBy(() -> validate("not an image".getBytes(StandardCharsets.UTF_8), "image/png"))
                .isInstanceOf(InvalidDeliveryProofException.class).hasMessageContaining("must be a PNG");
        assertThatThrownBy(() -> validate(bytes(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a), "image/jpeg"))
                .isInstanceOf(InvalidDeliveryProofException.class).hasMessageContaining("declared image type");
    }

    @Test
    void acceptsAnImageAtExactlyFiveMebibytes() {
        byte[] maximum = new byte[ProofImageValidator.MAX_BYTES];
        byte[] signature = bytes(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
        System.arraycopy(signature, 0, maximum, 0, signature.length);

        assertThat(validate(maximum, "image/png").bytes()).hasSize(ProofImageValidator.MAX_BYTES);
    }

    private static ProofImageValidator.ValidatedImage validate(byte[] bytes, String contentType) {
        return ProofImageValidator.validate(new ProofUpload("proof", contentType, bytes));
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
