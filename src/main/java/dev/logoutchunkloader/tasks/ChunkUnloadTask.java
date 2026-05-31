package dev.logoutchunkloader.tasks;

import dev.logoutchunkloader.LogoutChunkLoader;
import dev.logoutchunkloader.managers.ChunkManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Set;
import java.util.UUID;

public class ChunkUnloadTask extends BukkitRunnable {

    private final LogoutChunkLoader plugin;
    private final ChunkManager chunkManager;
    private final UUID playerId;
    private final Set<Chunk> chunks;

    public ChunkUnloadTask(LogoutChunkLoader plugin, ChunkManager chunkManager,
                           UUID playerId, Set<Chunk> chunks) {
        this.plugin = plugin;
        this.chunkManager = chunkManager;
        this.playerId = playerId;
        this.chunks = chunks;
    }

    @Override
    public void run() {
        if (Bukkit.getPlayer(playerId) != null) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("ChunkUnloadTask: Player " + playerId
                        + " is online again. Skipping unload.");
            }
            return;
        }

        chunkManager.unloadChunksForPlayer(playerId);

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("ChunkUnloadTask: Unloaded " + chunks.size()
                    + " chunks for offline player " + playerId);
        }
    }
}
