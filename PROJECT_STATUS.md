# KhoaTools Project Status

## Project

- Minecraft: 26.1.2
- Fabric Loader: 0.19.5
- Fabric API: 0.154.2+26.1.2
- Loom: 1.18.4 (resolved by Gradle)
- Java release: 25

## Recent work

- Added Better Chat option `editmsg` (CTRL ALT CLICK to edit message, default off, 2026-10-29): `ChatScreenMixin` (mouseClicked) finds the clicked message, `ChatMessageEdit.begin` swaps it for a dark-grey copy (`khoatools$replace`) and the raw `&` text (`RawChatText`) is put in the chat box. Enter is intercepted at the head of `ChatScreen.handleChatInput` (nothing is sent to the server): `RawChatParser` turns `&c / &#rrggbb / &l &o &n &m &k / &r` back into a styled component and the grey message is replaced by it. Closing the chat (`ChatScreen.removed`) or sending an empty box restores the original. Edits are in memory only (not saved in the chat cache); original click/hover events are not kept.
- Added Better Chat option `copyraw` (CTRL SHIFT CLICK to copy raw message, default off, 2026-10-29): `ChatScreenMixin.mouseClicked` finds the clicked chat line with `ChatLineFinder` (an `ActiveTextCollector` fed to `ChatComponent.captureClickableText`, like vanilla's ClickableStyleFinder), maps it to its `GuiMessage` (`ChatComponentExt.khoatools$messageOfLine`) and copies `RawChatText.toRaw`: `&c` for vanilla colours, `&#rrggbb` for hex/gradient, `&l &o &n &m &k`, `&r`. Pasted text is only coloured on servers that translate `&` codes.
- Added Inventory Tweak (`feature/inventorytweak`, creative only, both options default off): CTRL ALT CLICK deletes the clicked inventory item, CTRL ALT SPACE on a hovered shulker box moves its items into the first empty/mergeable inventory slot from slot 0 up (nothing replaced; leftovers stay in the shulker, the empty shulker stays). Mixin `InventoryTweakScreenMixin` on `CreativeModeInventoryScreen` (mouseClicked / keyPressed); changes are sent with `handleCreativeModeItemAdd`.
- Added Camera (`feature/camera`, 2026-10-29): `/khoatoolsclient camera [create|changelocation|select|watch|stopwatch|rotateable|list|delete] <name>`; no arg toggles the feature (also a card + config GUI: Enabled, Rotateable default off). Cameras are saved per server + dimension in `config/khoatools-cameras.json`. Watch moves the render camera (`CameraWatchCameraMixin` at the tail of `Camera.alignWithEntity`), freezes input (`CameraWatchInputMixin`, `KeyboardInput.tick`), redirects mouse turns (`CameraWatchTurnMixin`, `Entity.turn`). Controls > KhoaTools "Watch cam" key (unbound by default): hold = watch selected camera, release = return. HUD while watching: Day N + HH:MM (from `Level.getOverworldClockTime`) and a blinking red dot. Markers (box + arrow + name) drawn with Gizmos. Shared `util/KeyCategory` now registers the Controls category once (CustomItemTab uses it too).
- Custom Item Tab: added `list [name]`, `rename <old> <new>`, `icon <name> <item>`; names are now one word (A-Z a-z 0-9 _ -), displayed capitalized (`_`/`-` become spaces); items are sorted like vanilla creative order; tab contents are rebuilt right after every change and whenever the creative screen opens.
- Custom Item Tab: added `list [name]`, `rename <old> <new>`, `icon <name> <item>`; names are now one word (A-Z a-z 0-9 _ -), displayed capitalized (`_`/`-` become spaces); items are sorted like vanilla creative order; tab contents are rebuilt right after every change and whenever the creative screen opens.
- Custom Item Tab: added `list [name]`, `rename <old> <new>`, `icon <name> <item>`; names are now one word (A-Z a-z 0-9 _ -), displayed capitalized (`_`/`-` become spaces); items are sorted like vanilla creative order; tab contents are rebuilt right after every change and whenever the creative screen opens.
- Added Custom Item Tab (`feature/customitemtab`): `/khoatoolsclient customitemtab create|edit|save|delete <name>`. Tabs are real `CreativeModeTab`s added to the (unfrozen for one call) creative tab registry at runtime, positioned on the next creative page with the same page/row/column rule as Fabric API's creative-tab pagination (`FabricCreativeModeTabImpl.fabric_setPage`, reflection). Items are stored as JSON (`ItemStack.CODEC` + `RegistryOps`) in `config/khoatools-customtabs.json`. Edit mode: hold the `Add item to customtab` key (Controls > KhoaTools, default Left Alt) and click an item in the creative inventory; ends on `save`, on closing the creative inventory, or on disconnect. A white reminder is drawn above the creative GUI while editing. New mixins: `CreativeModeInventoryScreenMixin`, `CreativeModeTabMixin`, accessors `CreativeModeTabAccessor`, `CreativeModeInventoryScreenAccessor`, `KeyMappingKeyAccessor`; lang file `assets/khoatools/lang/en_us.json`.
- Added client `pulse` item editor. It opens from the feature card or `/khoatoolsclient pulse`, edits the main hand item through Minecraft 26.1.2's `ItemParser`, and applies item component syntax with the chosen count.
- Added `/khoatoolsclient give <item[components]> [count]`. It uses vanilla `ItemArgument` parsing and suggestions, is creative-only, and inserts the created item stack into the client player's inventory.

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
- `GRADLE_USER_HOME=C:\Users\Khoa-Code-Play\.gradle .\gradlew.bat build --offline` — **BUILD SUCCESSFUL** (2026-10-22), including the new Pulse and client give features.
- Generated artifact: `build/libs/khoatools-1.2.0.jar`.
- Generated client mixin configuration includes both `ChatScreenMixin` and `LightmapRenderStateExtractorMixin`.
- In-game verification after this build has not been recorded. Existing startup logs referenced a separate checkout under `Downloads`; they do not verify this artifact.

## Knowledge cache

- No version-specific Minecraft or Fabric API findings have been added to a shared knowledge index.
- Fullbright investigation used the resolved Minecraft 26.1.2 deobfuscated client artifact from the local Loom cache. No decompiled source or cache entry was created.

## Custom Item Tab verification
- `gradlew build --offline` BUILD SUCCESSFUL.
- Dev client started far enough to run the mod initializer (no display in the background session, so the game crashed at GLFW init afterwards): the screen class loaded with all new mixins applied, tab create / page=1 row=TOP column=0 / delete / recreate / reload from `config/khoatools-customtabs.json` worked, the key defaults to LEFT ALT.
- NOT verified in game: Alt+click adding items, the reminder text, the page arrow showing the tab, item JSON round trip with a real level, Controls screen entry.

## Edit message verification
- `GRADLE_USER_HOME=C:\Users\Khoa-Code-Play\.gradle .\gradlew.bat build --offline` BUILD SUCCESSFUL (2026-10-29). Verified with javap on the 26.1.2 merged-deobf jar: `ChatScreen.input`, `handleChatInput(String, boolean)` (called by keyPressed right before `setScreen`), `removed()`, `GuiMessage` constructor, `InputWithModifiers.hasAltDown`. NOT verified in game.

## Camera verification
- `gradlew.bat build --offline` BUILD SUCCESSFUL (2026-10-29). NOT verified in game: mixin application at runtime, watch/freeze/hotkey behavior, HUD, markers.
