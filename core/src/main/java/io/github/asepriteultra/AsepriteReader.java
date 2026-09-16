package io.github.asepriteultra;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Reads the documented little-endian Aseprite format without external dependencies. Thread-safe. */
public final class AsepriteReader {
    /** Limits bound input, retained pixel data and compositing work buffers per decode. */
    public record Limits(int fileBytes, long decodedBytes, int frames, int layers) {
        public Limits {
            if (fileBytes < 128 || fileBytes == Integer.MAX_VALUE || decodedBytes < 4
                    || frames < 1 || layers < 1) { throw new IllegalArgumentException("Invalid limits"); }
        }
        public static Limits defaults() { return new Limits(64 * 1024 * 1024, 128L * 1024 * 1024, 4096, 1024); }
    }

    private final Limits limits;

    public AsepriteReader() { this(Limits.defaults()); }
    public AsepriteReader(Limits limits) { this.limits = java.util.Objects.requireNonNull(limits); }

    public Aseprite read(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) { return read(input); }
    }

    /** Does not close the caller's stream. */
    public Aseprite read(InputStream input) throws IOException {
        return read(input.readNBytes(limits.fileBytes() + 1));
    }

    public Aseprite read(byte[] bytes) throws AsepriteException {
        if (bytes.length > limits.fileBytes()) { throw new AsepriteException("File exceeds input limit"); }
        return new Decoder(new Cursor(bytes), limits).decode();
    }

    private record Chunk(int type, Cursor data) { }
    private record Pixels(int width, int height, byte[] data) { }
    private record Cel(int layer, int x, int y, int opacity, Pixels pixels) { }

    private static final class Decoder {
        private final Cursor input;
        private final Limits limits;
        private final List<Aseprite.Layer> layers = new ArrayList<>();
        private final Map<String, Aseprite.Tag> tags = new LinkedHashMap<>();
        private final Map<String, Aseprite.Slice> slices = new LinkedHashMap<>();
        private final Set<String> warnings = new LinkedHashSet<>();
        private final List<Map<Integer, Cel>> cels = new ArrayList<>();
        private int[] palette = new int[256];
        private boolean[] paletteKnown = new boolean[256];
        private int width;
        private int height;
        private int depth;
        private int flags;
        private int transparent;
        private int frameCount;
        private long decoded;

        Decoder(Cursor input, Limits limits) { this.input = input; this.limits = limits; }

        Aseprite decode() throws AsepriteException {
            Cursor header = input.take(128);
            require(header.u32() == input.size(), "Header file size does not match input");
            require(header.u16() == 0xA5E0, "Invalid Aseprite magic");
            frameCount = header.u16();
            width = header.u16();
            height = header.u16();
            depth = header.u16();
            flags = header.i32();
            int speed = header.u16();
            header.skip(8);
            transparent = header.u8();
            require(frameCount > 0 && frameCount <= limits.frames(), "Frame count exceeds limit or is zero");
            require(width > 0 && height > 0, "Empty canvas");
            require((long) width * height <= Integer.MAX_VALUE, "Canvas exceeds Java array limit");
            require(depth == 8 || depth == 16 || depth == 32, "Unsupported color depth: " + depth);
            reserve((long) width * height * frameCount * 8); // Output plus immutable-image copy.
            List<Aseprite.Frame> frames = new ArrayList<>();
            for (int frame = 0; frame < frameCount; frame++) {
                int size = input.length();
                require(size >= 16, "Frame header too short");
                Cursor body = input.take(size - 4);
                require(body.u16() == 0xF1FA, "Invalid frame magic at frame " + frame);
                int oldCount = body.u16();
                int duration = body.u16();
                if (duration == 0) { duration = speed; }
                require(duration > 0, "Frame has no positive duration");
                body.skip(2);
                long newCount = body.u32();
                long chunkCount = newCount == 0 ? oldCount : newCount;
                require(chunkCount <= body.remaining() / 6, "Chunk count exceeds frame bounds");
                require(chunkCount <= 65536, "More than 65,536 chunks in a frame");
                List<Chunk> chunks = new ArrayList<>();
                boolean modernPalette = false;
                for (int i = 0; i < chunkCount; i++) {
                    int chunkSize = body.length();
                    require(chunkSize >= 6, "Chunk size below six bytes");
                    int type = body.u16();
                    chunks.add(new Chunk(type, body.take(chunkSize - 6)));
                    modernPalette |= type == 0x2019;
                }
                Map<Integer, Cel> frameCels = new HashMap<>();
                for (Chunk chunk : chunks) {
                    Cursor data = chunk.data();
                    switch (chunk.type()) {
                        case 0x2004 -> { require(frame == 0, "Layers must be declared in frame zero"); layer(data); }
                        case 0x2005 -> {
                            Cel cel = cel(data, frame);
                            require(frameCels.putIfAbsent(cel.layer(), cel) == null, "Duplicate cel on one layer/frame");
                        }
                        case 0x2018 -> tags(data);
                        case 0x2019 -> palette(data);
                        case 0x0004, 0x0011 -> { if (!modernPalette) { oldPalette(data, chunk.type() == 0x0011); } }
                        case 0x2022 -> slice(data);
                        case 0x2006 -> require((data.i32() & 1) == 0, "Precise/scaled cel bounds are unsupported");
                        case 0x2023 -> throw new AsepriteException("Tile sets are unsupported; flatten tilemap layers first");
                        case 0x2008 -> throw new AsepriteException("External-file dependencies are unsupported");
                        case 0x2007 -> warnings.add("Color profile metadata is ignored; samples are treated as sRGB");
                        case 0x2020 -> warnings.add("User data text, colors and properties are not exposed in v0.1");
                        default -> warnings.add("Skipped chunk 0x" + Integer.toHexString(chunk.type()));
                    }
                }
                validateLayers();
                for (Cel cel : frameCels.values()) {
                    require(cel.layer() < layers.size() && !layers.get(cel.layer()).group(), "Cel refers to invalid image layer");
                }
                cels.add(frameCels);
                int[] canvas = renderRange(0, layers.size(), frameCels);
                frames.add(new Aseprite.Frame(duration, new RgbaImage(width, height, canvas)));
            }
            require(input.remaining() == 0, "Trailing data outside declared frames");
            return new Aseprite(width, height, frames, layers, tags, slices, new ArrayList<>(warnings));
        }

        private void reserve(long bytes) throws AsepriteException {
            require(bytes >= 0 && bytes <= limits.decodedBytes() - decoded, "Decoded data exceeds memory budget");
            decoded += bytes;
        }

        private void layer(Cursor data) throws AsepriteException {
            require(layers.size() < limits.layers(), "Layer count exceeds limit");
            int layerFlags = data.u16();
            int type = data.u16();
            int level = data.u16();
            data.skip(4);
            int blend = data.u16();
            int opacity = data.u8();
            data.skip(3);
            String name = data.string();
            require(type <= 1, "Tilemap/unknown layer type is unsupported: " + name);
            require(level <= 64, "Layer nesting exceeds 64");
            require(blend == 0 || type == 1 && (flags & 2) == 0, "Non-normal blend mode unsupported: " + name);
            if (type == 1 ? (flags & 2) == 0 : (flags & 1) == 0) { opacity = 255; }
            layers.add(new Aseprite.Layer(name, layerFlags, type, level, opacity));
        }

        private void validateLayers() throws AsepriteException {
            for (int i = 0; i < layers.size(); i++) {
                int level = layers.get(i).depth();
                if (i == 0) { require(level == 0, "First layer must be at depth zero"); }
                else if (level > layers.get(i - 1).depth()) {
                    require(level == layers.get(i - 1).depth() + 1 && layers.get(i - 1).group(), "Invalid layer hierarchy");
                }
            }
        }

        private Cel cel(Cursor data, int frame) throws AsepriteException {
            int layer = data.u16();
            int x = data.i16();
            int y = data.i16();
            int opacity = data.u8();
            int type = data.u16();
            require(data.i16() == 0, "Cel z-index offsets are unsupported in v0.1");
            data.skip(5);
            Pixels pixels;
            if (type == 1) {
                int source = data.u16();
                require(source < frame, "Linked cels must refer to an earlier frame");
                Cel linked = cels.get(source).get(layer);
                require(linked != null, "Linked cel target is missing");
                pixels = linked.pixels(); // Share image data only; position and opacity belong to this cel.
            } else {
                require(type == 0 || type == 2, "Tilemap/unknown cel type is unsupported");
                int w = data.u16();
                int h = data.u16();
                require(w > 0 && h > 0, "Zero-size image cel");
                long length = (long) w * h * (depth / 8);
                reserve(length);
                require(length <= Integer.MAX_VALUE, "Cel is too large");
                byte[] raw = type == 0 ? data.bytes((int) length) : inflate(data, (int) length);
                pixels = new Pixels(w, h, raw);
            }
            return new Cel(layer, x, y, opacity, pixels);
        }

        private byte[] inflate(Cursor data, int length) throws AsepriteException {
            byte[] result = new byte[length];
            Inflater inflater = new Inflater();
            try {
                inflater.setInput(data.bytes(data.remaining()));
                int offset = 0;
                while (offset < length) {
                    int read = inflater.inflate(result, offset, length - offset);
                    require(read > 0, "Truncated or invalid zlib cel");
                    offset += read;
                }
                byte[] extra = new byte[1];
                require(inflater.inflate(extra) == 0 && inflater.finished() && inflater.getRemaining() == 0,
                        "Zlib cel does not match declared dimensions or has trailing data");
                return result;
            } catch (DataFormatException exception) {
                throw new AsepriteException("Invalid zlib cel", exception);
            } finally { inflater.end(); }
        }

        private void palette(Cursor data) throws AsepriteException {
            int size = data.length();
            int first = data.length();
            int last = data.length();
            data.skip(8);
            require(size > 0 && size <= 65536 && first <= last && last < size, "Invalid palette range");
            palette = Arrays.copyOf(palette, size);
            paletteKnown = Arrays.copyOf(paletteKnown, size);
            for (int i = first; i <= last; i++) {
                int entryFlags = data.u16();
                palette[i] = data.rgba();
                paletteKnown[i] = true;
                if ((entryFlags & 1) != 0) { data.string(); }
            }
        }

        private void oldPalette(Cursor data, boolean sixBit) throws AsepriteException {
            if (palette.length < 256) {
                palette = Arrays.copyOf(palette, 256);
                paletteKnown = Arrays.copyOf(paletteKnown, 256);
            }
            int packets = data.u16();
            int index = 0;
            for (int p = 0; p < packets; p++) {
                index += data.u8();
                int count = data.u8();
                if (count == 0) { count = 256; }
                require(index + count <= 256, "Old palette packet exceeds 256 colors");
                for (int i = 0; i < count; i++) {
                    int r = data.u8();
                    int g = data.u8();
                    int b = data.u8();
                    if (sixBit) {
                        require(r < 64 && g < 64 && b < 64, "Invalid 6-bit palette channel");
                        r = r * 255 / 63; g = g * 255 / 63; b = b * 255 / 63;
                    }
                    palette[index] = r << 24 | g << 16 | b << 8 | 255;
                    paletteKnown[index++] = true;
                }
            }
        }

        private void tags(Cursor data) throws AsepriteException {
            int count = data.u16();
            data.skip(8);
            for (int i = 0; i < count; i++) {
                int from = data.u16();
                int to = data.u16();
                int direction = data.u8();
                int repeat = data.u16();
                data.skip(10);
                String name = data.string();
                require(from <= to && to < frameCount && direction <= 3, "Invalid animation tag: " + name);
                Aseprite.Tag tag = new Aseprite.Tag(name, from, to, Aseprite.Direction.values()[direction], repeat);
                require(tags.putIfAbsent(name, tag) == null, "Duplicate animation tag: " + name);
            }
        }

        private void slice(Cursor data) throws AsepriteException {
            int count = data.length();
            int sliceFlags = data.i32();
            data.skip(4);
            String name = data.string();
            require(count <= data.remaining() / 20, "Slice key count exceeds chunk bounds");
            List<Aseprite.SliceKey> keys = new ArrayList<>();
            int previous = -1;
            for (int i = 0; i < count; i++) {
                int frame = data.length();
                require(frame > previous && frame < frameCount, "Slice keys must be ordered valid frames");
                previous = frame;
                Aseprite.Rect bounds = rect(data);
                Aseprite.Rect center = (sliceFlags & 1) != 0 ? rect(data) : null;
                Aseprite.Point pivot = (sliceFlags & 2) != 0 ? new Aseprite.Point(data.i32(), data.i32()) : null;
                keys.add(new Aseprite.SliceKey(frame, bounds, center, pivot));
            }
            require(slices.putIfAbsent(name, new Aseprite.Slice(name, keys)) == null, "Duplicate slice: " + name);
        }

        private Aseprite.Rect rect(Cursor data) throws AsepriteException {
            return new Aseprite.Rect(data.i32(), data.i32(), data.length(), data.length());
        }

        private int[] renderRange(int start, int end, Map<Integer, Cel> frame) throws AsepriteException {
            int[] canvas = new int[width * height];
            for (int i = start; i < end; i++) {
                Aseprite.Layer layer = layers.get(i);
                boolean visible = layer.visible() && (layer.flags() & 64) == 0;
                if (layer.group()) {
                    int groupEnd = i + 1;
                    while (groupEnd < end && layers.get(groupEnd).depth() > layer.depth()) { groupEnd++; }
                    if (visible) {
                        reserve((long) width * height * 4);
                        int[] group = renderRange(i + 1, groupEnd, frame);
                        for (int p = 0; p < canvas.length; p++) { canvas[p] = over(canvas[p], group[p], layer.opacity()); }
                    }
                    i = groupEnd - 1;
                } else if (visible && frame.containsKey(i)) {
                    Cel cel = frame.get(i);
                    Pixels source = cel.pixels();
                    int opacity = mul255(layer.opacity(), cel.opacity());
                    int minX = Math.max(0, -cel.x());
                    int minY = Math.max(0, -cel.y());
                    int maxX = Math.min(source.width(), width - cel.x());
                    int maxY = Math.min(source.height(), height - cel.y());
                    for (int y = minY; y < maxY; y++) {
                        for (int x = minX; x < maxX; x++) {
                            int color = color(source.data(), y * source.width() + x, (layer.flags() & 8) != 0);
                            int target = (y + cel.y()) * width + x + cel.x();
                            canvas[target] = over(canvas[target], color, opacity);
                        }
                    }
                }
            }
            return canvas;
        }

        private int color(byte[] data, int pixel, boolean background) throws AsepriteException {
            int offset = pixel * (depth / 8);
            int r = data[offset] & 255;
            if (depth == 32) {
                return r << 24 | (data[offset + 1] & 255) << 16 | (data[offset + 2] & 255) << 8 | data[offset + 3] & 255;
            }
            if (depth == 16) { return r << 24 | r << 16 | r << 8 | data[offset + 1] & 255; }
            if (!background && r == transparent) { return 0; }
            require(r < palette.length && paletteKnown[r], "Indexed cel references undefined palette entry " + r);
            return palette[r];
        }
    }

    private static int mul255(int a, int b) { return (a * b + 127) / 255; }

    private static int over(int destination, int source, int opacity) {
        int sa = mul255(source & 255, opacity);
        if (sa == 0) { return destination; }
        int da = destination & 255;
        int alphaNumerator = sa * 255 + da * (255 - sa);
        int color = 0;
        for (int shift = 24; shift >= 8; shift -= 8) {
            int s = source >>> shift & 255;
            int d = destination >>> shift & 255;
            int channel = (s * sa * 255 + d * da * (255 - sa) + alphaNumerator / 2) / alphaNumerator;
            color |= channel << shift;
        }
        return color | (alphaNumerator + 127) / 255;
    }

    private static void require(boolean condition, String message) throws AsepriteException {
        if (!condition) { throw new AsepriteException(message); }
    }

    private static final class Cursor {
        private final ByteBuffer data;
        Cursor(byte[] bytes) { this(ByteBuffer.wrap(bytes)); }
        Cursor(ByteBuffer data) { this.data = data.order(ByteOrder.LITTLE_ENDIAN); }
        int size() { return data.limit(); }
        int remaining() { return data.remaining(); }
        void need(int size) throws AsepriteException { require(size >= 0 && size <= remaining(), "Truncated file/frame/chunk"); }
        int u8() throws AsepriteException { need(1); return data.get() & 255; }
        int u16() throws AsepriteException { need(2); return data.getShort() & 65535; }
        int i16() throws AsepriteException { need(2); return data.getShort(); }
        int i32() throws AsepriteException { need(4); return data.getInt(); }
        long u32() throws AsepriteException { return Integer.toUnsignedLong(i32()); }
        int length() throws AsepriteException {
            long value = u32();
            require(value <= Integer.MAX_VALUE, "Length/index exceeds supported range");
            return (int) value;
        }
        void skip(int size) throws AsepriteException { need(size); data.position(data.position() + size); }
        Cursor take(int size) throws AsepriteException {
            need(size);
            ByteBuffer slice = data.slice();
            slice.limit(size);
            skip(size);
            return new Cursor(slice);
        }
        byte[] bytes(int size) throws AsepriteException { need(size); byte[] result = new byte[size]; data.get(result); return result; }
        int rgba() throws AsepriteException { return u8() << 24 | u8() << 16 | u8() << 8 | u8(); }
        String string() throws AsepriteException {
            byte[] bytes = bytes(u16());
            try {
                return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException exception) { throw new AsepriteException("Invalid UTF-8 string", exception); }
        }
    }
}
