# Aseprite Ultra Java

[![Build and test](https://github.com/sumetai/aseprite-ultra-java/actions/workflows/build.yml/badge.svg)](https://github.com/sumetai/aseprite-ultra-java/actions/workflows/build.yml)

**Load `.aseprite` files directly in Java.** Decode pixels, composite layers,
play tagged animations, build texture atlases, and reload edited files without
an Aseprite export step.

Inspired by [bevy_aseprite_ultra](https://github.com/Lommix/bevy_aseprite_ultra),
with an engine-independent Java core and an optional **libGDX** adapter.
This is an initial Java implementation of that workflow, not a feature-complete
port of the Bevy plugin. See the support table below.

**Java 17+ runtime · JDK 21 to build · MIT license**

| Module | What it provides |
| --- | --- |
| `core` | Binary reader, immutable RGBA frames, layer/tag/slice metadata, animation, atlas packing, hot reload |
| `gdx` | Nearest-filtered GPU sheets and an asynchronous `AssetManager` loader; libGDX 1.14.2 |
| `examples` | Headless `.aseprite` → PNG atlas command-line example |

The core has **zero runtime dependencies**, no AWT requirement, no native code,
and no graphics-context requirement. PNG export uses `java.desktop` only in the
example. GPU rendering requires the optional adapter and libGDX's platform backend.

## Build and try it

Set `JAVA_HOME` to JDK 21, then:

```powershell
git clone https://github.com/sumetai/aseprite-ultra-java.git
cd aseprite-ultra-java
.\gradlew.bat build
.\gradlew.bat :examples:run --args="samples/player.aseprite build/player-atlas.png"
```

On Linux/macOS, use `./gradlew`. Tests run without a window or GPU. They cover
binary bounds, corrupt zlib, compositing, palettes, linked cels, animation,
atlas layout, reload recovery, original Bevy sample files, and a pixel-for-pixel
comparison with the accompanying game's PNG sprite export.

Runtime JARs, source JARs and Javadocs are under `core/build/libs/` and
`gdx/build/libs/`. The two runtime artifacts are `aseprite-ultra-core-0.1.0.jar`
and `aseprite-ultra-gdx-0.1.0.jar`; their Maven artifact IDs are shown below.

### Add to another Gradle project

Source and [release JARs](https://github.com/sumetai/aseprite-ultra-java/releases)
are hosted on GitHub; it is **not published to Maven Central**.
For local use, first run this in the library checkout:

```powershell
.\gradlew.bat publishToMavenLocal
```

Then in your app:

```groovy
repositories {
    mavenCentral()
    mavenLocal { content { includeGroup 'io.github.asepriteultra' } }
}
dependencies {
    implementation 'io.github.asepriteultra:aseprite-ultra-core:0.1.0'
    // Or use this, which includes the core transitively:
    // implementation 'io.github.asepriteultra:aseprite-ultra-gdx:0.1.0'
}
```

For simultaneous app/library development, use a Gradle composite build in the
app's `settings.gradle`:

```groovy
includeBuild('../aseprite-ultra-java') {
    dependencySubstitution {
        substitute module('io.github.asepriteultra:aseprite-ultra-core') using project(':core')
        substitute module('io.github.asepriteultra:aseprite-ultra-gdx') using project(':gdx')
    }
}
```

## Plain Java

```java
import io.github.asepriteultra.*;
import java.nio.file.Path;

Aseprite sprite = new AsepriteReader().read(Path.of("player.aseprite"));
AnimationClip walk = sprite.animation("walk");
int frameIndex = walk.frameAt(250); // milliseconds; loops forever
RgbaImage image = sprite.frames().get(frameIndex).image();
int rgba = image.pixel(8, 8);       // packed 0xRRGGBBAA, straight alpha

SpriteAtlas atlas = SpriteAtlas.pack(sprite, 4096, 1);
Aseprite.Rect uvPixels = atlas.regions().get(frameIndex);
```

`read(InputStream)` leaves the caller's stream open. `read(Path)` owns and closes
its stream. Malformed or explicitly unsupported visual data throws
`AsepriteException` (an `IOException`), with bounded input/decompression.

Coordinates use a **top-left origin**, +Y down. Every frame has the original
canvas dimensions. Returned images and decoded collections are immutable;
`pixels()` and `sequence()` return copies. The library does not cache every
sprite globally or allocate per-pixel objects.

### Playback

```java
AnimationPlayer player = new AnimationPlayer(sprite.animation("walk"), 0);
// Once per simulation update, with a non-negative millisecond delta:
boolean justFinished = player.update(16);
int currentFrame = player.frameIndex();
player.setPaused(true);
player.restart();
```

`0` cycles means infinite looping; positive values count complete clip cycles.
`update` returns true only on the transition to finished, so it can trigger the
next gameplay action. `play(otherClip, cycles)` switches animations and resets
the clock. `sprite.animation()` plays the complete file timeline; unknown named
tags throw an exception rather than silently choosing a different animation.

Directions: forward, reverse, ping-pong and ping-pong-reverse. Ping-pong avoids
duplicating endpoints (`0,1,2,1,...`). A finite clip holds its last sequence entry.
Frame durations come from the file, including the old header-speed fallback.

The raw tag `repeat` field is preserved as metadata. Runtime cycles are chosen
explicitly: they are **not Aseprite's direction-leg repeat semantics**, particularly
for ping-pong. Automatic animation queues and per-frame event streams are not
part of v0.1. Fractional millisecond accumulation belongs to the caller when
converting a floating-point game clock.

### Slices

```java
var hitbox = sprite.slices().get("hitbox");
var active = hitbox.atFrame(frameIndex);
active.ifPresent(key -> {
    var bounds = key.bounds(); // canvas-relative bounds
    var pivot = key.pivot();   // nullable, relative to slice origin
    var center = key.center();// nullable, nine-patch center relative to bounds
});
```

Slices expose keyed metadata; the atlas contains full frames, not separately
packed slice images. A zero-width/height slice key represents a hidden slice.

## libGDX

Create the sheet on the rendering thread:

```java
import io.github.asepriteultra.gdx.AsepriteSheet;

AsepriteSheet sheet = AsepriteSheet.load(Gdx.files.internal("player.aseprite"));
// Inside a SpriteBatch begin/end block:
batch.draw(sheet.frame("walk", elapsedMs), x, y);
// Also usable by DecalBatch for 3D billboards.
// At shutdown:
sheet.dispose();
```

Or use `AssetManager` (decode on its worker; GL upload during `update()`):

```java
manager.setLoader(AsepriteSheet.class, new AsepriteAssetLoader());
manager.load("player.aseprite", AsepriteSheet.class);
// In render(), once manager.update() returns true:
AsepriteSheet sheet = manager.get("player.aseprite", AsepriteSheet.class);
// AssetManager owns disposal in this case.
```

The sheet owns the texture and regions. Do not mutate its regions or retain
them after replacing/disposal of the sheet. Atlas packing is single-page,
untrimmed and unrotated, with transparent padding and nearest filtering.
Choose a max size supported by your GPU; packing rejects files that do not fit
one page. Atlas allocation is capped at 16M pixels.

## Hot reload

```java
LiveAseprite live = new LiveAseprite(Path.of("player.aseprite"));

// Poll from the owning thread. Here, 250ms since the previous poll:
switch (live.update(250)) {
    case RELOADED -> { /* replace CPU references / rebuild your GPU sheet */ }
    case FAILED -> System.err.println(live.lastError().orElseThrow().getMessage());
    case UNCHANGED -> { }
}
```

Changes are detected through modification time and file size and debounced for
250ms by default. A failed read keeps the last good sprite. `reload()` forces a
retry even when the timestamp/size is unchanged. After a parse failure, another
save or forced reload retries. Missing files are checked again on later polls.
There are no background threads to manage or stop. This watcher targets real
filesystem files, not resources inside a JAR.

For libGDX, create the replacement sheet completely before swapping references
and disposing the old sheet. Do that on the GL thread, refresh any stored
`TextureRegion` references, and keep entity playback time separately. If GPU
upload fails, retain the old sheet. `AssetManager` does not automatically hot
reload an already loaded asset.

## v0.1 format support

| Feature | Status |
| --- | --- |
| RGBA / grayscale / indexed color | Supported |
| Raw and zlib-compressed image cels | Supported |
| Linked cels | Backward links, including chains; per-link position/opacity |
| Modern and old palettes | Supported; palette changes apply per frame |
| Normal layers and groups | Visibility, reference-layer exclusion, cel/layer opacity, isolated group opacity |
| Clipped/off-canvas cels | Supported |
| Frame durations and four tag directions | Supported |
| Slices, pivots, nine-patch keys | Metadata supported |
| Non-normal blend modes | Rejected |
| Tilemaps/tilesets, external-file dependencies | Rejected |
| Nonzero cel z-index, precise/scaled bounds | Rejected |
| Forward-linked cels | Rejected |
| User data / ICC color management | Not exposed/applied; reported in `warnings()` |
| Unknown chunks | Skipped within chunk boundaries and reported in `warnings()` |
| Writing `.aseprite`, Bevy ECS/material/UI systems | Not included |

The compositor uses straight-alpha normal source-over with integer rounding.
Color samples are treated as sRGB without ICC conversion. It is not a promise
of identical rendering for every Aseprite document; check the feature table and
`warnings()` when importing art.

Default decode limits: 64 MiB input, 128 MiB accounted pixel/decode buffers,
4096 frames, 1024 layers, 64 group levels, and 65,536 chunks per frame.
`AsepriteReader.Limits` customizes the first four. The decoded budget is
conservative accounting, not a hard cap on total JVM heap usage.

## License and credits

MIT. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
Upstream example assets used by compatibility tests retain their original MIT
license. Contributions should include a focused fixture/test for each format
or animation behavior they change.
