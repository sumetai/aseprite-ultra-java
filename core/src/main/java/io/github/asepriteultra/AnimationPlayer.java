package io.github.asepriteultra;

import java.util.Objects;

/** Mutable playback clock. Use one per animated entity, sharing the immutable clip. */
public final class AnimationPlayer {
    private AnimationClip clip;
    private long elapsedMs;
    private int cycles;
    private boolean paused;

    /** cycles = 0 loops forever; positive values count complete clip cycles. */
    public AnimationPlayer(AnimationClip clip, int cycles) { play(clip, cycles); }

    public void play(AnimationClip next, int repeatCycles) {
        Objects.requireNonNull(next, "clip");
        if (repeatCycles < 0) { throw new IllegalArgumentException("Negative cycle count"); }
        clip = next;
        cycles = repeatCycles;
        elapsedMs = 0;
        paused = false;
    }

    /** Returns true exactly when this update finishes finite playback. */
    public boolean update(long deltaMs) {
        if (deltaMs < 0) { throw new IllegalArgumentException("Negative delta"); }
        if (paused || finished()) { return false; }
        if (cycles == 0) {
            elapsedMs = (elapsedMs + deltaMs % clip.durationMs()) % clip.durationMs();
        } else {
            long end = clip.durationMs() * cycles;
            elapsedMs += Math.min(deltaMs, end - elapsedMs);
        }
        return finished();
    }

    public boolean finished() { return cycles > 0 && elapsedMs >= clip.durationMs() * cycles; }
    public int frameIndex() {
        return finished() ? clip.frameAt(clip.durationMs(), false) : clip.frameAt(elapsedMs);
    }
    public boolean paused() { return paused; }
    public void setPaused(boolean value) { paused = value; }
    public void restart() { elapsedMs = 0; }
}
