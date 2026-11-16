package com.flavors.service.impl;

import com.flavors.exception.InvalidDeliveryProofException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * Checks that a screenshot whose signature already matched is a complete, undamaged image of a sane size. A valid
 * signature says nothing about the rest of the file: a cut-off upload or a damaged export would otherwise be kept as
 * evidence the brand cannot open.
 *
 * <ul>
 *   <li>PNG: every chunk is walked up to {@code IEND} and its CRC checked, then the image data is decoded.</li>
 *   <li>JPEG: the image is decoded, and any decoder warning (a missing end marker, a short data segment) counts as
 *       damage. JPEG has no checksums, so damage that still decodes cleanly cannot be seen.</li>
 *   <li>GIF: every block is walked up to the trailer, then the first frame is decoded.</li>
 *   <li>WebP: the JDK has no WebP decoder, so the RIFF container, every chunk length and the bitstream header are
 *       checked. Damage inside the compressed data itself is not detected.</li>
 * </ul>
 *
 * <p>Resource limits: at most {@link #MAX_PIXELS} pixels, read from the header before anything is decoded. Decoding
 * keeps at most {@value #MAX_DECODED_SIDE} pixels per side in memory (source subsampling), skips metadata (so
 * compressed text chunks are never inflated), reads from memory (no temporary files), and at most
 * {@value #CONCURRENT_DECODES} uploads are decoded at a time.
 */
final class ProofImageIntegrity {

    /** 25 megapixels: above any phone or desktop screenshot, including long scrolling ones (1080 x 23 000). */
    static final long MAX_PIXELS = 25_000_000L;
    private static final int MAX_DECODED_SIDE = 1024;
    private static final int CONCURRENT_DECODES = 2;
    private static final Semaphore DECODES = new Semaphore(CONCURRENT_DECODES);

    private ProofImageIntegrity() {
    }

    static void check(ProofImageValidator.Type type, byte[] bytes) {
        switch (type) {
            case PNG -> {
                walkPng(bytes);
                decode("png", bytes);
            }
            case JPEG -> decode("jpeg", bytes);
            case GIF -> {
                walkGif(bytes);
                decode("gif", bytes);
            }
            case WEBP -> walkWebp(bytes);
        }
    }

    static InvalidDeliveryProofException damaged() {
        return new InvalidDeliveryProofException(
                "A screenshot is damaged or incomplete. Export it again and upload the new file.");
    }

    private static void requireSize(long width, long height) {
        if (width < 1 || height < 1) {
            throw damaged();
        }
        // Each side is checked first: two 32-bit PNG sides could overflow the product.
        if (width > MAX_PIXELS || height > MAX_PIXELS || width * height > MAX_PIXELS) {
            throw new InvalidDeliveryProofException("A screenshot can have at most 25 megapixels.");
        }
    }

    // --- PNG: signature, IHDR first, CRC on every chunk, at least one IDAT, then IEND --------------------------------

    private static void walkPng(byte[] bytes) {
        Bytes in = new Bytes(bytes, 8);
        CRC32 crc = new CRC32();
        boolean header = false;
        boolean data = false;
        while (true) {
            long length = in.u32be();
            int typeAt = in.position();
            String chunk = in.ascii(4);
            if (length > in.remaining() - 4) {
                throw damaged();
            }
            int dataAt = in.position();
            in.skip((int) length);
            crc.reset();
            crc.update(bytes, typeAt, 4 + (int) length);
            if (crc.getValue() != in.u32be()) {
                throw damaged();
            }
            if (!header) {
                if (!chunk.equals("IHDR") || length != 13) {
                    throw damaged();
                }
                requireSize(Bytes.u32be(bytes, dataAt), Bytes.u32be(bytes, dataAt + 4));
                header = true;
            } else if (chunk.equals("IDAT")) {
                data = true;
            } else if (chunk.equals("IEND")) {
                if (!data) {
                    throw damaged();
                }
                return;
            }
        }
    }

    // --- GIF: header, logical screen, then extension and image blocks up to the trailer (0x3B) -----------------------

    private static void walkGif(byte[] bytes) {
        Bytes in = new Bytes(bytes, 6);
        int screenWidth = in.u16le();
        int screenHeight = in.u16le();
        if (screenWidth > 0 && screenHeight > 0) {
            requireSize(screenWidth, screenHeight);
        }
        int flags = in.u8();
        in.skip(2);
        skipColorTable(in, flags);
        boolean image = false;
        while (true) {
            switch (in.u8()) {
                case 0x21 -> {
                    in.u8();
                    skipSubBlocks(in);
                }
                case 0x2C -> {
                    in.skip(4);
                    requireSize(in.u16le(), in.u16le());
                    skipColorTable(in, in.u8());
                    in.u8();
                    skipSubBlocks(in);
                    image = true;
                }
                case 0x3B -> {
                    if (!image) {
                        throw damaged();
                    }
                    return;
                }
                default -> throw damaged();
            }
        }
    }

    private static void skipColorTable(Bytes in, int flags) {
        if ((flags & 0x80) != 0) {
            in.skip(3 * (1 << ((flags & 0x07) + 1)));
        }
    }

    private static void skipSubBlocks(Bytes in) {
        for (int size = in.u8(); size != 0; size = in.u8()) {
            in.skip(size);
        }
    }

    // --- WebP: RIFF size matches the file, every chunk fits, and the first chunk is a valid VP8, VP8L or VP8X -------

    private static void walkWebp(byte[] bytes) {
        long riffSize = Bytes.u32le(bytes, 4);
        if (riffSize + 8 != bytes.length || riffSize < 4 + 8) {
            throw damaged();
        }
        Bytes in = new Bytes(bytes, 12);
        boolean first = true;
        boolean extended = false;
        boolean frame = false;
        while (in.remaining() > 0) {
            String chunk = in.ascii(4);
            long size = in.u32le();
            if (size > in.remaining()) {
                throw damaged();
            }
            int at = in.position();
            switch (chunk) {
                case "VP8 " -> {
                    requireVp8(bytes, at, size);
                    frame = true;
                }
                case "VP8L" -> {
                    requireVp8l(bytes, at, size);
                    frame = true;
                }
                case "VP8X" -> {
                    if (!first || size < 10) {
                        throw damaged();
                    }
                    requireSize(1 + Bytes.u24le(bytes, at + 4), 1 + Bytes.u24le(bytes, at + 7));
                    extended = true;
                }
                case "ANMF" -> {
                    if (!extended || size < 16 + 8) {
                        throw damaged();
                    }
                    frame = true;
                }
                default -> {
                    if (first) {
                        throw damaged();
                    }
                }
            }
            first = false;
            // Chunks are padded to an even length; a missing pad byte after the last chunk is tolerated.
            in.skip((int) Math.min(size + (size & 1), in.remaining()));
        }
        if (!frame) {
            throw damaged();
        }
    }

    private static void requireVp8(byte[] bytes, int at, long size) {
        // Frame tag (3 bytes, key frame bit clear), start code 9D 01 2A, then 14-bit width and height.
        if (size < 10 || (bytes[at] & 0x01) != 0 || (bytes[at + 3] & 0xff) != 0x9d || (bytes[at + 4] & 0xff) != 0x01
                || (bytes[at + 5] & 0xff) != 0x2a) {
            throw damaged();
        }
        requireSize(Bytes.u16le(bytes, at + 6) & 0x3fff, Bytes.u16le(bytes, at + 8) & 0x3fff);
    }

    private static void requireVp8l(byte[] bytes, int at, long size) {
        // Signature 0x2F, then 14 bits width - 1, 14 bits height - 1, 1 alpha bit and a 3-bit version that must be 0.
        if (size < 5 || (bytes[at] & 0xff) != 0x2f) {
            throw damaged();
        }
        long bits = Bytes.u32le(bytes, at + 1);
        if ((bits >>> 29) != 0) {
            throw damaged();
        }
        requireSize(1 + (bits & 0x3fff), 1 + ((bits >>> 14) & 0x3fff));
    }

    // --- Decoding (PNG, JPEG, GIF) ----------------------------------------------------------------------------------

    private static void decode(String format, byte[] bytes) {
        DECODES.acquireUninterruptibly();
        ImageReader reader = null;
        try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
            if (!readers.hasNext()) {
                throw new IllegalStateException("The JDK has no " + format + " decoder.");
            }
            reader = readers.next();
            AtomicBoolean warned = new AtomicBoolean();
            reader.addIIOReadWarningListener((source, warning) -> warned.set(true));
            reader.setInput(in, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            requireSize(width, height);
            ImageReadParam param = reader.getDefaultReadParam();
            param.setSourceSubsampling(subsampling(width), subsampling(height), 0, 0);
            // A raster skips colour conversion, so a valid CMYK JPEG is not refused for its colour space.
            if (reader.canReadRaster()) {
                reader.readRaster(0, param);
            } else {
                reader.read(0, param);
            }
            if (warned.get()) {
                throw damaged();
            }
        } catch (IOException e) {
            throw damaged();
        } catch (InvalidDeliveryProofException e) {
            throw e;
        } catch (IllegalStateException e) {
            if (reader == null) {
                throw e;
            }
            throw damaged();
        } catch (RuntimeException e) {
            // Decoders also fail on malformed input with unchecked exceptions (e.g. an index out of bounds).
            throw damaged();
        } finally {
            if (reader != null) {
                reader.dispose();
            }
            DECODES.release();
        }
    }

    private static int subsampling(int side) {
        return Math.max(1, (side + MAX_DECODED_SIDE - 1) / MAX_DECODED_SIDE);
    }

    /** Reads a byte array front to back; running past the end means the file was cut off. */
    private static final class Bytes {
        private final byte[] bytes;
        private int position;

        Bytes(byte[] bytes, int position) {
            this.bytes = bytes;
            this.position = position;
        }

        int position() {
            return position;
        }

        int remaining() {
            return bytes.length - position;
        }

        void skip(int count) {
            require(count);
            position += count;
        }

        int u8() {
            require(1);
            return bytes[position++] & 0xff;
        }

        int u16le() {
            require(2);
            int value = u16le(bytes, position);
            position += 2;
            return value;
        }

        long u32le() {
            require(4);
            long value = u32le(bytes, position);
            position += 4;
            return value;
        }

        long u32be() {
            require(4);
            long value = u32be(bytes, position);
            position += 4;
            return value;
        }

        String ascii(int count) {
            require(count);
            String value = new String(bytes, position, count, StandardCharsets.US_ASCII);
            position += count;
            return value;
        }

        private void require(int count) {
            if (count < 0 || count > remaining()) {
                throw damaged();
            }
        }

        static int u16le(byte[] b, int i) {
            return (b[i] & 0xff) | (b[i + 1] & 0xff) << 8;
        }

        static long u24le(byte[] b, int i) {
            return (b[i] & 0xffL) | (b[i + 1] & 0xffL) << 8 | (b[i + 2] & 0xffL) << 16;
        }

        static long u32le(byte[] b, int i) {
            return (b[i] & 0xffL) | (b[i + 1] & 0xffL) << 8 | (b[i + 2] & 0xffL) << 16 | (b[i + 3] & 0xffL) << 24;
        }

        static long u32be(byte[] b, int i) {
            return (b[i] & 0xffL) << 24 | (b[i + 1] & 0xffL) << 16 | (b[i + 2] & 0xffL) << 8 | (b[i + 3] & 0xffL);
        }
    }
}
