package com.flavors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flavors.exception.InvalidDeliveryProofException;
import com.flavors.service.ProofUpload;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.Test;

class ProofImageValidatorTest {

    @Test
    void acceptsRealImagesOfEverySupportedType() {
        assertThat(validate(TestImages.png(4, 3), "image/png").contentType()).isEqualTo("image/png");
        assertThat(validate(TestImages.jpeg(4, 3), "image/jpeg").extension()).isEqualTo("jpg");
        assertThat(validate(TestImages.jpeg(4, 3), "image/jpg").extension()).isEqualTo("jpg");
        assertThat(validate(TestImages.gif(4, 3), "image/gif").extension()).isEqualTo("gif");
        assertThat(validate(TestImages.WEBP_LOSSY, "image/webp").extension()).isEqualTo("webp");
        assertThat(validate(TestImages.WEBP_LOSSLESS, "image/webp").extension()).isEqualTo("webp");
        assertThat(validate(TestImages.WEBP_ALPHA, "image/webp").extension()).isEqualTo("webp");
        assertThat(validate(TestImages.WEBP_ANIMATED, "image/webp").extension()).isEqualTo("webp");
    }

    @Test
    void rejectsEmptyOversizedUnsupportedAndSpoofedFiles() {
        assertThatThrownBy(() -> validate(new byte[0], "image/png"))
                .isInstanceOf(InvalidDeliveryProofException.class).hasMessageContaining("empty");
        assertThatThrownBy(() -> validate(new byte[ProofImageValidator.MAX_BYTES + 1], "image/png"))
                .isInstanceOf(InvalidDeliveryProofException.class).hasMessageContaining("larger than 5 MB");
        assertThatThrownBy(() -> validate("not an image".getBytes(StandardCharsets.UTF_8), "image/png"))
                .isInstanceOf(InvalidDeliveryProofException.class).hasMessageContaining("must be a PNG");
        assertThatThrownBy(() -> validate(TestImages.png(2, 2), "image/jpeg"))
                .isInstanceOf(InvalidDeliveryProofException.class).hasMessageContaining("declared image type");
    }

    @Test
    void acceptsAnImageAtExactlyFiveMebibytes() {
        byte[] maximum = TestImages.pngOfSize(ProofImageValidator.MAX_BYTES);

        assertThat(validate(maximum, "image/png").bytes()).hasSize(ProofImageValidator.MAX_BYTES);
    }

    @Test
    void aValidSignatureAloneIsNotAnImage() {
        assertDamaged(bytes(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a), "image/png");
        assertDamaged(bytes(0xff, 0xd8, 0xff, 0x00), "image/jpeg");
        assertDamaged("GIF89a".getBytes(StandardCharsets.US_ASCII), "image/gif");
        assertDamaged("RIFFxxxxWEBPrest".getBytes(StandardCharsets.US_ASCII), "image/webp");
    }

    @Test
    void rejectsAnImageThatWasCutOffAnywhere() {
        byte[][] images = {TestImages.png(32, 24), TestImages.jpeg(32, 24), TestImages.gif(32, 24),
            TestImages.WEBP_LOSSY, TestImages.WEBP_LOSSLESS, TestImages.WEBP_ALPHA};
        String[] types = {"image/png", "image/jpeg", "image/gif", "image/webp", "image/webp", "image/webp"};
        for (int i = 0; i < images.length; i++) {
            byte[] image = images[i];
            // Down to the very last byte: PNG's IEND, JPEG's end marker, GIF's trailer, WebP's declared RIFF size.
            for (int cut : new int[] {image.length / 2, image.length - 10, image.length - 1}) {
                assertDamaged(Arrays.copyOf(image, cut), types[i]);
            }
        }
    }

    @Test
    void rejectsAPngWithADamagedChunk() {
        byte[] png = TestImages.png(32, 24);
        png[png.length / 2] ^= 0x01;

        assertDamaged(png, "image/png");
    }

    @Test
    void rejectsAPngWhoseDamagedDataStillHasValidChecksums() {
        assertDamaged(TestImages.pngWithDamagedDataAndValidChecksums(), "image/png");
    }

    @Test
    void rejectsAGifWithAnUnknownBlock() {
        byte[] gif = TestImages.gif(8, 8);
        // The trailer becomes an unknown block introducer.
        gif[gif.length - 1] = 0x42;

        assertDamaged(gif, "image/gif");
    }

