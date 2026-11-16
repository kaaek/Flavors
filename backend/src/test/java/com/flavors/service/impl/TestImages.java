package com.flavors.service.impl;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/** Small, real images for tests: uploads are decoded, so a bare file signature is not enough. */
final class TestImages {

    /** 1 x 1 WebP files from Modernizr's feature detection: lossy (VP8), lossless (VP8L), with alpha, animated. */
    static final byte[] WEBP_LOSSY = base64("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
    static final byte[] WEBP_LOSSLESS = base64("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==");
    static final byte[] WEBP_ALPHA = base64("UklGRkoAAABXRUJQVlA4WAoAAAAQAAAAAAAAAAAAQUxQSAwAAAARBxAR/Q9ERP8DAABWUDgg"
            + "GAAAABQBAJ0BKgEAAQAAAP4AAA3AAP7mtQAAAA==");
    static final byte[] WEBP_ANIMATED = base64("UklGRlIAAABXRUJQVlA4WAoAAAASAAAAAAAAAAAAQU5JTQYAAAD/////AABBTk1GJgAA"
            + "AAAAAAAAAAAAAAAAAGQAAABWUDhMDQAAAC8AAAAQBxAREYiI/gcA");

    private TestImages() {
    }

    static byte[] png(int width, int height) {
        return write("png", noise(width, height, BufferedImage.TYPE_INT_RGB));
    }

    static byte[] jpeg(int width, int height) {
        return write("jpeg", noise(width, height, BufferedImage.TYPE_INT_RGB));
    }

    static byte[] gif(int width, int height) {
        return write("gif", noise(width, height, BufferedImage.TYPE_BYTE_INDEXED));
    }

    /** A one-bit PNG: cheap to build and decode even at millions of pixels. */
    static byte[] blankPng(int width, int height) {
        return write("png", new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY));
    }

    /** A valid PNG padded to exactly {@code size} bytes with a private ancillary chunk before {@code IEND}. */
    static byte[] pngOfSize(int size) {
        byte[] base = png(8, 8);
        int iend = base.length - 12;
        byte[] padding = new byte[size - base.length - 12];
        byte[] chunk = chunk("prVt", padding);
        byte[] result = new byte[size];
        System.arraycopy(base, 0, result, 0, iend);
        System.arraycopy(chunk, 0, result, iend, chunk.length);
        System.arraycopy(base, iend, result, iend + chunk.length, 12);
        return result;
    }

    /** The PNG with other sides written into its header and a matching CRC, so only the size is wrong. */
    static byte[] pngClaiming(long width, long height) {
        byte[] png = png(2, 2);
        putU32be(png, 16, width);
        putU32be(png, 20, height);
        putU32be(png, 29, crc(png, 12, 4 + 13));
        return png;
    }

    /** Overwrites bytes inside the PNG's first IDAT chunk, then fixes that chunk's CRC: only decoding can notice. */
    static byte[] pngWithDamagedDataAndValidChecksums() {
        byte[] png = png(32, 32);
        int idat = indexOf(png, "IDAT".getBytes(StandardCharsets.US_ASCII)) - 4;
        int length = (int) u32be(png, idat);
        Arrays.fill(png, idat + 8 + length / 3, idat + 8 + length / 3 + 32, (byte) 0x55);
        putU32be(png, idat + 8 + length, crc(png, idat + 4, 4 + length));
        return png;
    }

    static int indexOf(byte[] bytes, byte[] part) {
        for (int i = 0; i + part.length <= bytes.length; i++) {
            if (Arrays.equals(bytes, i, i + part.length, part, 0, part.length)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Not found");
    }

    static void putU32be(byte[] bytes, int at, long value) {
        bytes[at] = (byte) (value >>> 24);
        bytes[at + 1] = (byte) (value >>> 16);
        bytes[at + 2] = (byte) (value >>> 8);
        bytes[at + 3] = (byte) value;
    }

    static void putU32le(byte[] bytes, int at, long value) {
        bytes[at] = (byte) value;
        bytes[at + 1] = (byte) (value >>> 8);
        bytes[at + 2] = (byte) (value >>> 16);
        bytes[at + 3] = (byte) (value >>> 24);
    }

    private static long u32be(byte[] b, int i) {
        return (b[i] & 0xffL) << 24 | (b[i + 1] & 0xffL) << 16 | (b[i + 2] & 0xffL) << 8 | (b[i + 3] & 0xffL);
    }

    private static long crc(byte[] bytes, int from, int length) {
        CRC32 crc = new CRC32();
        crc.update(bytes, from, length);
        return crc.getValue();
    }

    private static byte[] chunk(String type, byte[] data) {
        byte[] chunk = new byte[12 + data.length];
        putU32be(chunk, 0, data.length);
        System.arraycopy(type.getBytes(StandardCharsets.US_ASCII), 0, chunk, 4, 4);
        System.arraycopy(data, 0, chunk, 8, data.length);
        putU32be(chunk, 8 + data.length, crc(chunk, 4, 4 + data.length));
        return chunk;
    }

    private static BufferedImage noise(int width, int height, int type) {
        BufferedImage image = new BufferedImage(width, height, type);
        Random random = new Random(width * 31L + height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, random.nextInt());
            }
        }
        return image;
    }

    private static byte[] write(String format, BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalStateException("No " + format + " writer");
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] base64(String value) {
        return Base64.getDecoder().decode(value);
    }
}
