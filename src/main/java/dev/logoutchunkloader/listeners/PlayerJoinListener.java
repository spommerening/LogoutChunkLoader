package dev.logoutchunkloader.listeners;

import dev.logoutchunkloader.LogoutChunkLoader;
import dev.logoutchunkloader.managers.ChunkManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final LogoutChunkLoader plugin;
    private final ChunkManager chunkManager;

    public PlayerJoinListener(LogoutChunkLoader plugin, ChunkManager chunkManager) {
        this.plugin = plugin;
        this.chunkManager = chunkManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (chunkManager.hasActiveChunks(player.getUniqueId())) {
            chunkManager.cancelAndUnloadForPlayer(player.getUniqueId(), player.getName());

            String message = plugin.getConfig().getString("login-message", "");
            if (message != null && !message.isEmpty()) {
                Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(message);
                player.sendMessage(component);
            }
        }
    }
}
