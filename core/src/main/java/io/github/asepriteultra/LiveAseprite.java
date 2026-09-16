package io.github.asepriteultra;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Optional;

/** Poll from the owning thread. A failed read keeps the last good immutable sprite available. */
public final class LiveAseprite {
    public enum Update { UNCHANGED, RELOADED, FAILED }
    private record Stamp(long modified, long size) { }
    private final Path path;
    private final AsepriteReader reader;
    private final long debounceMs;
    private Aseprite current;
    private Stamp observed;
    private long quietMs;
    private boolean dirty;
    private IOException lastError;

    public LiveAseprite(Path path) throws IOException { this(path, new AsepriteReader(), 250); }

    public LiveAseprite(Path path, AsepriteReader reader, long debounceMs) throws IOException {
        this.path = Objects.requireNonNull(path);
        this.reader = Objects.requireNonNull(reader);
        if (debounceMs < 0) { throw new IllegalArgumentException("Negative debounce"); }
        this.debounceMs = debounceMs;
        observed = stamp();
        current = reader.read(path);
    }

    public Aseprite current() { return current; }
    public Optional<IOException> lastError() { return Optional.ofNullable(lastError); }

    /** Poll frequency is chosen by the caller; deltaMs is time since the previous poll. */
    public Update update(long deltaMs) {
        if (deltaMs < 0) { throw new IllegalArgumentException("Negative delta"); }
        try {
            Stamp next = stamp();
            if (!next.equals(observed)) {
                observed = next;
                quietMs = 0;
                dirty = true;
                return Update.UNCHANGED;
            }
            if (!dirty) { return Update.UNCHANGED; }
            quietMs += Math.min(deltaMs, debounceMs - quietMs);
            return quietMs >= debounceMs ? reload() : Update.UNCHANGED;
        } catch (IOException exception) {
            lastError = exception;
            dirty = true;
            quietMs = 0;
            return Update.FAILED;
        }
    }

    public Update reload() {
        dirty = false;
        quietMs = 0;
        try {
            Stamp before = stamp();
            Aseprite next = reader.read(path);
            if (!before.equals(stamp())) { throw new IOException("File changed during reload"); }
            current = next;
            observed = before;
            lastError = null;
            return Update.RELOADED;
        } catch (IOException exception) {
            lastError = exception;
            return Update.FAILED;
        }
    }

    private Stamp stamp() throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        return new Stamp(attrs.lastModifiedTime().to(java.util.concurrent.TimeUnit.NANOSECONDS), attrs.size());
    }
}
