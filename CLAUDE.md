# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build

Java 21 is required. Use the explicit `JAVA_HOME` when the system default is older:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew clean jar
```

Output: `build/libs/LogoutChunkLoader-1.0.0.jar`

There are no tests. The only build task of interest is `jar`.

## Paper API dependency

The project compiles against `io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT` from `https://repo.papermc.io/repository/maven-public/`. Paper's artifact history skips 1.21.2 (jumps 1.21.1 → 1.21.3).

Paper's `26.x` versioning scheme (e.g. `26.1.2.build.66-stable`) is a **new API artifact naming convention** introduced in 2025 — it does not represent a different Minecraft version. The underlying server is still Minecraft 1.21.x. The `26.x` artifacts require Java 25 to compile against, but the plugin is fully compatible at runtime with Paper 26.x servers because Java 25 runs Java 21 bytecode and all APIs used are unchanged.

The API version declared in `plugin.yml` is `'1.21'` (Bukkit-style). Paper 26.x servers may log a minor warning about this, but load the plugin without issue.

## Architecture

The plugin has one non-obvious invariant: **`onDisable()` must not touch `data.yml`**. When the server shuts down, `ChunkManager.unloadAllChunks()` releases all force-loaded chunks from JVM memory but intentionally leaves `data.yml` intact so `loadData()` can restore every active region on the next startup.

### State owned by `ChunkManager`

Three parallel maps are always kept in sync, keyed by player UUID:

| Map | Contents |
|---|---|
| `activeChunks` | `Set<Chunk>` currently force-loaded |
| `unloadTasks` | Scheduled `BukkitTask` (absent when delay is `-1`) |
| `regionMeta` | `RegionMeta` (world, centerX/Z, radius, absolute `expiryMs`) |

`regionMeta` is the persistence source of truth. `saveData()` serialises it to `data.yml`; `loadData()` deserialises it on startup.

### Expiry is stored as an absolute epoch timestamp

`expiryMs` is `System.currentTimeMillis() + delaySeconds * 1000` at logout time. On restart, `loadData()` computes `remainingMs = expiryMs - now` and schedules the `BukkitTask` with that remaining duration — the clock is never reset. A value of `-1` means permanent (no task is scheduled).

### Flow summary

- **Logout** → `PlayerQuitListener` → `ChunkManager.loadChunksForPlayer()` → `forceLoadRegion()` + `saveData()`
- **Timer expires** → `ChunkUnloadTask.run()` → `ChunkManager.unloadChunksForPlayer()` + `saveData()`
- **Login** → `PlayerJoinListener` → `ChunkManager.cancelAndUnloadForPlayer()` + `saveData()`
- **Server start** → `ChunkManager` constructor → `loadData()` → `forceLoadRegion()` for each non-expired entry
- **Server stop** → `LogoutChunkLoader.onDisable()` → `unloadAllChunks()` (no `saveData()`)
