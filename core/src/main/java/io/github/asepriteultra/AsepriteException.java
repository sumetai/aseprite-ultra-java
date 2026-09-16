package io.github.asepriteultra;

import java.io.IOException;

/** A malformed file or a visual feature this release cannot faithfully render. */
public final class AsepriteException extends IOException {
    private static final long serialVersionUID = 1L;

    public AsepriteException(String message) { super(message); }
    public AsepriteException(String message, Throwable cause) { super(message, cause); }
}
