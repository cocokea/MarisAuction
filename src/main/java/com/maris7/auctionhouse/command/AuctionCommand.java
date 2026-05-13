package com.maris7.auctionhouse.command;

import com.maris7.auctionhouse.MarisAuctionPlugin;
import com.maris7.auctionhouse.gui.GuiManager;
import com.maris7.auctionhouse.service.AuctionService;
import com.maris7.auctionhouse.util.ItemUtil;
import com.maris7.auctionhouse.util.MoneyUtil;
import com.maris7.auctionhouse.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class AuctionCommand implements CommandExecutor, TabCompleter {
    private final MarisAuctionPlugin plugin;
    private final AuctionService auctionService;
    private final GuiManager guiManager;

    public AuctionCommand(MarisAuctionPlugin plugin, AuctionService auctionService, GuiManager guiManager) {
        this.plugin = plugin;
        this.auctionService = auctionService;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (!plugin.getDatabaseManager().isAuctionAvailable()) {
            return true;
        }
        if (!plugin.isAuctionEnabled(player.getUniqueId())) {
            return true;
        }

        if (args.length == 0) {
            guiManager.openMain(player, guiManager.defaultView());
            return true;
        }
        if (args[0].equalsIgnoreCase("sell") && args.length >= 2) {
            handleSell(player, args[1], plugin.isFastSellEnabled(player.getUniqueId()));
            return true;
        }
        guiManager.openMain(player, guiManager.defaultView().withQuery(String.join(" ", args)));
        return true;
    }

    private void handleSell(Player player, String rawPrice, boolean skipConfirm) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            Text.send(player, plugin.getConfigRegistry().messages().get("errors.hold-item-first"));
            return;
        }
        if (ItemUtil.isBlacklisted(plugin.getConfigRegistry(), hand)) {
            Text.send(player, plugin.getConfigRegistry().messages().get("errors.blacklisted-item"));
            return;
        }
        double price;
        try {
            price = MoneyUtil.parseShort(rawPrice);
        } catch (Exception ex) {
            Text.send(player, plugin.getConfigRegistry().messages().get("errors.invalid-price"));
            return;
        }
        if (skipConfirm) {
            guiManager.completeListing(player, new GuiManager.PendingListing(hand.clone(), price, false), false);
            return;
        }
        guiManager.openConfirmListing(player, hand.clone(), price, false);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("<search>");
        }
        return List.of();
    }
}