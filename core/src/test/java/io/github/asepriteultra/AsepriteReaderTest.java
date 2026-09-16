package io.github.asepriteultra;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static io.github.asepriteultra.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

final class AsepriteReaderTest {
    private final AsepriteReader reader = new AsepriteReader();

    @Test void rawAndCompressedRgbaMatchAndClipNegativeCelCoordinates() throws IOException {
        byte[] pixels = rgba(0xff0000ff, 0x00ff00ff, 0x0000ffff, 0xffffffff);
        for (boolean compressed : new boolean[] {false, true}) {
            Aseprite sprite = reader.read(file(2, 1, 32, 1,
                    frame(80, layer(), cel(0, -1, 0, 255, 4, 1, pixels, compressed))));
            assertArrayEquals(new int[] {0x00ff00ff, 0x0000ffff}, sprite.frames().get(0).image().pixels());
            assertEquals(80, sprite.frames().get(0).durationMs());
        }
    }

    @Test void grayscalePreservesStraightAlphaAndEmptyPixels() throws IOException {
        Aseprite sprite = reader.read(file(3, 1, 16, 1, frame(100, layer(),
                cel(0, 0, 0, 255, 2, 1, new byte[] {80, (byte) 128, (byte) 255, 0}, true))));
        assertArrayEquals(new int[] {0x50505080, 0, 0}, sprite.frames().get(0).image().pixels());
    }

    @Test void composesLayerAndCelOpacity() throws IOException {
        Aseprite sprite = reader.read(file(1, 1, 32, 1, frame(100, layer(),
                layer("top", 1, 0, 0, 128, 0),
                cel(0, 0, 0, 255, 1, 1, rgba(0x0000ffff), false),
                cel(1, 0, 0, 128, 1, 1, rgba(0xff0000ff), false))));
        assertEquals(0x4000bfff, sprite.frames().get(0).image().pixel(0, 0));
    }

    @Test void hiddenAndReferenceLayersDoNotRender() throws IOException {
        Aseprite sprite = reader.read(file(1, 1, 32, 1, frame(100,
                layer("hidden", 0, 0, 0, 255, 0), layer("reference", 65, 0, 0, 255, 0),
                cel(0, 0, 0, 255, 1, 1, rgba(-1), false), cel(1, 0, 0, 255, 1, 1, rgba(-1), false))));
        assertEquals(0, sprite.frames().get(0).image().pixel(0, 0));
    }

    @Test void groupOpacityIsAppliedOnceAfterChildrenCompose() throws IOException {
        // Group-opacity validity is independent of the ordinary layer-opacity flag.
        for (int flags : new int[] {2, 3}) {
            Aseprite sprite = reader.read(file(1, 1, 32, flags, frame(100,
                    layer("group", 1, 1, 0, 128, 0), layer("red", 1, 0, 1, 255, 0), layer("blue", 1, 0, 1, 255, 0),
                    cel(1, 0, 0, 255, 1, 1, rgba(0xff0000ff), false),
                    cel(2, 0, 0, 255, 1, 1, rgba(0x0000ffff), false))));
            assertEquals(0x0000ff80, sprite.frames().get(0).image().pixel(0, 0));
        }
    }

    @Test void hiddenGroupHidesChildrenAndLegacyOpacityIsIgnored() throws IOException {
        Aseprite hidden = reader.read(file(1, 1, 32, 3, frame(100,
                layer("group", 0, 1, 0, 255, 0), layer("child", 1, 0, 1, 255, 0),
                cel(1, 0, 0, 255, 1, 1, rgba(-1), false))));
        assertEquals(0, hidden.frames().get(0).image().pixel(0, 0));
        Aseprite legacy = reader.read(file(1, 1, 32, 0, frame(0,
                layer("legacy", 1, 0, 0, 0, 0), cel(0, 0, 0, 255, 1, 1, rgba(-1), false))));
        assertEquals(-1, legacy.frames().get(0).image().pixel(0, 0));
        assertEquals(100, legacy.frames().get(0).durationMs());
    }

