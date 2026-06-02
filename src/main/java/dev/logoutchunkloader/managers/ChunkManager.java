package dev.logoutchunkloader.managers;

import dev.logoutchunkloader.LogoutChunkLoader;
import dev.logoutchunkloader.tasks.ChunkUnloadTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ChunkManager {

    private final LogoutChunkLoader plugin;
    private final Map<UUID, Set<Chunk>> activeChunks = new HashMap<>();
    private final Map<UUID, BukkitTask> unloadTasks = new HashMap<>();
    private final Map<UUID, RegionMeta> regionMeta = new HashMap<>();
    private final Map<UUID, Long> loginTimes = new HashMap<>();
    private final Map<UUID, BukkitTask> readyTasks = new HashMap<>();
    private final File dataFile;

    private static class RegionMeta {
        final String worldName;
        final int centerX;
        final int centerZ;
        final int radius;
        final long expiryMs; // absolute epoch ms; -1 = never

        RegionMeta(String worldName, int centerX, int centerZ, int radius, long expiryMs) {
            this.worldName = worldName;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.radius = radius;
            this.expiryMs = expiryMs;
        }
    }

    public ChunkManager(LogoutChunkLoader plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        loadData();
    }

    public boolean loadChunksForPlayer(Player player, Location location) {
        UUID playerId = player.getUniqueId();

        boolean requirePerm = plugin.getConfig().getBoolean("require-permission", false);
        if (requirePerm && !player.hasPermission("logoutchunkloader.use")) {
            return false;
        }

        int maxRegions = plugin.getConfig().getInt("max-regions-per-player", 1);
        if (maxRegions > 0 && activeChunks.containsKey(playerId)) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Player " + player.getName()
                        + " already has max regions loaded. Skipping.");
            }
            return false;
        }

        int minOnlineSeconds = plugin.getConfig().getInt("min-online-seconds", 900);
        if (minOnlineSeconds > 0) {
            Long loginTime = loginTimes.get(playerId);
            long elapsedSeconds = loginTime == null ? 0L
                    : (System.currentTimeMillis() - loginTime) / 1000L;
            if (elapsedSeconds < minOnlineSeconds) {
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Player " + player.getName()
                            + " was online for only " + elapsedSeconds + "s (min: "
                            + minOnlineSeconds + "s). Skipping chunk loading.");
                }
                return false;
            }
        }

        World world = location.getWorld();
        if (world == null) return false;

        int radius = plugin.getConfig().getInt("chunk-radius", 2);
        int centerX = location.getBlockX() >> 4;
        int centerZ = location.getBlockZ() >> 4;
        int delaySeconds = plugin.getConfig().getInt("unload-delay-seconds", 10800);
        long expiryMs = delaySeconds < 0 ? -1L : System.currentTimeMillis() + (long) delaySeconds * 1000L;

        forceLoadRegion(playerId, world, centerX, centerZ, radius, expiryMs, delaySeconds);

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Force-loaded " + activeChunks.get(playerId).size()
                    + " chunks for player " + player.getName()
                    + " at " + world.getName() + " [" + centerX + ", " + centerZ + "]");
        }

        saveData();
        return true;
    }

    private void forceLoadRegion(UUID playerId, World world, int centerX, int centerZ,
                                  int radius, long expiryMs, int delaySeconds) {
        Set<Chunk> loadedChunks = new HashSet<>();
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                world.setChunkForceLoaded(x, z, true);
                loadedChunks.add(world.getChunkAt(x, z));
            }
        }
        activeChunks.put(playerId, loadedChunks);
        regionMeta.put(playerId, new RegionMeta(world.getName(), centerX, centerZ, radius, expiryMs));

        if (delaySeconds >= 0) {
            long delayTicks = (long) delaySeconds * 20L;
            BukkitTask task = new ChunkUnloadTask(plugin, this, playerId, loadedChunks)
                    .runTaskLater(plugin, delayTicks);
            unloadTasks.put(playerId, task);
        }
    }

    public void recordLogin(Player player) {
        UUID playerId = player.getUniqueId();
        loginTimes.put(playerId, System.currentTimeMillis());

        BukkitTask existing = readyTasks.remove(playerId);
        if (existing != null) existing.cancel();

        int minOnlineSeconds = plugin.getConfig().getInt("min-online-seconds", 900);
        if (minOnlineSeconds <= 0) return;

        String readyMessage = plugin.getConfig().getString("ready-message", "");
        if (readyMessage == null || readyMessage.isEmpty()) return;

        long delayTicks = (long) minOnlineSeconds * 20L;
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            readyTasks.remove(playerId);
            Player p = Bukkit.getPlayer(playerId);
            if (p != null && p.isOnline()) {
                Component component = LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(readyMessage);
                p.sendMessage(component);
            }
        }, delayTicks);
        readyTasks.put(playerId, task);
    }

    public void cleanupLoginTracking(UUID playerId) {
        loginTimes.remove(playerId);
        BukkitTask task = readyTasks.remove(playerId);
        if (task != null) task.cancel();
    }

    public void cancelAndUnloadForPlayer(UUID playerId, String playerName) {
        BukkitTask task = unloadTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }

        Set<Chunk> chunks = activeChunks.remove(playerId);
        regionMeta.remove(playerId);

        if (chunks != null && !chunks.isEmpty()) {
            unloadChunkSet(chunks);
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Unloaded " + chunks.size()
                        + " chunks for returning player " + playerName);
            }
        }

        saveData();
    }

    public void unloadChunksForPlayer(UUID playerId) {
        unloadTasks.remove(playerId);
        Set<Chunk> chunks = activeChunks.remove(playerId);
        regionMeta.remove(playerId);
        if (chunks != null) {
            unloadChunkSet(chunks);
        }
        saveData();
    }

    /**
     * Called on plugin disable. Releases all force-loaded chunks from memory
     * but intentionally does NOT modify data.yml so regions are restored on
     * the next server start.
     */
    public void unloadAllChunks() {
        for (BukkitTask task : unloadTasks.values()) {
            task.cancel();
        }
        unloadTasks.clear();

        for (BukkitTask task : readyTasks.values()) {
            task.cancel();
        }
        readyTasks.clear();
        loginTimes.clear();

        for (Set<Chunk> chunks : activeChunks.values()) {
            unloadChunkSet(chunks);
        }
        activeChunks.clear();
        // regionMeta is kept intact — data.yml already reflects current state
    }

    private void unloadChunkSet(Set<Chunk> chunks) {
        for (Chunk chunk : chunks) {
            if (chunk.getWorld() != null) {
                chunk.getWorld().setChunkForceLoaded(chunk.getX(), chunk.getZ(), false);
            }
        }
    }

    private void loadData() {
        if (!dataFile.exists()) return;

        FileConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        if (!data.contains("active-regions")) return;

        long now = System.currentTimeMillis();
        int loaded = 0;
        int expired = 0;

        for (String uuidStr : data.getConfigurationSection("active-regions").getKeys(false)) {
            String path = "active-regions." + uuidStr;

            UUID playerId;
            try {
                playerId = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in data.yml, skipping: " + uuidStr);
                continue;
            }

            long expiryMs = data.getLong(path + ".expiry", -1);
            if (expiryMs != -1 && expiryMs <= now) {
                expired++;
                continue;
            }

            String worldName = data.getString(path + ".world", "");
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("World '" + worldName + "' not found for "
                        + uuidStr + " in data.yml — skipping.");
                continue;
            }

            int centerX = data.getInt(path + ".center-x");
            int centerZ = data.getInt(path + ".center-z");
            int radius = data.getInt(path + ".radius", plugin.getConfig().getInt("chunk-radius", 2));

            // Convert absolute expiry back to a remaining-seconds delay for the task scheduler
            int delaySeconds = expiryMs == -1 ? -1 : (int) ((expiryMs - now) / 1000L);

            forceLoadRegion(playerId, world, centerX, centerZ, radius, expiryMs, delaySeconds);
            loaded++;
        }

        if (loaded > 0 || expired > 0) {
            plugin.getLogger().info("Restored " + loaded + " chunk region(s) from data.yml"
                    + (expired > 0 ? " (" + expired + " had expired while the server was offline)" : "") + ".");
        }
    }

    private void saveData() {
        FileConfiguration data = new YamlConfiguration();
        for (Map.Entry<UUID, RegionMeta> entry : regionMeta.entrySet()) {
            String path = "active-regions." + entry.getKey();
            RegionMeta meta = entry.getValue();
            data.set(path + ".world", meta.worldName);
            data.set(path + ".center-x", meta.centerX);
            data.set(path + ".center-z", meta.centerZ);
            data.set(path + ".radius", meta.radius);
            data.set(path + ".expiry", meta.expiryMs);
        }
        try {
            plugin.getDataFolder().mkdirs();
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save data.yml: " + e.getMessage());
        }
    }

    public Map<UUID, Integer> getActiveRegionSummary() {
        Map<UUID, Integer> summary = new LinkedHashMap<>();
        for (Map.Entry<UUID, Set<Chunk>> entry : activeChunks.entrySet()) {
            summary.put(entry.getKey(), entry.getValue().size());
        }
        return summary;
    }

    public boolean hasActiveChunks(UUID playerId) {
        return activeChunks.containsKey(playerId);
    }

    public int getTotalForceLoadedChunks() {
        return activeChunks.values().stream().mapToInt(Set::size).sum();
    }
}
