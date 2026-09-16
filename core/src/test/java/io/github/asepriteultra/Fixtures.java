package io.github.asepriteultra;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.DeflaterOutputStream;

/** Tiny synthetic files with deliberately specified pixels and metadata; no editor required. */
final class Fixtures {
    private Fixtures() { }

    static final class Bytes {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        Bytes u8(int value) { out.write(value); return this; }
        Bytes u16(int value) { return u8(value).u8(value >>> 8); }
        Bytes u32(int value) { return u16(value).u16(value >>> 16); }
        Bytes zero(int count) { for (int i = 0; i < count; i++) { u8(0); } return this; }
        Bytes bytes(byte[] bytes) { out.writeBytes(bytes); return this; }
        Bytes string(String text) {
            byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
            return u16(encoded.length).bytes(encoded);
        }
        byte[] get() { return out.toByteArray(); }
    }

    static byte[] chunk(int type, byte[] data) { return new Bytes().u32(data.length + 6).u16(type).bytes(data).get(); }

    static byte[] frame(int duration, byte[]... chunks) {
        Bytes body = new Bytes().u16(0xF1FA).u16(chunks.length).u16(duration).zero(2).u32(chunks.length);
        for (byte[] chunk : chunks) { body.bytes(chunk); }
        return new Bytes().u32(body.get().length + 4).bytes(body.get()).get();
    }

    static byte[] file(int width, int height, int depth, int flags, byte[]... frames) {
        Bytes body = new Bytes();
        for (byte[] frame : frames) { body.bytes(frame); }
        Bytes header = new Bytes().u32(128 + body.get().length).u16(0xA5E0).u16(frames.length)
                .u16(width).u16(height).u16(depth).u32(flags).u16(100).zero(8).u8(0).zero(3)
                .u16(256).u8(1).u8(1).zero(92);
        if (header.get().length != 128) { throw new AssertionError("Fixture header size"); }
        return header.bytes(body.get()).get();
    }

    static byte[] layer(String name, int flags, int type, int level, int opacity, int blend) {
        return chunk(0x2004, new Bytes().u16(flags).u16(type).u16(level).zero(4)
                .u16(blend).u8(opacity).zero(3).string(name).get());
    }

    static byte[] layer() { return layer("sprite", 1, 0, 0, 255, 0); }

    static byte[] cel(int layer, int x, int y, int opacity, int w, int h, byte[] pixels, boolean compressed) {
        byte[] data = pixels;
        if (compressed) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (DeflaterOutputStream zip = new DeflaterOutputStream(output)) { zip.write(pixels); }
            catch (IOException exception) { throw new AssertionError(exception); }
            data = output.toByteArray();
        }
        return chunk(0x2005, new Bytes().u16(layer).u16(x).u16(y).u8(opacity)
                .u16(compressed ? 2 : 0).zero(7).u16(w).u16(h).bytes(data).get());
    }

    static byte[] linked(int layer, int frame, int x, int opacity) {
        return chunk(0x2005, new Bytes().u16(layer).u16(x).u16(0).u8(opacity).u16(1).zero(7).u16(frame).get());
    }

    static byte[] rgba(int... colors) {
        Bytes result = new Bytes();
        for (int color : colors) { result.u8(color >>> 24).u8(color >>> 16).u8(color >>> 8).u8(color); }
        return result.get();
    }

    static byte[] tags(int direction, int from, int to) {
        return chunk(0x2018, new Bytes().u16(1).zero(8).u16(from).u16(to).u8(direction).u16(3)
                .zero(10).string("walk").get());
    }

    static byte[] palette(int first, int... colors) {
        Bytes data = new Bytes().u32(256).u32(first).u32(first + colors.length - 1).zero(8);
        for (int color : colors) { data.u16(0).bytes(rgba(color)); }
        return chunk(0x2019, data.get());
    }

    static byte[] simple(int color) {
        return file(1, 1, 32, 1, frame(100, layer(), cel(0, 0, 0, 255, 1, 1, rgba(color), true)));
    }

    static void put32(byte[] bytes, int offset, int value) {
        for (int i = 0; i < 4; i++) { bytes[offset + i] = (byte) (value >>> (i * 8)); }
    }
}
