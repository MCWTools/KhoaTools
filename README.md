# KhoaTools

Expandable client-side toolbox for Minecraft 26.1.2 (Fabric). Modid: `khoatools`.

## GUI

`/khoagui` (or `/khoatoolsclient` with nothing after it) opens the config GUI. `/khoatools` is server-side, op-only, and has no GUI.
- Two tabs: **ClientsideTools** and **ServersideTools**, and a search bar.
- Each feature is a card (2 per row). **Click the card** = turn the feature on/off (green = on; one-shot features like Server IP / Player In Direction just run once). **Click Config** = description + settings (tick box, number box that only takes digits, text box). Settings are saved immediately.
- The background is transparent and the game keeps running. **Esc** closes the GUI (no pause menu); `< Back` returns to the list.
- Commands still work: `/khoatoolsclient <feature> ...`.

## Commands

`/khoatoolsclient ...` = client-side commands. `/khoatools ...` = server-side commands (op only): `/khoatools optimizetnt [raymultiplier 0.05-1.00]` toggles / sets how many explosion rays primed TNT uses.

### `/khoatoolsclient downloadchunk [allchunkinrenderdistance]` (toggle)
Saves chunks as vanilla anvil data to `.minecraft/khoatools/downloadedchunk/<server or world>/region/r.X.Z.mca` (+ `entities/`, `DIM-1/`, `DIM1/`, ...).

- `downloadchunk` (default `false`): every chunk you walk into is saved once. Action bar shows `Downloading chunk (x z) ~size`.
- `downloadchunk true`: every loaded chunk inside render distance is saved automatically, nearest first (a few per tick, no lag). Entities are not scanned in this mode. Running it again with the other value switches mode without turning it off.
- Chunks that already exist on disk are skipped. All blocks are scanned; only data the client receives is saved (sign text, spawner data, ...). Unknown blocks / block entities / mobs are not saved. Light is not saved.
- While on, every saved chunk within 16 chunks is tinted green (2D layer at y=100) so you do not have to walk over it again.

### `/khoatoolsclient loaddownloadedchunk <server/world>`
Singleplayer only. Pastes the saved chunks of the current dimension into the current world, one chunk at a time, written straight into the loaded chunk (only changed blocks, no neighbour updates / drops / on-place logic, biomes and block entities included, entities not).

- Suggestions come from the folder names in `.minecraft/khoatools/downloadedchunk/`.
- Order: the chunk nearest to where you ran the command, then always the nearest to the last pasted chunk.
- You are teleported only when the next chunk is farther than (render distance - 1) chunks from where you stand; closer chunks are pasted in place. You are NOT teleported back at the end. You are temporarily invulnerable while it runs.
- Every pasted chunk is printed in chat (`Loaded chunk x z (blockX, blockZ) [i/total]`).
- `loaddownloadedchunk far_tp_delay [ticks]` (default 20): wait before the next chunk if it is outside render distance. Saved in config.
- `loaddownloadedchunk near_tp_delay [ticks]` (default 10): wait before the next chunk if it is close. Saved in config. Without a number they print the current value.
- `/khoatoolsclient loaddownloadedchunk stop` cancels.

### `/khoatoolsclient getip`
On every join (including after a server transfer) the real `ip:port` you are connected to is printed in chat. `getip` prints it again. Behind a proxy (Velocity/Bungee) you only see the proxy's address.

### `/khoatoolsclient betterchat`
- `/khoatoolsclient betterchat length <n>`: messages kept before the oldest is deleted. `0` = vanilla default (100).
- `/khoatoolsclient betterchat cache <true|false>`: save server chat per server to `.minecraft/khoatools/betterchat/<server>.json`
  and show it again on join, followed by a highlighted separator. Colors/bold/etc. are kept.
  Only server messages are saved (not client messages). The cache keeps up to `length` messages.
- `/khoatoolsclient betterchat`: show current settings.

### Merged mods (now sub-commands of `/khoatoolsclient`)
- `locatorfinder [player]` (no arg = off), `locatortrace [repeatdelay]`, `playerindirection` (ex locator-finder)
- `optimizefps [animation|particle <0-3>] [entity <-1..99999>]` (toggle; includes ex entitylimiter)
- `tnthitray` (toggle): real explosion rays of primed TNT (entity hit rays if an entity is in range, else block rays)
- `logoutghost [status|clear|fade <ticks>|opacity <percent>]` (ex logout-ghost, settings in `config/logout-ghost.json`)
- `sky` opens the SkyChanger GUI, PNGs in `.minecraft/skyboxes` (ex skychanger, settings in `config/skychanger.json`)

Settings live in `config/khoatools.json`.

## Moving another of your mods in (short way)
1. Copy its source packages into `src/client/java` (keep the package names).
2. Delete its `ModInitializer`/`ClientModInitializer`/datagen and `fabric.mod.json`; replace `MyMod.LOGGER` with `KhoaTools.LOGGER`, `MyMod.id(..)` with `KhoaTools.id(..)`.
3. Copy its `*.mixins.json` to `src/client/resources/` (rename `khoatools.<name>.mixins.json`) and add it to `mixins` in `fabric.mod.json`; move `assets/<oldid>/` to `assets/khoatools/`.
4. Wrap its command in a `KhoaFeature` (subcommand of the root) and add it to `KhoaFeatures`.

## Adding a feature (it shows up in the GUI by itself)
Client tool: implement `khoa.tools.client.feature.KhoaFeature` and add it to `KhoaFeatures`. Only `id()` and `registerCommands()` are required; optional:
`displayName()` (card title), `description()`, `settings()` (list of `Setting.bool/integer/string(key, label, description, getter, setter)`),
`activate(mc)` (card click) and `isActive()` (green while true).
Server tool: implement `khoa.tools.command.KhoaServerFeature` and add it to `KhoaServerFeatures` (clicking its card runs `/khoatools <id>`).
