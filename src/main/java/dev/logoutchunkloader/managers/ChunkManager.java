package dev.logoutchunkloader.managers;

import dev.logoutchunkloader.LogoutChunkLoader;
import dev.logoutchunkloader.tasks.ChunkUnloadTask;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class ChunkManager {

    private final LogoutChunkLoader plugin;

    private final Map<UUID, Set<Chunk>> activeChunks = new HashMap<>();
    private final Map<UUID, BukkitTask> unloadTasks = new HashMap<>();

    public ChunkManager(LogoutChunkLoader plugin) {
        this.plugin = plugin;
    }

    public void loadChunksForPlayer(Player player, Location location) {
        UUID playerId = player.getUniqueId();

        boolean requirePerm = plugin.getConfig().getBoolean("require-permission", false);
        if (requirePerm && !player.hasPermission("logoutchunkloader.use")) {
            return;
        }

        int maxRegions = plugin.getConfig().getInt("max-regions-per-player", 1);
        if (maxRegions > 0 && activeChunks.containsKey(playerId)) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Player " + player.getName()
                        + " already has max regions loaded. Skipping.");
            }
            return;
        }

        World world = location.getWorld();
        if (world == null) return;

        int radius = plugin.getConfig().getInt("chunk-radius", 2);
        int centerX = location.getBlockX() >> 4;
        int centerZ = location.getBlockZ() >> 4;

        Set<Chunk> loadedChunks = new HashSet<>();

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                world.setChunkForceLoaded(x, z, true);
                loadedChunks.add(world.getChunkAt(x, z));
            }
        }

        activeChunks.put(playerId, loadedChunks);

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Force-loaded " + loadedChunks.size()
                    + " chunks for player " + player.getName()
                    + " at " + world.getName() + " [" + centerX + ", " + centerZ + "]");
        }

        int delaySeconds = plugin.getConfig().getInt("unload-delay-seconds", 600);
        if (delaySeconds >= 0) {
            long delayTicks = (long) delaySeconds * 20L;
            BukkitTask task = new ChunkUnloadTask(plugin, this, playerId, loadedChunks)
                    .runTaskLater(plugin, delayTicks);
            unloadTasks.put(playerId, task);
        }
    }

    public void cancelAndUnloadForPlayer(UUID playerId, String playerName) {
        BukkitTask task = unloadTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }

        Set<Chunk> chunks = activeChunks.remove(playerId);
        if (chunks != null && !chunks.isEmpty()) {
            unloadChunkSet(chunks);
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Unloaded " + chunks.size()
                        + " chunks for returning player " + playerName);
            }
        }
    }

    public void unloadChunksForPlayer(UUID playerId) {
        unloadTasks.remove(playerId);
        Set<Chunk> chunks = activeChunks.remove(playerId);
        if (chunks != null) {
            unloadChunkSet(chunks);
        }
    }

    public void unloadAllChunks() {
        for (BukkitTask task : unloadTasks.values()) {
            task.cancel();
        }
        unloadTasks.clear();

        for (Set<Chunk> chunks : activeChunks.values()) {
            unloadChunkSet(chunks);
        }
        activeChunks.clear();
    }

    private void unloadChunkSet(Set<Chunk> chunks) {
        for (Chunk chunk : chunks) {
            if (chunk.getWorld() != null) {
                chunk.getWorld().setChunkForceLoaded(chunk.getX(), chunk.getZ(), false);
            }
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
