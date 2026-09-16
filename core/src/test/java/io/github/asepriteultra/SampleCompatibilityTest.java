package io.github.asepriteultra;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SampleCompatibilityTest {
    private Aseprite load(String path) throws IOException {
        try (InputStream input = Objects.requireNonNull(getClass().getResourceAsStream(path))) {
            return new AsepriteReader().read(input);
        }
    }

    @Test void gameSpriteMatchesExistingPngExportPixelForPixel() throws IOException {
        Aseprite sprite = load("/player.aseprite");
        BufferedImage expected;
        try (InputStream input = Objects.requireNonNull(getClass().getResourceAsStream("/player-export.png"))) {
            expected = ImageIO.read(input);
        }
        assertEquals(16, sprite.width());
        assertEquals(16, sprite.height());
        assertEquals(2, sprite.frames().size());
        assertEquals(java.util.Set.of("idle", "walk"), sprite.tags().keySet());
        for (int i = 0; i < sprite.frames().size(); i++) {
            RgbaImage frame = sprite.frames().get(i).image();
            for (int y = 0; y < frame.height(); y++) {
                for (int x = 0; x < frame.width(); x++) {
                    int argb = expected.getRGB(i * frame.width() + x, y);
                    int rgba = argb >>> 24 == 0 ? 0 : argb << 8 | argb >>> 24;
                    assertEquals(rgba, frame.pixel(x, y), "frame=" + i + ", x=" + x + ", y=" + y);
                }
            }
        }
    }

    @Test void loadsOriginalBevyUltraExampleFiles() throws IOException {
        for (String name : new String[] {"player", "ball", "ghost_slices"}) {
            Aseprite sprite = load("/upstream/" + name + ".aseprite");
            assertFalse(sprite.frames().isEmpty());
            boolean visible = false;
            for (Aseprite.Frame frame : sprite.frames()) {
                for (int pixel : frame.image().pixels()) { visible |= (pixel & 255) != 0; }
            }
            assertTrue(visible, name + " must render visible pixels");
            SpriteAtlas atlas = SpriteAtlas.pack(sprite, 4096, 1);
            assertEquals(sprite.frames().size(), atlas.regions().size());
            if (name.equals("ghost_slices")) { assertFalse(sprite.slices().isEmpty()); }
        }
    }
}