    @Test void linkedCelsReusePixelsButNotPositionOrOpacity() throws IOException {
        Aseprite sprite = reader.read(file(3, 1, 32, 1,
                frame(100, layer(), cel(0, 0, 0, 255, 1, 1, rgba(0xff0000ff), true)),
                frame(100, linked(0, 0, 1, 128)), frame(100, linked(0, 1, 2, 255))));
        assertArrayEquals(new int[] {0, 0xff000080, 0}, sprite.frames().get(1).image().pixels());
        assertArrayEquals(new int[] {0, 0, 0xff0000ff}, sprite.frames().get(2).image().pixels());
    }

    @Test void indexedLinkedCelsUseCurrentFramePaletteAndTransparentIndex() throws IOException {
        Aseprite sprite = reader.read(file(2, 1, 8, 1,
                frame(100, layer(), palette(0, -1, 0xff0000ff), cel(0, 0, 0, 255, 2, 1, new byte[] {0, 1}, true)),
                frame(100, palette(1, 0x00ff00ff), linked(0, 0, 0, 255))));
        assertArrayEquals(new int[] {0, 0xff0000ff}, sprite.frames().get(0).image().pixels());
        assertArrayEquals(new int[] {0, 0x00ff00ff}, sprite.frames().get(1).image().pixels());
    }

    @Test void indexedBackgroundDoesNotHideTransparentPaletteIndex() throws IOException {
        Aseprite sprite = reader.read(file(1, 1, 8, 1, frame(100,
                layer("background", 9, 0, 0, 255, 0), palette(0, 0xff0000ff),
                cel(0, 0, 0, 255, 1, 1, new byte[] {0}, false))));
        assertEquals(0xff0000ff, sprite.frames().get(0).image().pixel(0, 0));
    }

    @Test void oldPalettesAndModernPalettePrecedence() throws IOException {
        for (int type : new int[] {0x0004, 0x0011}) {
            byte[] old = chunk(type, new Bytes().u16(1).u8(1).u8(1).u8(type == 0x11 ? 63 : 255).u8(0).u8(0).get());
            byte[] cel = cel(0, 0, 0, 255, 1, 1, new byte[] {1}, false);
            assertEquals(0xff0000ff, reader.read(file(1, 1, 8, 1, frame(100, layer(), old, cel)))
                    .frames().get(0).image().pixel(0, 0));
            for (boolean first : new boolean[] {false, true}) {
                byte[] modern = palette(1, 0x00ff00ff);
                Aseprite sprite = reader.read(file(1, 1, 8, 1,
                        frame(100, layer(), first ? modern : old, first ? old : modern, cel)));
                assertEquals(0x00ff00ff, sprite.frames().get(0).image().pixel(0, 0));
            }
        }
    }

    @Test void readsSlicesWithPivotAndNinePatchCenter() throws IOException {
        byte[] slice = chunk(0x2022, new Bytes().u32(1).u32(3).u32(0).string("hitbox")
                .u32(0).u32(-2).u32(3).u32(4).u32(5).u32(1).u32(1).u32(2).u32(3).u32(2).u32(4).get());
        Aseprite sprite = reader.read(file(1, 1, 32, 1, frame(100, layer(), slice)));
        Aseprite.SliceKey key = sprite.slices().get("hitbox").atFrame(10).orElseThrow();
        assertEquals(new Aseprite.Rect(-2, 3, 4, 5), key.bounds());
        assertEquals(new Aseprite.Rect(1, 1, 2, 3), key.center());
        assertEquals(new Aseprite.Point(2, 4), key.pivot());
        assertTrue(sprite.slices().get("hitbox").atFrame(-1).isEmpty());
    }

    @Test void unknownChunkDoesNotDesynchronizeReaderAndIsReported() throws IOException {
        Aseprite sprite = reader.read(file(1, 1, 32, 1, frame(100,
                chunk(0x7777, new byte[] {3, 2, 1}), layer(), cel(0, 0, 0, 255, 1, 1, rgba(-1), false))));
        assertEquals(-1, sprite.frames().get(0).image().pixel(0, 0));
        assertEquals(java.util.List.of("Skipped chunk 0x7777"), sprite.warnings());
    }

