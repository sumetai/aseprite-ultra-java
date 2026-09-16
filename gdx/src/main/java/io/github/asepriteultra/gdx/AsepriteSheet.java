package io.github.asepriteultra.gdx;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import io.github.asepriteultra.AnimationClip;
import io.github.asepriteultra.Aseprite;
import io.github.asepriteultra.AsepriteReader;
import io.github.asepriteultra.SpriteAtlas;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Owns one nearest-filtered texture. Construct/dispose on libGDX's GL thread. */
public final class AsepriteSheet implements Disposable {
    private final Aseprite sprite;
    private final Texture texture;
    private final TextureRegion[] regions;
    private final Map<String, AnimationClip> clips = new HashMap<>();

    public static AsepriteSheet load(FileHandle file) throws IOException {
        try (InputStream input = file.read()) { return new AsepriteSheet(new AsepriteReader().read(input), 4096, 1); }
    }

    public AsepriteSheet(Aseprite sprite, int maxAtlasSize, int padding) {
        this.sprite = sprite;
        SpriteAtlas atlas = SpriteAtlas.pack(sprite, maxAtlasSize, padding);
        Pixmap pixmap = new Pixmap(atlas.image().width(), atlas.image().height(), Pixmap.Format.RGBA8888);
        try {
            pixmap.setBlending(Pixmap.Blending.None);
            for (int y = 0; y < pixmap.getHeight(); y++) {
                for (int x = 0; x < pixmap.getWidth(); x++) { pixmap.drawPixel(x, y, atlas.image().pixel(x, y)); }
            }
            texture = new Texture(pixmap);
        } finally { pixmap.dispose(); }
        try {
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            regions = new TextureRegion[atlas.regions().size()];
            for (int i = 0; i < regions.length; i++) {
                Aseprite.Rect rect = atlas.regions().get(i);
                regions[i] = new TextureRegion(texture, rect.x(), rect.y(), rect.width(), rect.height());
            }
            for (String name : sprite.tags().keySet()) { clips.put(name, sprite.animation(name)); }
        } catch (RuntimeException exception) { texture.dispose(); throw exception; }
    }

    public Aseprite sprite() { return sprite; }
    public Texture texture() { return texture; }
    /** Regions are owned by this sheet; callers should not mutate or retain them after disposal/reload. */
    public TextureRegion frame(int index) { return regions[index]; }
    public TextureRegion frame(String tag, long elapsedMs) {
        AnimationClip clip = clips.get(tag);
        if (clip == null) { throw new IllegalArgumentException("Unknown animation tag: " + tag); }
        return regions[clip.frameAt(elapsedMs)];
    }
    @Override public void dispose() { texture.dispose(); }
}