    @Test
    void rejectsWebpFilesWithABrokenBitstreamHeaderOrChunk() {
        byte[] lossyStartCode = TestImages.WEBP_LOSSY.clone();
        lossyStartCode[20 + 3] = 0x00;
        byte[] losslessVersion = TestImages.WEBP_LOSSLESS.clone();
        losslessVersion[20 + 4] |= (byte) 0xe0;
        byte[] chunkTooLong = TestImages.WEBP_LOSSY.clone();
        chunkTooLong[16] = (byte) 0xff;
        byte[] extendedWithoutImage = Arrays.copyOf(TestImages.WEBP_ALPHA, 12 + 18);
        extendedWithoutImage[4] = 18 + 4;
        extendedWithoutImage[5] = 0;

        assertDamaged(lossyStartCode, "image/webp");
        assertDamaged(losslessVersion, "image/webp");
        assertDamaged(chunkTooLong, "image/webp");
        assertDamaged(extendedWithoutImage, "image/webp");
    }

    @Test
    void rejectsWebpFilesThatAreOnlyAHeader() {
        // RIFF header, a chunk header, then only the 10-byte VP8 frame header (or 5-byte VP8L header), with sizes
        // that match, so the container itself looks complete.
        assertDamaged(webpWithFirstChunkCutTo(TestImages.WEBP_LOSSY, 10), "image/webp");
        assertDamaged(webpWithFirstChunkCutTo(TestImages.WEBP_LOSSLESS, 5), "image/webp");
        // The VP8 header announces a 9-byte first partition and token data after it; here the data stops right after
        // that partition.
        assertDamaged(webpWithFirstChunkCutTo(TestImages.WEBP_LOSSY, 19), "image/webp");

        byte[] animated = TestImages.WEBP_ANIMATED.clone();
        int frame = TestImages.indexOf(animated, "VP8L".getBytes(StandardCharsets.US_ASCII));
        animated[frame + 4] = 5;
        assertDamaged(animated, "image/webp");
    }

    /** The WebP with its first chunk cut to {@code length} bytes, and the chunk and RIFF sizes made to match. */
    private static byte[] webpWithFirstChunkCutTo(byte[] webp, int length) {
        byte[] cut = Arrays.copyOf(webp, 12 + 8 + length);
        TestImages.putU32le(cut, 4, cut.length - 8);
        TestImages.putU32le(cut, 16, length);
        return cut;
    }

    @Test
    void rejectsAnImageWithTooManyPixelsBeforeDecodingIt() {
        assertTooLarge(TestImages.pngClaiming(100_000, 100_000), "image/png");
        // Two 32-bit sides whose product would overflow a long.
        assertTooLarge(TestImages.pngClaiming(0xffff_ffffL, 0xffff_ffffL), "image/png");

        byte[] jpeg = TestImages.jpeg(8, 8);
        // 12288 x 12288 in the frame header (SOF0: marker, length, precision, height, width). libjpeg itself
        // refuses sides over 65500, so a smaller side shows that this limit is the one that applies.
        int frame = TestImages.indexOf(jpeg, bytes(0xff, 0xc0));
        System.arraycopy(bytes(0x30, 0x00, 0x30, 0x00), 0, jpeg, frame + 5, 4);
        assertTooLarge(jpeg, "image/jpeg");

        byte[] gif = TestImages.gif(8, 8);
        Arrays.fill(gif, 6, 10, (byte) 0xff);
        assertTooLarge(gif, "image/gif");

        byte[] webp = TestImages.WEBP_ALPHA.clone();
        Arrays.fill(webp, 20 + 4, 20 + 10, (byte) 0xff);
        assertTooLarge(webp, "image/webp");
    }

    @Test
    void acceptsAnImageAtThePixelLimitAndDecodesItWithBoundedMemory() {
        byte[] limit = TestImages.blankPng(5_000, 5_000);
        byte[] over = TestImages.blankPng(5_000, 5_001);

        org.junit.jupiter.api.Assertions.assertTimeout(Duration.ofSeconds(10),
                () -> assertThat(validate(limit, "image/png").contentType()).isEqualTo("image/png"));
        assertTooLarge(over, "image/png");
    }

    private static void assertDamaged(byte[] image, String contentType) {
        refused(image, contentType).hasMessageContaining("damaged or incomplete");
    }

    private static void assertTooLarge(byte[] image, String contentType) {
        refused(image, contentType).hasMessageContaining("at most 25 megapixels");
    }

    private static AbstractThrowableAssert<?, ? extends Throwable> refused(byte[] image, String contentType) {
        return assertThatThrownBy(() -> validate(image, contentType))
                .as("%s of %d bytes", contentType, image.length)
                .isInstanceOf(InvalidDeliveryProofException.class);
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
