package io.github.asepriteultra;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import static io.github.asepriteultra.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

final class AnimationAndAtlasTest {
    private Aseprite sprite(int direction) throws IOException {
        return new AsepriteReader().read(file(1, 1, 32, 1,
                frame(100, layer(), tags(direction, 0, 2), cel(0, 0, 0, 255, 1, 1, rgba(0xff0000ff), false)),
                frame(200, cel(0, 0, 0, 255, 1, 1, rgba(0x00ff00ff), false)),
                frame(300, cel(0, 0, 0, 255, 1, 1, rgba(0x0000ffff), false))));
    }

    @Test void allFourDirectionsHaveCorrectSequencesAndDurations() throws IOException {
        int[][] sequences = {{0, 1, 2}, {2, 1, 0}, {0, 1, 2, 1}, {2, 1, 0, 1}};
        for (int direction = 0; direction < 4; direction++) {
            Aseprite sprite = sprite(direction);
            AnimationClip clip = sprite.animation("walk");
            assertArrayEquals(sequences[direction], clip.sequence());
            long time = 0;
            for (int frame : sequences[direction]) {
                assertEquals(frame, clip.frameAt(time));
                time += sprite.frames().get(frame).durationMs();
                assertEquals(frame, clip.frameAt(time - 1));
            }
            assertEquals(time, clip.durationMs());
            assertEquals(sequences[direction][0], clip.frameAt(time));
            assertEquals(sequences[direction][sequences[direction].length - 1], clip.frameAt(time, false));
            assertEquals(3, sprite.tags().get("walk").repeat());
        }
    }

    @Test void oneFramePingPongAndInvalidTags() throws IOException {
        for (int direction = 0; direction < 4; direction++) {
            Aseprite sprite = new AsepriteReader().read(file(1, 1, 32, 1, frame(100, layer(), tags(direction, 0, 0))));
            assertArrayEquals(new int[] {0}, sprite.animation("walk").sequence());
            assertEquals(0, sprite.animation().frameAt(Long.MAX_VALUE));
        }
        assertThrows(AsepriteException.class, () -> new AsepriteReader().read(file(1, 1, 32, 1,
                frame(100, layer(), tags(0, 0, 2)))));
        assertThrows(IllegalArgumentException.class, () -> sprite(0).animation("missing"));
        assertThrows(IllegalArgumentException.class, () -> sprite(0).animation().frameAt(-1));
    }

    @Test void finitePlaybackPauseRestartAndCompletionAreExact() throws IOException {
        AnimationPlayer player = new AnimationPlayer(sprite(0).animation("walk"), 2);
        assertFalse(player.update(100));
        assertEquals(1, player.frameIndex());
        player.setPaused(true);
        assertFalse(player.update(500));
        assertEquals(1, player.frameIndex());
        player.setPaused(false);
        assertTrue(player.update(Long.MAX_VALUE));
        assertEquals(2, player.frameIndex());
        assertFalse(player.update(1));
        player.restart();
        assertFalse(player.finished());
        assertEquals(0, player.frameIndex());
        player.play(sprite(1).animation(), 0);
        assertFalse(player.update(Long.MAX_VALUE));
        assertFalse(player.finished());
    }

    @Test void atlasRegionsPaddingWrappingAndLimits() throws IOException {
        Aseprite sprite = sprite(0);
        SpriteAtlas atlas = SpriteAtlas.pack(sprite, 6, 1);
        assertEquals(6, atlas.image().width());
        assertEquals(6, atlas.image().height());
        assertEquals(new Aseprite.Rect(1, 4, 1, 1), atlas.regions().get(2));
        assertEquals(0xff0000ff, atlas.image().pixel(1, 1));
        assertEquals(0x00ff00ff, atlas.image().pixel(4, 1));
        assertEquals(0x0000ffff, atlas.image().pixel(1, 4));
        assertEquals(0, atlas.image().pixel(0, 0));
        assertThrows(IllegalArgumentException.class, () -> SpriteAtlas.pack(sprite, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> SpriteAtlas.pack(sprite, 3, 1));
    }
}
