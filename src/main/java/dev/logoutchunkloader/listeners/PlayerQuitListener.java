package dev.logoutchunkloader.listeners;

import dev.logoutchunkloader.LogoutChunkLoader;
import dev.logoutchunkloader.managers.ChunkManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final LogoutChunkLoader plugin;
    private final ChunkManager chunkManager;

    public PlayerQuitListener(LogoutChunkLoader plugin, ChunkManager chunkManager) {
        this.plugin = plugin;
        this.chunkManager = chunkManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Location logoutLocation = player.getLocation();

        chunkManager.loadChunksForPlayer(player, logoutLocation);

        String message = plugin.getConfig().getString("logout-message", "");
        if (message != null && !message.isEmpty()) {
            int delay = plugin.getConfig().getInt("unload-delay-seconds", 600);
            message = message.replace("{seconds}", String.valueOf(delay));
            Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(message);
            player.sendMessage(component);
        }
    }
}
