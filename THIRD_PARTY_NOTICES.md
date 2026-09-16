# References and third-party material

The Java implementation is original code written against the published
[Aseprite file format specification](https://github.com/aseprite/aseprite/blob/main/docs/ase-file-specs.md).
It is inspired by the direct-loading, atlas and animation workflow of
[Lommix/bevy_aseprite_ultra](https://github.com/Lommix/bevy_aseprite_ultra) (MIT).
[elgopher/aseprite-file](https://github.com/elgopher/aseprite-file) (Apache-2.0)
was evaluated as a Java reference; none of its implementation is included.
This project is not affiliated with or endorsed by either upstream project or Aseprite.

## Upstream compatibility fixtures

`core/src/test/resources/upstream/{player,ball,ghost_slices}.aseprite` are
unchanged example assets from `Lommix/bevy_aseprite_ultra`, commit
`fed6f83b2426a86fb15f46e40da5037c93fcf4dc`, paths `assets/*.aseprite`.
Their MIT license and copyright notice are preserved alongside them in
`core/src/test/resources/upstream/LICENSE`. These fixtures are test-only and
are not shipped in the runtime JARs.

`samples/player.aseprite` and `core/src/test/resources/player-export.png` are
the original generated sample art from the accompanying Pixel Game Java starter,
released here under this repository's MIT license.

The Gradle wrapper is generated Gradle tooling, licensed under Apache-2.0:
https://github.com/gradle/gradle/blob/v8.13.0/LICENSE

The optional libGDX adapter depends on libGDX (Apache-2.0). Consumers obtain
libGDX as a separate dependency; it is not shaded into this library's JAR.