    @Test void rejectsUnsupportedVisualFeaturesAndBadHierarchy() {
        for (byte[] layer : new byte[][] {
                layer("multiply", 1, 0, 0, 255, 1), layer("tilemap", 1, 2, 0, 255, 0),
                layer("orphan", 1, 0, 1, 255, 0)}) {
            assertThrows(AsepriteException.class, () -> reader.read(file(1, 1, 32, 1, frame(100, layer))));
        }
        byte[] cel = cel(0, 0, 0, 255, 1, 1, rgba(-1), false);
        cel[15] = 1; // z-index low byte, following the six-byte chunk header.
        assertThrows(AsepriteException.class, () -> reader.read(file(1, 1, 32, 1, frame(100, layer(), cel))));
    }

    @Test void rejectsInvalidLinksDuplicateCelsAndUndefinedPalette() {
        assertThrows(AsepriteException.class, () -> reader.read(file(1, 1, 32, 1, frame(100, layer(), linked(0, 0, 0, 255)))));
        assertThrows(AsepriteException.class, () -> reader.read(file(1, 1, 32, 1,
                frame(100, layer()), frame(100, linked(0, 0, 0, 255)))));
        byte[] cel = cel(0, 0, 0, 255, 1, 1, rgba(-1), false);
        assertThrows(AsepriteException.class, () -> reader.read(file(1, 1, 32, 1, frame(100, layer(), cel, cel))));
        assertThrows(AsepriteException.class, () -> reader.read(file(1, 1, 8, 1,
                frame(100, layer(), cel(0, 0, 0, 255, 1, 1, new byte[] {1}, false)))));
    }

    @Test void rejectsCorruptOrWrongSizeZlib() {
        for (byte[] pixels : new byte[][] {rgba(-1, -1), new byte[] {1, 2}}) {
            assertThrows(AsepriteException.class, () -> reader.read(file(1, 1, 32, 1,
                    frame(100, layer(), cel(0, 0, 0, 255, 1, 1, pixels, true)))));
        }
        byte[] bytes = simple(-1);
        bytes[bytes.length - 1] ^= 1;
        assertThrows(AsepriteException.class, () -> reader.read(bytes));
    }

    @Test void truncatedInputNeverLeaksBufferOrIndexExceptions() {
        byte[] complete = simple(-1);
        for (int length = 0; length < complete.length; length++) {
            byte[] truncated = Arrays.copyOf(complete, length);
            if (length >= 4) { put32(truncated, 0, length); }
            assertThrows(AsepriteException.class, () -> reader.read(truncated), "length=" + length);
        }
    }

    @Test void rejectsFrameAndChunkBoundaryViolations() {
        byte[] original = simple(-1);
        for (int offset : new int[] {0, 128, 144}) {
            for (int size : new int[] {0, 5, Integer.MAX_VALUE, -1}) {
                byte[] bytes = original.clone();
                put32(bytes, offset, size);
                assertThrows(AsepriteException.class, () -> reader.read(bytes));
            }
        }
    }

    @Test void boundedRandomMutationsOnlyReturnSpritesOrCheckedFormatErrors() {
        Random random = new Random(19);
        byte[] original = simple(-1);
        for (int i = 0; i < 1000; i++) {
            byte[] bytes = original.clone();
            bytes[random.nextInt(bytes.length)] = (byte) random.nextInt(256);
            try { reader.read(bytes); }
            catch (AsepriteException expected) { /* A malformed fixture must fail through the documented API. */ }
        }
    }

    @Test void enforcesInputAndDecodedMemoryLimits() {
        AsepriteReader limited = new AsepriteReader(new AsepriteReader.Limits(128, 16, 2, 2));
        assertThrows(AsepriteException.class, () -> limited.read(new ByteArrayInputStream(simple(-1))));
        AsepriteReader decoded = new AsepriteReader(new AsepriteReader.Limits(4096, 16, 2, 2));
        assertThrows(AsepriteException.class, () -> decoded.read(file(100, 100, 32, 1, frame(100, layer()))));
    }

    @Test void decodedImagesAndCollectionsCannotBeMutatedThroughAccessors() throws IOException {
        Aseprite sprite = reader.read(simple(-1));
        sprite.frames().get(0).image().pixels()[0] = 0;
        assertEquals(-1, sprite.frames().get(0).image().pixel(0, 0));
        assertThrows(UnsupportedOperationException.class, () -> sprite.frames().clear());
        assertThrows(IndexOutOfBoundsException.class, () -> sprite.frames().get(0).image().pixel(1, 0));
    }
}
