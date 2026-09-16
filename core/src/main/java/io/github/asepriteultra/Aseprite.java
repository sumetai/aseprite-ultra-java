package io.github.asepriteultra;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable decoded sprite. Frames retain full canvas dimensions, including transparency. */
public final class Aseprite {
    public enum Direction { FORWARD, REVERSE, PING_PONG, PING_PONG_REVERSE }

    public record Frame(int durationMs, RgbaImage image) {
        public Frame {
            if (durationMs <= 0) { throw new IllegalArgumentException("Non-positive frame duration"); }
            Objects.requireNonNull(image, "image");
        }
    }

    public record Layer(String name, int flags, int type, int depth, int opacity) {
        public boolean visible() { return (flags & 1) != 0; }
        public boolean group() { return type == 1; }
    }

    /** repeat is the original Aseprite tag field; AnimationClip chooses runtime looping explicitly. */
    public record Tag(String name, int from, int to, Direction direction, int repeat) {
        public Tag {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(direction, "direction");
            if (from < 0 || to < from || repeat < 0) { throw new IllegalArgumentException("Invalid tag"); }
        }
    }

    public record Rect(int x, int y, int width, int height) { }
    public record Point(int x, int y) { }
    public record SliceKey(int frame, Rect bounds, Rect center, Point pivot) { }
    public record Slice(String name, List<SliceKey> keys) {
        public Slice { keys = List.copyOf(keys); }
        public Optional<SliceKey> atFrame(int frame) {
            SliceKey active = null;
            for (SliceKey key : keys) {
                if (key.frame() > frame) { break; }
                active = key;
            }
            return Optional.ofNullable(active);
        }
    }

    private final int width;
    private final int height;
    private final List<Frame> frames;
    private final List<Layer> layers;
    private final Map<String, Tag> tags;
    private final Map<String, Slice> slices;
    private final List<String> warnings;

    Aseprite(int width, int height, List<Frame> frames, List<Layer> layers,
             Map<String, Tag> tags, Map<String, Slice> slices, List<String> warnings) {
        this.width = width;
        this.height = height;
        this.frames = List.copyOf(frames);
        this.layers = List.copyOf(layers);
        this.tags = Map.copyOf(tags);
        this.slices = Map.copyOf(slices);
        this.warnings = List.copyOf(warnings);
    }

    public int width() { return width; }
    public int height() { return height; }
    public List<Frame> frames() { return frames; }
    public List<Layer> layers() { return layers; }
    public Map<String, Tag> tags() { return tags; }
    public Map<String, Slice> slices() { return slices; }
    public List<String> warnings() { return warnings; }

    public AnimationClip animation(String tagName) {
        Tag tag = tags.get(tagName);
        if (tag == null) { throw new IllegalArgumentException("Unknown animation tag: " + tagName); }
        return new AnimationClip(this, tag);
    }

    public AnimationClip animation() {
        return new AnimationClip(this, new Tag("", 0, frames.size() - 1, Direction.FORWARD, 0));
    }
}
