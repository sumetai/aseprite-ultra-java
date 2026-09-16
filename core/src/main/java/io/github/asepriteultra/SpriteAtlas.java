package io.github.asepriteultra;

import java.util.ArrayList;
import java.util.List;

/** Deterministic grid atlas with full-size frames and transparent padding. No rotation or trimming. */
public record SpriteAtlas(RgbaImage image, List<Aseprite.Rect> regions) {
    public SpriteAtlas { regions = List.copyOf(regions); }

    public static SpriteAtlas pack(Aseprite sprite, int maxSize, int padding) {
        if (padding < 0 || maxSize <= 0 || maxSize > 8192) {
            throw new IllegalArgumentException("Use maxSize 1..8192 and non-negative padding");
        }
        long cellWidth = sprite.width() + 2L * padding;
        long cellHeight = sprite.height() + 2L * padding;
        int count = sprite.frames().size();
        int columns = (int) Math.min(count, maxSize / cellWidth);
        if (columns == 0 || cellHeight > maxSize) { throw new IllegalArgumentException("Frame exceeds atlas limit"); }
        int rows = (count + columns - 1) / columns;
        if (rows * cellHeight > maxSize) { throw new IllegalArgumentException("Sprite exceeds single-atlas limit"); }
        int width = (int) (columns * cellWidth);
        int height = (int) (rows * cellHeight);
        if ((long) width * height > 16_777_216) { throw new IllegalArgumentException("Atlas exceeds 16M pixel budget"); }
        int[] pixels = new int[width * height];
        List<Aseprite.Rect> regions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int x = (int) (i % columns * cellWidth) + padding;
            int y = (int) (i / columns * cellHeight) + padding;
            RgbaImage frame = sprite.frames().get(i).image();
            int[] source = frame.pixels();
            for (int row = 0; row < frame.height(); row++) {
                System.arraycopy(source, row * frame.width(), pixels, (y + row) * width + x, frame.width());
            }
            regions.add(new Aseprite.Rect(x, y, frame.width(), frame.height()));
        }
        return new SpriteAtlas(new RgbaImage(width, height, pixels), regions);
    }
}
