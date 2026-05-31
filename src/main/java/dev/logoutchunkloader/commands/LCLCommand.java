package dev.logoutchunkloader.commands;

import dev.logoutchunkloader.LogoutChunkLoader;
import dev.logoutchunkloader.managers.ChunkManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;

public class LCLCommand implements CommandExecutor, TabCompleter {

    private final LogoutChunkLoader plugin;
    private final ChunkManager chunkManager;

    public LCLCommand(LogoutChunkLoader plugin, ChunkManager chunkManager) {
        this.plugin = plugin;
        this.chunkManager = chunkManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("logoutchunkloader.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(Component.text("[LCL] Configuration reloaded.", NamedTextColor.GREEN));
            }
            case "list" -> {
                Map<UUID, Integer> summary = chunkManager.getActiveRegionSummary();
                if (summary.isEmpty()) {
                    sender.sendMessage(Component.text("[LCL] No active chunk regions.", NamedTextColor.YELLOW));
                } else {
                    sender.sendMessage(Component.text("[LCL] Active chunk regions:", NamedTextColor.GOLD));
                    for (Map.Entry<UUID, Integer> entry : summary.entrySet()) {
                        OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                        String name = op.getName() != null ? op.getName() : entry.getKey().toString();
                        sender.sendMessage(Component.text(
                                "  - " + name + ": " + entry.getValue() + " chunks",
                                NamedTextColor.AQUA));
                    }
                }
            }
            case "status" -> {
                int total = chunkManager.getTotalForceLoadedChunks();
                int regions = chunkManager.getActiveRegionSummary().size();
                sender.sendMessage(Component.text("[LCL] Status:", NamedTextColor.GOLD));
                sender.sendMessage(Component.text("  Active regions: " + regions, NamedTextColor.WHITE));
                sender.sendMessage(Component.text("  Total force-loaded chunks: " + total, NamedTextColor.WHITE));
                sender.sendMessage(Component.text(
                        "  Chunk radius: " + plugin.getConfig().getInt("chunk-radius", 2),
                        NamedTextColor.WHITE));
                sender.sendMessage(Component.text(
                        "  Unload delay: " + plugin.getConfig().getInt("unload-delay-seconds", 600) + "s",
                        NamedTextColor.WHITE));
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("[LCL] LogoutChunkLoader Commands:", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /lcl reload  - Konfiguration neu laden", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /lcl list    - Aktive Chunk-Regionen anzeigen", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /lcl status  - Plugin-Status anzeigen", NamedTextColor.YELLOW));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("reload", "list", "status");
        }
        return Collections.emptyList();
    }
}
