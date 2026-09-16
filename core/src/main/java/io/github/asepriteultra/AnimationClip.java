package io.github.asepriteultra;

import java.util.Arrays;

/** Stateless timing with exact frame boundaries. Ping-pong cycles do not duplicate endpoints. */
public final class AnimationClip {
    private final int[] sequence;
    private final long[] ends;
    private final long durationMs;

    AnimationClip(Aseprite sprite, Aseprite.Tag tag) {
        int count = tag.to() - tag.from() + 1;
        boolean pingPong = tag.direction() == Aseprite.Direction.PING_PONG
                || tag.direction() == Aseprite.Direction.PING_PONG_REVERSE;
        boolean reverse = tag.direction() == Aseprite.Direction.REVERSE
                || tag.direction() == Aseprite.Direction.PING_PONG_REVERSE;
        sequence = new int[pingPong && count > 1 ? count * 2 - 2 : count];
        ends = new long[sequence.length];
        long total = 0;
        for (int i = 0; i < sequence.length; i++) {
            int offset = i < count ? i : 2 * count - 2 - i;
            int frame = reverse ? tag.to() - offset : tag.from() + offset;
            sequence[i] = frame;
            total += sprite.frames().get(frame).durationMs();
            ends[i] = total;
        }
        durationMs = total;
    }

    public long durationMs() { return durationMs; }
    public int[] sequence() { return sequence.clone(); }

    /** Loop forever. Negative elapsed times are rejected. */
    public int frameAt(long elapsedMs) { return frameAt(elapsedMs, true); }

    /** Non-looping playback holds the final entry in this clip's sequence. */
    public int frameAt(long elapsedMs, boolean loop) {
        if (elapsedMs < 0) { throw new IllegalArgumentException("Negative elapsed time"); }
        long time = loop ? elapsedMs % durationMs : Math.min(elapsedMs, durationMs - 1);
        int index = Arrays.binarySearch(ends, time + 1);
        return sequence[index >= 0 ? index : -index - 1];
    }
}
