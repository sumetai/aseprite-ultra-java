package io.github.asepriteultra;

import java.util.Objects;

/** Immutable, top-left-origin image. Pixels are straight-alpha packed 0xRRGGBBAA. */
public final class RgbaImage {
    private final int width;
    private final int height;
    private final int[] pixels;

    public RgbaImage(int width, int height, int[] pixels) {
        Objects.requireNonNull(pixels, "pixels");
        if (width <= 0 || height <= 0 || (long) width * height != pixels.length) {
            throw new IllegalArgumentException("Invalid image dimensions/pixel count");
        }
        this.width = width;
        this.height = height;
        this.pixels = pixels.clone();
    }

    public int width() { return width; }
    public int height() { return height; }
    public int[] pixels() { return pixels.clone(); }
    public int pixel(int x, int y) {
        Objects.checkIndex(x, width);
        Objects.checkIndex(y, height);
        return pixels[y * width + x];
    }
}
