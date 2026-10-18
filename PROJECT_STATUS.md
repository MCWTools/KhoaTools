# KhoaTools Project Status

## Project

- Minecraft: 26.1.2
- Fabric Loader: 0.19.5
- Fabric API: 0.154.2+26.1.2
- Loom: 1.18.4 (resolved by Gradle)
- Java release: 25

## Recent work

- Planned, not implemented: client-side singleplayer OBJ/MTL/PNG model tool. The intended design is local asset loading from `.minecraft/khoatools/`, persistent model records, `/khoatoolsclient spawnmodel`, and nearest/all removal commands. No model parser, renderer, commands, or persistence for this feature currently exists.

- Fixed Playlist: tracks are now real music files listed from the resource manager (`sounds/music/**.ogg`) and played through a hand-resolved `Sound` (they were treated as sound events, so nothing played); next track starts when the sound stops; `MusicManagerMixin` holds back vanilla background music while a playlist plays; Loop now loops the playlist, not one track. GUI: lists had no x offset (track list drew over the playlist list), now positioned; buttons scale with width.
- Added Visible Barrier (`feature/visiblebarrier`): toggle spawns the vanilla BLOCK_MARKER particle on every barrier within 32 blocks (respawned every 40 ticks, marker lifetime is 80), any game mode.
- Added Find Leak (`feature/findleak`, config slider Max Distance 10-128): time-sliced BFS from the player to the nearest outside cell (open sky, unloaded chunk, scan-box edge), takes the narrowest cross-section on that path as a hole, seals it virtually and repeats; touching holes merge into one red Gizmos box. Closed pockets behind an opening are not leaks.
- Added chat coordinate click-to-copy for XZ and XYZ integer coordinates. Coordinate spans tolerate repeated whitespace, preserve formatted chat styles, copy a normalized coordinate, and show `Copied clicked coordinate to clipboard` in the action bar.
- Added the `ChatScreenMixin` handler for coordinate clipboard events; unrelated clipboard click events remain vanilla.
- Updated Fullbright lightmap extraction to force a refresh on every extraction, allowing vanilla values to be restored after toggling the feature off.
- When enabled, Fullbright sets full brightness and clears darkness and boss overlay dimming in the extracted lightmap state.
- The client mixin configuration includes `LightmapRenderStateExtractorMixin` in the generated resources.

## Verification

- Visible Barrier + Find Leak: compiled with javac 25 against the 26.1.2 client jar and Fabric API 0.154.2 jars (no errors in the new files); Find Leak algorithm run on synthetic rooms (sealed, wall/roof holes, adjacent holes, closet behind a doorway, missing wall). Gradle build and in-game test not run yet.
- `GRADLE_USER_HOME=C:\Users\Khoa-Code-Play\.gradle .\gradlew.bat build --offline` — **BUILD SUCCESSFUL** (2026-10-13).
- Generated artifact: `build/libs/khoatools-1.2.0.jar`.
- Generated client mixin configuration includes both `ChatScreenMixin` and `LightmapRenderStateExtractorMixin`.
- In-game verification after this build has not been recorded. Existing startup logs referenced a separate checkout under `Downloads`; they do not verify this artifact.

## Knowledge cache

- No version-specific Minecraft or Fabric API findings have been added to a shared knowledge index.
- Fullbright investigation used the resolved Minecraft 26.1.2 deobfuscated client artifact from the local Loom cache. No decompiled source or cache entry was created.
