package io.github.asepriteultra.examples;

import io.github.asepriteultra.Aseprite;
import io.github.asepriteultra.AsepriteReader;
import io.github.asepriteultra.SpriteAtlas;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import javax.imageio.ImageIO;

/** Headless command-line sample. Only this PNG-export example uses java.desktop/AWT. */
public final class ExportAtlas {
    private ExportAtlas() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) { throw new IllegalArgumentException("Usage: ExportAtlas input.aseprite output.png"); }
        Aseprite sprite = new AsepriteReader().read(Path.of(args[0]));
        SpriteAtlas atlas = SpriteAtlas.pack(sprite, 4096, 1);
        BufferedImage png = new BufferedImage(atlas.image().width(), atlas.image().height(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < png.getHeight(); y++) {
            for (int x = 0; x < png.getWidth(); x++) {
                int rgba = atlas.image().pixel(x, y);
                png.setRGB(x, y, rgba >>> 8 | rgba << 24);
            }
        }
        Path output = Path.of(args[1]).toAbsolutePath();
        Files.createDirectories(output.getParent());
        ImageIO.write(png, "png", output.toFile());
        System.out.printf("%dx%d, %d frames, tags: %s%n", sprite.width(), sprite.height(), sprite.frames().size(), sprite.tags());
        sprite.warnings().forEach(System.out::println);
    }
}
