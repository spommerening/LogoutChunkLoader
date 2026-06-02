# Changelog

All notable changes to LogoutChunkLoader are documented here.

---

## [1.1.0] — 2026-06-02

### Added

- **Minimum online time gate** (`min-online-seconds`, default: `900`).
  The ChunkLoader now only activates at logout for players who have been online
  for at least the configured number of seconds in the current session.
  This prevents short-lived visitors and automated reconnect bots from
  registering force-loaded regions, keeping the total chunk count on the
  server lower.

- **Login notice** (`min-online-message`).
  When a player joins and a minimum online time is configured, they receive a
  message explaining how many seconds they need to stay online before the
  ChunkLoader becomes active. Configurable; leave empty to disable.

- **Ready notification** (`ready-message`).
  Once a player has been online for the required time, they automatically
  receive a confirmation that chunk keeping is now active for their session.
  Configurable; leave empty to disable.

### Changed

- `/lcl status` now reports the configured `min-online-seconds` value (shown
  as `disabled` when set to `0`).

- `loadChunksForPlayer()` now returns a `boolean` (`true` if a region was
  actually registered). The logout message is only sent when chunks were
  actually loaded.

- `unloadAllChunks()` (called on plugin disable) now also cancels any pending
  ready-notification tasks and clears the login-time tracking map.

### Configuration

Two new keys in `config.yml`:

```yaml
min-online-seconds: 900
min-online-message: "&7[ChunkLoader] &7Chunk keeping is not yet active. &eStay online for &b{seconds} more seconds &eand it will be enabled automatically."
ready-message: "&7[ChunkLoader] &aChunk keeping is now active &7— your chunks will be kept loaded when you log off."
```

Existing installations that do not have these keys will use the above defaults
automatically (via Bukkit's `getConfig().getInt/getString` fallback).

---

## [1.0.0] — initial release

- Force-loads a configurable chunk radius around a player's logout position.
- Configurable unload delay (or permanent with `-1`).
- Restart-safe persistence via `data.yml` with absolute expiry timestamps.
- Per-player region limit and optional permission gate.
- Admin commands: `/lcl reload | list | status`.
