package io.github.asepriteultra;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static io.github.asepriteultra.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

final class LiveAsepriteTest {
    @TempDir Path directory;

    @Test void debounceFailedWriteDeletionAndRecoveryPreserveLastGoodData() throws IOException {
        Path path = directory.resolve("sprite.aseprite");
        Files.write(path, simple(0xff0000ff));
        LiveAseprite live = new LiveAseprite(path);
        Aseprite initial = live.current();
        assertEquals(LiveAseprite.Update.UNCHANGED, live.update(1000));
        Files.write(path, new byte[] {1, 2, 3});
        assertEquals(LiveAseprite.Update.UNCHANGED, live.update(250));
        assertEquals(LiveAseprite.Update.UNCHANGED, live.update(249));
        assertEquals(LiveAseprite.Update.FAILED, live.update(1));
        assertSame(initial, live.current());
        assertTrue(live.lastError().isPresent());
        Files.delete(path);
        assertEquals(LiveAseprite.Update.FAILED, live.update(250));
        assertSame(initial, live.current());
        Files.write(path, simple(0x00ff00ff));
        Files.setLastModifiedTime(path, FileTime.fromMillis(123456789));
        assertEquals(LiveAseprite.Update.UNCHANGED, live.update(250));
        assertEquals(LiveAseprite.Update.RELOADED, live.update(250));
        assertEquals(0x00ff00ff, live.current().frames().get(0).image().pixel(0, 0));
        assertTrue(live.lastError().isEmpty());
    }

    @Test void forcedReloadWorksWithoutTimestampChange() throws IOException {
        Path path = directory.resolve("sprite.aseprite");
        Files.write(path, simple(-1));
        LiveAseprite live = new LiveAseprite(path);
        FileTime original = Files.getLastModifiedTime(path);
        Files.write(path, simple(0xff0000ff));
        Files.setLastModifiedTime(path, original);
        assertEquals(LiveAseprite.Update.RELOADED, live.reload());
        assertEquals(0xff0000ff, live.current().frames().get(0).image().pixel(0, 0));
    }
}
