# LogoutChunkLoader

A [Paper](https://papermc.io/) plugin for Minecraft 1.21.x that keeps a configurable area of chunks force-loaded around a player's logout position — so furnaces keep smelting, hoppers keep moving items, and Redstone contraptions keep ticking long after the player has gone offline.

---

## Why this exists

Vanilla Minecraft unloads chunks the moment no player is nearby. Log out next to your iron farm and every process inside it freezes until you come back. LogoutChunkLoader fixes this by registering a force-loaded region at the exact chunk coordinate where you disconnected, scheduling its automatic release after a configurable delay, and — critically — **persisting that region across server restarts** so a reboot doesn't silently undo your offline progress.

---

## Features

- **Automatic force-loading on logout** — no commands required from the player.
- **Configurable chunk radius** — from a single chunk up to a 9×9 area.
- **Configurable unload delay** — set a fixed duration, or `-1` to keep chunks loaded indefinitely.
- **Restart-safe persistence** — active regions are saved to `data.yml` and restored on the next server start with their original expiry time intact (the clock is never reset by a reboot).
- **Instant release on login** — when the player reconnects the region is released immediately; their presence takes over naturally.
- **Minimum online time gate** — only players who have been online for a configurable number of seconds activate the ChunkLoader at logout, keeping the number of force-loaded regions low by excluding short-lived visitors and bots.
- **Per-player region limit** — caps the number of simultaneous regions per player to prevent abuse on public servers.
- **Optional permission gate** — restrict the feature to players with a specific permission node.
- **Color-coded player messages** — configurable feedback at login, on reaching the online-time threshold, and at logout; all using standard `&` color codes.
- **Admin command** — `/lcl reload | list | status` for live inspection and hot config reloads.

---

## Requirements

| Requirement | Version |
|---|---|
| Minecraft (Java Edition) | 1.21.x |
| Server software | [Paper](https://papermc.io/downloads/paper) 1.21.x or 26.x (or any Paper fork) |
| Java | 21 or newer |

Spigot and CraftBukkit are **not** supported — the plugin uses Paper-specific APIs.

> **Paper 26.x note:** Starting in 2025, Paper introduced a new API versioning scheme (`26.x`) that replaces the old `1.21.x-R0.1-SNAPSHOT` artifact names. Despite the different version number, Paper 26.x servers are still running Minecraft 1.21.x underneath. The plugin is fully compatible — Java 21 bytecode runs on Paper 26.x's required Java 25 runtime, and all APIs used are unchanged. A `plugin.yml` `api-version` warning may appear in the console, but it does not affect functionality.

---

## Installation

1. Download the latest `LogoutChunkLoader-x.x.x.jar` from the [Releases](../../releases) page.
2. Drop it into your server's `plugins/` folder.
3. Restart the server (or use a plugin manager that supports hot-loading).
4. Edit `plugins/LogoutChunkLoader/config.yml` to your liking.
5. Run `/lcl reload` to apply changes without restarting.

The default configuration is conservative and safe to use immediately on any server.

---

## Building from source

### Prerequisites

- **Java 21 JDK** — [Eclipse Temurin](https://adoptium.net/) is recommended.
- No other tools required; the Gradle wrapper (`gradlew`) downloads everything else automatically.

### Steps

```bash
# Clone the repository
git clone https://github.com/your-username/LogoutChunkLoader.git
cd LogoutChunkLoader

# Build the JAR (Linux / macOS)
./gradlew jar

# Build the JAR (Windows)
gradlew.bat jar
```

The compiled plugin will be placed at:

```
build/libs/LogoutChunkLoader-1.1.0.jar
```

> **Note for Linux/macOS:** If `./gradlew` is not executable, run `chmod +x gradlew` first.

> **Note if your system Java is older than 21:** Set `JAVA_HOME` explicitly:
> ```bash
> JAVA_HOME=/path/to/jdk-21 ./gradlew jar
> ```

---

## Configuration

The full `config.yml` with all options and their defaults:

```yaml
# Chunk radius that stays loaded around the logout position.
# Radius 0 = only the player's chunk (1x1)
# Radius 1 = 3x3 chunks around the logout position
# Radius 2 = 5x5 chunks (default, suitable for most farms)
# Radius 3 = 7x7 chunks
# WARNING: Large radii significantly increase server load!
chunk-radius: 2

# How many seconds should chunks stay loaded after logout?
# -1 = Keep chunks loaded permanently (persists across server restarts).
# Default: 10800 seconds (3 hours)
unload-delay-seconds: 10800

# Maximum number of simultaneously force-loaded regions per player.
# Prevents abuse on public servers.
# -1 = unlimited
max-regions-per-player: 1

# Minimum number of seconds a player must be online in a single session before
# the ChunkLoader will activate for them at logout.
# This prevents short-lived visitors and bots from inflating the number of
# force-loaded chunk regions on the server.
# 0 = disabled (activate for every logout, no minimum required)
# Default: 900 seconds (15 minutes)
min-online-seconds: 900

# If true, only players with the 'logoutchunkloader.use' permission benefit.
# Default: false (all players)
require-permission: false

# Enable debug output in the server console.
debug: false

# Message sent to the player on logout (leave empty to disable).
# Supports & color codes.
logout-message: "&7[ChunkLoader] &aYour chunks will stay loaded for &e{seconds} seconds&a."

# Message sent to the player on login if their chunks are still loaded.
login-message: "&7[ChunkLoader] &aYour chunks have been unloaded."

# Message sent to the player on login when a minimum online time is required.
# {seconds} is replaced by the configured min-online-seconds value.
# Leave empty to disable.
min-online-message: "&7[ChunkLoader] &7Chunk keeping is not yet active. &eStay online for &b{seconds} more seconds &eand it will be enabled automatically."

# Message sent to the player once they have been online long enough for the
# ChunkLoader to become active for their session.
# Leave empty to disable.
ready-message: "&7[ChunkLoader] &aChunk keeping is now active &7— your chunks will be kept loaded when you log off."
```

### Chunk radius performance guide

| Radius | Chunks loaded | Suitable for |
|:---:|:---:|---|
| 0 | 1 (1×1) | Logout position only |
| 1 | 9 (3×3) | Small contraptions |
| **2** | **25 (5×5)** | **Most farms — recommended default** |
| 3 | 49 (7×7) | Larger multi-chunk builds |
| 4 | 81 (9×9) | High-performance servers only |

Each force-loaded chunk runs full tick calculations. Monitor server TPS with `/tps` when using larger radii with many offline players.

---

## Minimum online time

The `min-online-seconds` setting (default: 900 — 15 minutes) ensures that the ChunkLoader only activates for players who have been connected long enough to actually be doing meaningful work. Short-lived visitors and automated reconnect bots never trigger chunk loading, which keeps the total number of force-loaded regions on the server as low as possible.

### How it works

1. **On login** — the player receives a short notice that chunk keeping is not yet active, along with how many seconds remain until it becomes available.
2. **After `min-online-seconds`** — a scheduled task fires and sends the player a confirmation that chunk keeping is now active for their session.
3. **On logout** — if the player has been online for at least `min-online-seconds` seconds in the current session, the ChunkLoader activates normally. If not, no region is registered and no logout message is sent.

The timer is purely in-memory and is reset on every login. It is never persisted and is not affected by server restarts.

### Disabling the feature

Set `min-online-seconds: 0` to disable the minimum online time requirement entirely. In that case, the `min-online-message` and `ready-message` are also never sent.

### Customising the messages

Both messages support `&` color codes. The `{seconds}` placeholder in `min-online-message` is replaced with the configured `min-online-seconds` value. Set either message to an empty string to suppress it.

---

## Commands & permissions

### Commands

| Command | Description |
|---|---|
| `/lcl reload` | Reloads `config.yml` without restarting the server. Running timers keep their original delay. |
| `/lcl list` | Lists all players with currently active force-loaded regions and their chunk counts. |
| `/lcl status` | Shows total active regions, total force-loaded chunks, and the current radius and delay settings. |

`/lcl` has the alias `/logoutchunkloader`.

### Permissions

| Permission node | Default | Description |
|---|---|---|
| `logoutchunkloader.admin` | OP | Access to all `/lcl` subcommands. |
| `logoutchunkloader.use` | Everyone | Player's chunks are force-loaded on logout. Only checked when `require-permission: true`. |

---

## How it works

When a player disconnects, `PlayerQuitListener` captures their exact location and passes it to `ChunkManager`, which calls `World.setChunkForceLoaded(x, z, true)` for every chunk in the configured radius. The region's metadata — world, center chunk coordinates, radius, and an **absolute expiry timestamp** — is immediately written to `plugins/LogoutChunkLoader/data.yml`.

A `BukkitTask` is scheduled to release the region after the configured delay. If the player reconnects before the timer fires, `PlayerJoinListener` cancels the task and unloads the region instantly.

On server shutdown, `onDisable()` calls `World.setChunkForceLoaded(x, z, false)` for every active chunk to ensure a clean JVM exit — but `data.yml` is left untouched. On the next startup, `ChunkManager` reads `data.yml`, discards any entries whose expiry has already passed, and restores the rest with a new timer set to the **remaining** duration. The expiry clock is never reset by a reboot.

---

## Known limitations

- **Random-tick farms** (wheat, sugar cane, pumpkins, melons) require a player within 128 blocks for random ticks to fire. Force-loading chunks alone does not satisfy this Vanilla requirement. Contraptions that **do** benefit: furnaces, hoppers, observers, pistons, Redstone clocks, and mob spawners (within their spawn range).
- **No cross-restart timer precision below one second** — the remaining delay is stored as whole seconds; sub-second precision is lost on restart.
- **One region per player by default** — the `max-regions-per-player: 1` default means a second logout overwrites nothing; the existing region stays until its timer expires. Set to `-1` for unlimited regions if your use case requires it.

---

## License

This project is released under the [MIT License](LICENSE).
