package com.maris7.auctionhouse.listener;

import com.maris7.auctionhouse.MarisAuctionPlugin;
import com.maris7.auctionhouse.gui.AuctionHolder;
import com.maris7.auctionhouse.gui.GuiManager;
import com.maris7.auctionhouse.gui.Screen;
import com.maris7.auctionhouse.model.ClaimEntry;
import com.maris7.auctionhouse.service.AuctionService;
import com.maris7.auctionhouse.util.FoliaScheduler;
import com.maris7.auctionhouse.util.ItemUtil;
import com.maris7.auctionhouse.util.MoneyUtil;
import com.maris7.auctionhouse.util.Text;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class GuiListener implements Listener {

    private final GuiManager guiManager;

    public GuiListener(GuiManager guiManager) {
        this.guiManager = guiManager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof AuctionHolder holder)) {
            return;
        }
        if (!MarisAuctionPlugin.getInstance().getDatabaseManager().isAuctionAvailable()) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        if (holder.screen() == Screen.INSERT_ITEM) {
            handleInsertProtection(event);
        } else {
            event.setCancelled(true);
        }

        switch (holder.screen()) {
            case MAIN -> handleMain(event, player, holder);
            case YOUR_ITEMS -> handleYourItems(event, player);
            case INSERT_ITEM -> handleInsert(event, player);
            case CONFIRM -> handleConfirm(event, player);
            case BUY_CONFIRM -> handleBuyConfirm(event, player, holder);
            case TRANSACTIONS -> handleTransactions(event, player, holder);
            case FILTER_MENU -> handleFilterMenu(event, player, holder);
            case SORT_MENU -> handleSortMenu(event, player, holder);
            case SHULKER_PREVIEW -> event.setCancelled(true);
        }
    }

    private void handleInsertProtection(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        int rawSlot = event.getRawSlot();
        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }
        if (rawSlot >= top.getSize()) {
            return;
        }
        int itemSlot = guiManager.insertSlot("item", 2);
        if (rawSlot != itemSlot) {
            event.setCancelled(true);
        }
    }

    private void handleMain(InventoryClickEvent event, Player player, AuctionHolder holder) {
        GuiManager.ViewState state = holder.state();
        int backSlot = guiManager.mainSlot("previous-page", 45);
        int sortSlot = guiManager.mainSlot("sort", 47);
        int filterSlot = guiManager.mainSlot("filter", 48);
        int refreshSlot = guiManager.mainSlot("refresh", 49);
        int searchSlot = guiManager.mainSlot("search", 50);
        int yourItemsSlot = guiManager.mainSlot("your-items", 52);
        int nextSlot = guiManager.mainSlot("next-page", 53);

        if (event.getRawSlot() == backSlot) {
            guiManager.openMain(player, state.withPage(state.page() - 1), "page-turn");
            return;
        }
        if (event.getRawSlot() == sortSlot) {
            int index = (GuiManager.SORTS.indexOf(state.sortMode()) + 1) % GuiManager.SORTS.size();
            guiManager.openMain(player, state.withPage(0).withSort(GuiManager.SORTS.get(index)), "button-click");
            return;
        }
        if (event.getRawSlot() == filterSlot) {
            int index = (GuiManager.FILTERS.indexOf(state.category()) + 1) % GuiManager.FILTERS.size();
            guiManager.openMain(player, state.withPage(0).withCategory(GuiManager.FILTERS.get(index)), "button-click");
            return;
        }
        if (event.getRawSlot() == refreshSlot) {
            guiManager.openMain(player, state, "refresh");
            return;
        }
        if (event.getRawSlot() == searchSlot) {
            MarisAuctionPlugin.getInstance().getSoundService().play(player, "search");
            player.closeInventory();
            FoliaScheduler.runEntity(MarisAuctionPlugin.getInstance(), player, () -> guiManager.promptSearch(player, state));
            return;
        }
        if (event.getRawSlot() == yourItemsSlot) {
            guiManager.openYourItems(player, "button-click");
            return;
        }
        if (event.getRawSlot() == nextSlot) {
            guiManager.openMain(player, state.withPage(state.page() + 1), "page-turn");
            return;
        }

        if (event.getRawSlot() < 0 || event.getRawSlot() >= 45 || event.getCurrentItem() == null || event.getCurrentItem().getType().isAir()) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (event.isRightClick() && ItemUtil.isShulker(clicked)) {
            guiManager.openShulkerPreview(player, clicked, state);
            return;
        }

        long auctionId = readAuctionId(clicked);
        if (auctionId <= 0L) {
            return;
        }

        MarisAuctionPlugin plugin = MarisAuctionPlugin.getInstance();
        String sellerUuid = readSellerUuid(clicked);
        if (sellerUuid != null && sellerUuid.equals(player.getUniqueId().toString())) {
            cancelAndReturn(player, auctionId, true, state);
            return;
        }

        if (player.getInventory().firstEmpty() == -1) {
            Text.send(player, plugin.getConfigRegistry().messages().get("errors.inventory-full"));
            return;
        }

        if (guiManager.hasFastBuy(player.getUniqueId())) {
            executeBuy(player, auctionId, state);
            return;
        }

        guiManager.openBuyConfirm(player, clicked, state);
    }

    private void handleYourItems(InventoryClickEvent event, Player player) {
        if (event.getRawSlot() == guiManager.yourItemsSlot("transactions", 26)) {
            guiManager.openTransactions(player, new GuiManager.ViewState(0, null, "ALL", "RECENTLY_LISTED"), "button-click");
            return;
        }
        if (event.getCurrentItem() == null || event.getCurrentItem().getType().isAir()) {
            return;
        }
        if ("sell_button".equals(readMenuAction(event.getCurrentItem()))) {
            guiManager.openInsertItem(player, null, "button-click");
            return;
        }

        long auctionId = readAuctionId(event.getCurrentItem());
        if (auctionId <= 0L) {
            return;
        }
        if (event.isRightClick() && ItemUtil.isShulker(event.getCurrentItem())) {
            guiManager.openShulkerPreview(player, event.getCurrentItem(), guiManager.lastMainState(player));
            return;
        }

        cancelAndReturn(player, auctionId, false, null);
    }

    private void handleInsert(InventoryClickEvent event, Player player) {
        if (event.getRawSlot() == guiManager.insertSlot("cancel", 0)) {
            ItemStack item = event.getView().getTopInventory().getItem(guiManager.insertSlot("item", 2));
            if (item != null && !item.getType().isAir()) {
                returnItem(player, item);
                event.getView().getTopInventory().setItem(guiManager.insertSlot("item", 2), null);
            }
            FoliaScheduler.runEntity(MarisAuctionPlugin.getInstance(), player, () -> guiManager.openYourItems(player, "back"));
            return;
        }
        if (event.getRawSlot() != guiManager.insertSlot("confirm", 4)) {
            return;
        }
        ItemStack item = event.getView().getTopInventory().getItem(guiManager.insertSlot("item", 2));
        if (item == null || item.getType().isAir()) {
            MarisAuctionPlugin.getInstance().getSoundService().play(player, "fail");
            Text.send(player, MarisAuctionPlugin.getInstance().getConfigRegistry().messages().get("errors.insert-empty"));
            return;
        }
        if (ItemUtil.isBlacklisted(MarisAuctionPlugin.getInstance().getConfigRegistry(), item)) {
            MarisAuctionPlugin.getInstance().getSoundService().play(player, "fail");
            Text.send(player, MarisAuctionPlugin.getInstance().getConfigRegistry().messages().get("errors.blacklisted-item"));
            return;
        }
        event.getView().getTopInventory().setItem(guiManager.insertSlot("item", 2), null);
        guiManager.setInsertDraft(player.getUniqueId(), item.clone());
        guiManager.suppressNextClose(player);
        MarisAuctionPlugin.getInstance().getSoundService().play(player, "button-click");
        player.closeInventory();
        FoliaScheduler.runEntity(MarisAuctionPlugin.getInstance(), player, () -> guiManager.promptPrice(player, item.clone(), true));
    }

    private void handleConfirm(InventoryClickEvent event, Player player) {
        MarisAuctionPlugin plugin = MarisAuctionPlugin.getInstance();
        if (event.getRawSlot() == guiManager.confirmSlot("cancel", 11)) {
            GuiManager.PendingListing pending = guiManager.removePending(player.getUniqueId());
            if (pending != null && pending.alreadyRemoved()) {
                ItemStack restore = guiManager.removeInsertDraft(player.getUniqueId());
                if (restore == null || restore.getType().isAir()) {
                    restore = pending.item();
                }
                ItemStack finalRestore = restore;
                FoliaScheduler.runEntity(plugin, player, () -> guiManager.openInsertItem(player, finalRestore, "back"));
            } else {
                plugin.getSoundService().play(player, "back");
                player.closeInventory();
            }
            return;
        }
        if (event.getRawSlot() != guiManager.confirmSlot("confirm", 15)) {
            return;
        }

        GuiManager.PendingListing pending = guiManager.getPendingListing(player.getUniqueId());
        if (pending == null) {
            return;
        }

        guiManager.completeListing(player, pending, true);
    }

    private void handleBuyConfirm(InventoryClickEvent event, Player player, AuctionHolder holder) {
        if (event.getRawSlot() == guiManager.buyConfirmSlot("cancel", 11)) {
            guiManager.openMain(player, holder.state(), "back");
            return;
        }
        if (event.getRawSlot() != guiManager.buyConfirmSlot("confirm", 15)) {
            return;
        }
        long auctionId = readAuctionId(event.getView().getTopInventory().getItem(guiManager.buyConfirmSlot("item", 13)));
        if (auctionId <= 0L) {
            return;
        }
        executeBuy(player, auctionId, holder.state());
    }

    private void executeBuy(Player player, long auctionId, GuiManager.ViewState state) {
        MarisAuctionPlugin plugin = MarisAuctionPlugin.getInstance();
        if (player.getInventory().firstEmpty() == -1) {
            plugin.getSoundService().play(player, "fail");
            Text.send(player, plugin.getConfigRegistry().messages().get("errors.inventory-full"));
            guiManager.openMain(player, state, null);
            return;
        }
        plugin.getAuctionService().buy(player, auctionId).thenAccept(result -> FoliaScheduler.runEntity(plugin, player, () -> {
            switch (result.status()) {
                case ALREADY_SOLD -> { plugin.getSoundService().play(player, "fail"); Text.send(player, plugin.getConfigRegistry().messages().get("errors.already-sold")); }
                case INSUFFICIENT_FUNDS -> { plugin.getSoundService().play(player, "fail"); Text.send(player, plugin.getConfigRegistry().messages().get("errors.insufficient-funds")); }
                case SELF_BUY -> { plugin.getSoundService().play(player, "fail"); Text.send(player, plugin.getConfigRegistry().messages().get("errors.self-buy")); }
                case SUCCESS -> {
                    if (result.entry() != null) {
                        plugin.getSoundService().play(player, "success");
                        player.getInventory().addItem(result.entry().item().clone());
                        Text.send(player, plugin.getConfigRegistry().messages().get("purchase.success")
                                .replace("%price%", MoneyUtil.formatShort(result.entry().price()))
                                .replace("%seller%", result.entry().sellerName()));
                    }
                }
                default -> { plugin.getSoundService().play(player, "fail"); Text.send(player, plugin.getConfigRegistry().messages().get("errors.buy-failed")); }
            }
            guiManager.openMain(player, state, null);
        }));
    }

    private void handleTransactions(InventoryClickEvent event, Player player, AuctionHolder holder) {
        GuiManager.ViewState state = holder.state();
        int page = state.page();
        if (event.getRawSlot() == guiManager.transactionsSlot("previous-page", 45)) {
            guiManager.openTransactions(player, state.withPage(page - 1), "page-turn");
        } else if (event.getRawSlot() == guiManager.transactionsSlot("refresh", 49)) {
            guiManager.openTransactions(player, state, "refresh");
        } else if (event.getRawSlot() == guiManager.transactionsSlot("search", 50)) {
            guiManager.suppressNextClose(player);
            MarisAuctionPlugin.getInstance().getSoundService().play(player, "search");
            player.closeInventory();
            FoliaScheduler.runEntity(MarisAuctionPlugin.getInstance(), player, () -> MarisAuctionPlugin.getInstance().getSignInputService().openSearch(player, input -> {
                String normalized = input == null || input.isBlank() ? null : input;
                if (normalized == null) {
                    guiManager.openYourItems(player, "back");
                    return;
                }
                guiManager.openTransactions(player, state.withPage(0).withQuery(normalized));
            }));
        } else if (event.getRawSlot() == guiManager.transactionsSlot("next-page", 53)) {
            guiManager.openTransactions(player, state.withPage(page + 1), "page-turn");
        }
    }

    private void handleFilterMenu(InventoryClickEvent event, Player player, AuctionHolder holder) {
        if (event.getRawSlot() == guiManager.filterBackSlot()) {
            guiManager.openMain(player, holder.state());
            return;
        }
        List<Integer> slots = guiManager.filterSlots();
        for (int i = 0; i < GuiManager.FILTERS.size() && i < slots.size(); i++) {
            if (event.getRawSlot() == slots.get(i)) {
                guiManager.openMain(player, holder.state().withPage(0).withCategory(GuiManager.FILTERS.get(i)));
                return;
            }
        }
    }

    private void handleSortMenu(InventoryClickEvent event, Player player, AuctionHolder holder) {
        if (event.getRawSlot() == guiManager.sortBackSlot()) {
            guiManager.openMain(player, holder.state());
            return;
        }
        List<Integer> slots = guiManager.sortSlots();
        for (int i = 0; i < GuiManager.SORTS.size() && i < slots.size(); i++) {
            if (event.getRawSlot() == slots.get(i)) {
                guiManager.openMain(player, holder.state().withPage(0).withSort(GuiManager.SORTS.get(i)));
                return;
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof AuctionHolder holder)) {
            return;
        }
        if (holder.screen() != Screen.INSERT_ITEM) {
            event.setCancelled(true);
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot == guiManager.insertSlot("item", 2)) {
                continue;
            }
            if (rawSlot < event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof AuctionHolder holder)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        if (guiManager.consumeSuppressedClose(player.getUniqueId())) {
            return;
        }

        if (holder.screen() == Screen.INSERT_ITEM) {
            ItemStack item = event.getInventory().getItem(guiManager.insertSlot("item", 2));
            guiManager.removeInsertDraft(player.getUniqueId());
            if (item != null && !item.getType().isAir()) {
                returnItem(player, item);
            }
        }

        if (holder.screen() == Screen.CONFIRM) {
            GuiManager.PendingListing pending = guiManager.removePending(player.getUniqueId());
            guiManager.removeInsertDraft(player.getUniqueId());
            if (pending != null && pending.alreadyRemoved()) {
                returnItem(player, pending.item());
            }
        }

        FoliaScheduler.runEntity(MarisAuctionPlugin.getInstance(), player, () -> guiManager.handleManualClose(player, holder.screen(), holder.state()));
    }

    private void cancelAndReturn(Player player, long auctionId, boolean reopenMain, GuiManager.ViewState state) {
        MarisAuctionPlugin plugin = MarisAuctionPlugin.getInstance();
        plugin.getAuctionService().cancelListing(player.getUniqueId(), auctionId).thenAccept(result -> FoliaScheduler.runEntity(plugin, player, () -> {
            if (result.status() != AuctionService.Status.SUCCESS) {
                plugin.getSoundService().play(player, "fail");
                Text.send(player, plugin.getConfigRegistry().messages().get("errors.cancel-failed"));
                if (reopenMain && state != null) {
                    guiManager.openMain(player, state, null);
                } else {
                    guiManager.openYourItems(player, null);
                }
                return;
            }
            plugin.getAuctionService().getClaims(player.getUniqueId()).thenAccept(claims -> FoliaScheduler.runEntity(plugin, player, () ->
                    claimToInventory(player, claims, false).thenRun(() -> FoliaScheduler.runEntity(plugin, player, () -> {
                        if (reopenMain && state != null) {
                            guiManager.openMain(player, state);
                        } else {
                            guiManager.openYourItems(player, "back");
                        }
                    }))
            ));
        }));
    }

    private String readSellerUuid(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(MarisAuctionPlugin.getInstance(), "seller_uuid"), PersistentDataType.STRING);
    }

    private String readMenuAction(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(MarisAuctionPlugin.getInstance(), "menu_action"), PersistentDataType.STRING);
    }

    private void returnItem(Player player, ItemStack item) {
        if (player.getInventory().addItem(item.clone()).isEmpty()) {
            return;
        }
        player.getWorld().dropItemNaturally(player.getLocation(), item.clone());
    }

    private long readAuctionId(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return 0L;
        }
        Long value = item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(MarisAuctionPlugin.getInstance(), "auction_id"), PersistentDataType.LONG);
        return value == null ? 0L : value;
    }

    private java.util.concurrent.CompletableFuture<Integer> claimToInventory(Player player, List<ClaimEntry> claims, boolean showEmptyMessage) {
        MarisAuctionPlugin plugin = MarisAuctionPlugin.getInstance();
        if (claims.isEmpty()) {
            return java.util.concurrent.CompletableFuture.completedFuture(0);
        }
        List<Long> claimedIds = new ArrayList<>();
        boolean inventoryFilled = false;
        for (ClaimEntry claim : claims) {
            if (player.getInventory().firstEmpty() == -1) {
                inventoryFilled = true;
                break;
            }
            ItemStack clone = claim.item().clone();
            if (player.getInventory().addItem(clone).isEmpty()) {
                claimedIds.add(claim.id());
            } else {
                inventoryFilled = true;
                break;
            }
        }
        if (claimedIds.isEmpty()) {
            return java.util.concurrent.CompletableFuture.completedFuture(0);
        }
        return plugin.getAuctionService().deleteClaims(player.getUniqueId(), claimedIds);
    }
}
