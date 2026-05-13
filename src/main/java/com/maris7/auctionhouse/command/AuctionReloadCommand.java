package com.maris7.auctionhouse.command;

import com.maris7.auctionhouse.MarisAuctionPlugin;
import com.maris7.auctionhouse.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class AuctionReloadCommand implements CommandExecutor {

    private final MarisAuctionPlugin plugin;

    public AuctionReloadCommand(MarisAuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        plugin.reloadPlugin();
        Text.send(sender, plugin.getConfigRegistry().messages().get("plugin.reloaded"));
        return true;
    }
}
