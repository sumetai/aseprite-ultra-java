# Changelog

## 0.1.0

- Initial dependency-free Java 17+ binary reader and normal-layer compositor.
- RGBA, grayscale, indexed palettes, compressed/raw/linked cels and groups.
- Immutable full-canvas frames, named animation tags and keyed slice metadata.
- Four playback directions, finite/infinite playback and completion signal.
- Single-page atlas generation and last-good filesystem hot reload.
- Optional libGDX texture sheets and asynchronous AssetManager loader.
- Headless PNG export example, generated fixtures and upstream compatibility tests.

This is a scoped first release. Consult the README for unsupported format
features and differences from Bevy's runtime integration.
