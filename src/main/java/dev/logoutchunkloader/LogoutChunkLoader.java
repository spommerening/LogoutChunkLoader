package dev.logoutchunkloader;

import dev.logoutchunkloader.commands.LCLCommand;
import dev.logoutchunkloader.listeners.PlayerJoinListener;
import dev.logoutchunkloader.listeners.PlayerQuitListener;
import dev.logoutchunkloader.managers.ChunkManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class LogoutChunkLoader extends JavaPlugin {

    private ChunkManager chunkManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        chunkManager = new ChunkManager(this);

        getServer().getPluginManager().registerEvents(
                new PlayerQuitListener(this, chunkManager), this);
        getServer().getPluginManager().registerEvents(
                new PlayerJoinListener(this, chunkManager), this);

        PluginCommand lclCommand = getCommand("lcl");
        if (lclCommand != null) {
            LCLCommand executor = new LCLCommand(this, chunkManager);
            lclCommand.setExecutor(executor);
            lclCommand.setTabCompleter(executor);
        }

        getLogger().info("LogoutChunkLoader v" + getDescription().getVersion() + " enabled.");
        getLogger().info("Chunk radius: " + getConfig().getInt("chunk-radius", 2));
        getLogger().info("Unload delay: " + getConfig().getInt("unload-delay-seconds", 600) + "s");
    }

    @Override
    public void onDisable() {
        if (chunkManager != null) {
            chunkManager.unloadAllChunks();
        }
        getLogger().info("LogoutChunkLoader disabled. All force-loaded chunks have been released.");
    }

    public ChunkManager getChunkManager() {
        return chunkManager;
    }
}
