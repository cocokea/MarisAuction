package com.maris7.auctionhouse.gui;

import com.maris7.auctionhouse.MarisAuctionPlugin;
import com.maris7.auctionhouse.model.AuctionEntry;
import com.maris7.auctionhouse.model.ClaimEntry;
import com.maris7.auctionhouse.model.TransactionEntry;
import com.maris7.auctionhouse.service.AuctionService;
import com.maris7.auctionhouse.service.SignInputService;
import com.maris7.auctionhouse.util.FoliaScheduler;
import com.maris7.auctionhouse.util.ItemUtil;
import com.maris7.auctionhouse.util.MoneyUtil;
import com.maris7.auctionhouse.util.PermissionUtil;
import com.maris7.auctionhouse.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuiManager {

    public static final List<String> FILTERS = List.of("ALL", "BLOCKS", "TOOLS", "FOOD", "COMBAT", "POTIONS", "BOOKS", "INGREDIENTS", "UTILITIES");
    public static final List<String> SORTS = List.of("HIGHEST_PRICE", "LOWEST_PRICE", "RECENTLY_LISTED");

    private final MarisAuctionPlugin plugin;
    private final AuctionService auctionService;
    private final SignInputService signInputService;
    private final Map<UUID, PendingListing> pendingListings = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> insertDrafts = new ConcurrentHashMap<>();
    private final Map<UUID, ViewState> lastMainStates = new ConcurrentHashMap<>();
    private final Map<UUID, CloseTarget> closeTargets = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> suppressedCloses = ConcurrentHashMap.newKeySet();

    public GuiManager(MarisAuctionPlugin plugin, AuctionService auctionService, SignInputService signInputService) {
        this.plugin = plugin;
        this.auctionService = auctionService;
        this.signInputService = signInputService;
    }

    public boolean hasFastBuy(UUID uniqueId) {
        return plugin.isFastBuyEnabled(uniqueId);
    }




    public ViewState defaultView() {
        return new ViewState(0, null, "ALL", "RECENTLY_LISTED");
    }

    public void openMain(Player player, String query) {
        openMain(player, new ViewState(0, query, "ALL", "RECENTLY_LISTED"));
    }

    public void openMain(Player player, ViewState state) {
        openMain(player, state, null);
    }

    public void openMain(Player player, ViewState state, String soundKey) {
        AuctionService.BrowseRequest request = new AuctionService.BrowseRequest(state.query(), state.category(), state.sortMode());
        auctionService.browse(request).thenAccept(list -> FoliaScheduler.runEntity(plugin, player, () -> {
            YamlConfiguration cfg = guiFile("auction-main");
            int size = cfg.getInt("inventory.size", 54);
            int pageSize = Math.min(45, cfg.getIntegerList("inventory.content-slots").size());
            if (pageSize <= 0) {
                pageSize = 45;
            }
            int maxPage = maxPage(list.size(), pageSize);
            int page = clampPage(state.page(), maxPage);
            ViewState applied = state.withPage(page);
            lastMainStates.put(player.getUniqueId(), applied);

            AuctionHolder holder = new AuctionHolder(Screen.MAIN, applied);
            Inventory inventory = Bukkit.createInventory(holder, size, title(cfg, "title", "&8AUCTION (/Page %pages%)", page + 1));
            holder.setInventory(inventory);

            List<Integer> contentSlots = contentSlots(cfg, size);
            List<AuctionEntry> slice = pageSlice(list, page, pageSize);
            for (int i = 0; i < Math.min(contentSlots.size(), slice.size()); i++) {
                inventory.setItem(contentSlots.get(i), saleIcon(slice.get(i), true, false));
            }

            setConfiguredButton(inventory, cfg, "buttons.previous-page", simple(material(cfg, "buttons.previous-page.material", Material.ARROW), string(cfg, "buttons.previous-page.name", "&#00FF42ʙᴀᴄᴋ"), lore(cfg, "buttons.previous-page.lore", List.of("&fClick to go to the previous page"))));
            if (page == 0) {
                inventory.clear(cfg.getInt("buttons.previous-page.slot", 45));
            }
            setConfiguredButton(inventory, cfg, "buttons.sort", sortButton(applied));
            setConfiguredButton(inventory, cfg, "buttons.filter", filterButton(applied));
            setConfiguredButton(inventory, cfg, "buttons.refresh", simple(material(cfg, "buttons.refresh.material", Material.ANVIL), string(cfg, "buttons.refresh.name", "&#00FF42ᴀᴜᴄᴛɪᴏɴ"), lore(cfg, "buttons.refresh.lore", List.of("&fClick to refresh"))));
            setConfiguredButton(inventory, cfg, "buttons.search", searchButton(applied));
            setConfiguredButton(inventory, cfg, "buttons.your-items", simple(material(cfg, "buttons.your-items.material", Material.CHEST), string(cfg, "buttons.your-items.name", "&#00FF42ʏᴏᴜʀ ɪᴛᴇᴍs"), lore(cfg, "buttons.your-items.lore", List.of("&fClick to view the items you have listed."))));
            setConfiguredButton(inventory, cfg, "buttons.next-page", simple(material(cfg, "buttons.next-page.material", Material.ARROW), string(cfg, "buttons.next-page.name", "&#00FF42ɴᴇxᴛ"), lore(cfg, "buttons.next-page.lore", List.of("&fClick to go to the next page"))));
            if (page >= maxPage) {
                inventory.clear(cfg.getInt("buttons.next-page.slot", 53));
            }
            present(player, inventory, null);
            playConfiguredSound(player, soundKey);
        }));
    }

    public ViewState lastMainState(Player player) {
        return lastMainStates.getOrDefault(player.getUniqueId(), defaultView());
    }

    public void openFilterMenu(Player player, ViewState parent) {
        YamlConfiguration cfg = guiFile("filter-menu");
        AuctionHolder holder = new AuctionHolder(Screen.FILTER_MENU, parent);
        Inventory inventory = Bukkit.createInventory(holder, cfg.getInt("inventory.size", 27), guiTitle(cfg, "title", "&8ғɪʟᴛᴇʀ"));
        holder.setInventory(inventory);
        fillConfigured(inventory, cfg, "filler", Material.BLACK_STAINED_GLASS_PANE, " ");
        List<Integer> slots = cfg.getIntegerList("option-slots");
        for (int i = 0; i < Math.min(FILTERS.size(), slots.size()); i++) {
            String filter = FILTERS.get(i);
            boolean selected = filter.equalsIgnoreCase(parent.category());
            inventory.setItem(slots.get(i), simple(
                    material(cfg, "option-material", Material.PAPER),
                    selected ? string(cfg, "selected-format", "&#00FF42• %name%").replace("%name%", pretty(filter)) : string(cfg, "normal-format", "&f• %name%").replace("%name%", pretty(filter)),
                    selected ? lore(cfg, "selected-lore", List.of("&7Selected")) : lore(cfg, "normal-lore", List.of("&7Click to apply"))
            ));
        }
        setConfiguredButton(inventory, cfg, "back", simple(material(cfg, "back.material", Material.ARROW), string(cfg, "back.name", "&#00FF42ʙᴀᴄᴋ"), lore(cfg, "back.lore", List.of("&7Back to auction"))));
        player.openInventory(inventory);
        plugin.getSoundService().play(player, "menu-open");
    }

    public void openSortMenu(Player player, ViewState parent) {
        YamlConfiguration cfg = guiFile("sort-menu");
        AuctionHolder holder = new AuctionHolder(Screen.SORT_MENU, parent);
        Inventory inventory = Bukkit.createInventory(holder, cfg.getInt("inventory.size", 27), guiTitle(cfg, "title", "&8sᴏʀᴛ"));
        holder.setInventory(inventory);
        fillConfigured(inventory, cfg, "filler", Material.BLACK_STAINED_GLASS_PANE, " ");
        List<Integer> slots = cfg.getIntegerList("option-slots");
        for (int i = 0; i < Math.min(SORTS.size(), slots.size()); i++) {
            String sort = SORTS.get(i);
            boolean selected = sort.equalsIgnoreCase(parent.sortMode());
            inventory.setItem(slots.get(i), simple(
                    material(cfg, "option-material", Material.CAULDRON),
                    selected ? string(cfg, "selected-format", "&#00FF42• %name%").replace("%name%", pretty(sort)) : string(cfg, "normal-format", "&f• %name%").replace("%name%", pretty(sort)),
                    selected ? lore(cfg, "selected-lore", List.of("&7Selected")) : lore(cfg, "normal-lore", List.of("&7Click to apply"))
            ));
        }
        setConfiguredButton(inventory, cfg, "back", simple(material(cfg, "back.material", Material.ARROW), string(cfg, "back.name", "&#00FF42ʙᴀᴄᴋ"), lore(cfg, "back.lore", List.of("&7Back to auction"))));
        player.openInventory(inventory);
        plugin.getSoundService().play(player, "menu-open");
    }

    public void openYourItems(Player player) {
        openYourItems(player, "menu-open");
    }

    public void openYourItems(Player player, String soundKey) {
        auctionService.getClaims(player.getUniqueId())
                .thenCompose(claims -> deliverStoredReturns(player, claims))
                .thenCompose(ignored -> auctionService.getOwn(player.getUniqueId()))
                .thenAccept(list -> FoliaScheduler.runEntity(plugin, player, () -> {
                    YamlConfiguration cfg = guiFile("your-items");
                    AuctionHolder holder = new AuctionHolder(Screen.YOUR_ITEMS, lastMainState(player));
                    Inventory inventory = Bukkit.createInventory(holder, cfg.getInt("inventory.size", 27), title(cfg, "title", "&8ᴀᴜᴄᴛɪᴏɴ - ʏᴏᴜʀ ɪᴛᴇᴍs", 1));
                    holder.setInventory(inventory);
                    int slot = 0;
                    for (AuctionEntry entry : list.stream().limit(26).toList()) {
                        inventory.setItem(slot++, saleIcon(entry, false, true));
                    }
                    int sellSlot = Math.min(slot, 25);
                    if (sellSlot <= 25) {
                        inventory.setItem(sellSlot, taggedAction(simple(material(cfg, "buttons.sell.material", Material.MAP), string(cfg, "buttons.sell.name", "&#00FF42Sell Item"), lore(cfg, "buttons.sell.lore", List.of("&fClick to list an item", " ", "&8Or", "&8Hold an item and type", "&8/ah sell (price)", " ", "&8Click your listed items to return them"))), "sell_button"));
                    }
                    setConfiguredButton(inventory, cfg, "buttons.transactions", simple(material(cfg, "buttons.transactions.material", Material.WRITTEN_BOOK), string(cfg, "buttons.transactions.name", "&#00FF42ᴛʀᴀɴsᴀᴄᴛɪᴏɴs"), lore(cfg, "buttons.transactions.lore", List.of("&fClick to see your purchases and sales."))));
                    present(player, inventory, new CloseTarget(Screen.MAIN, lastMainState(player)));
                    playConfiguredSound(player, soundKey);
                }));
    }

    private java.util.concurrent.CompletableFuture<Void> deliverStoredReturns(Player player, List<ClaimEntry> claims) {
        if (claims.isEmpty()) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        java.util.concurrent.CompletableFuture<Void> future = new java.util.concurrent.CompletableFuture<>();
        FoliaScheduler.runEntity(plugin, player, () -> {
            List<Long> deliveredIds = new ArrayList<>();
            for (ClaimEntry claim : claims) {
                if (player.getInventory().firstEmpty() == -1) {
                    break;
                }
                if (player.getInventory().addItem(claim.item().clone()).isEmpty()) {
                    deliveredIds.add(claim.id());
                } else {
                    break;
                }
            }
            if (deliveredIds.isEmpty()) {
                future.complete(null);
                return;
            }
            boolean hasExpired = claims.stream().anyMatch(claim -> "EXPIRED".equalsIgnoreCase(claim.reason()));
            plugin.getSoundService().play(player, hasExpired ? "expired-notify" : "seller-notify");
            auctionService.deleteClaims(player.getUniqueId(), deliveredIds)
                    .thenAccept(removed -> future.complete(null))
                    .exceptionally(ex -> {
                        future.completeExceptionally(ex);
                        return null;
                    });
        });
        return future;
    }

    public void openInsertItem(Player player) {
        openInsertItem(player, null, "menu-open");
    }

    public void openInsertItem(Player player, ItemStack prefilledItem) {
        openInsertItem(player, prefilledItem, "menu-open");
    }

    public void openInsertItem(Player player, ItemStack prefilledItem, String soundKey) {
        removeInsertDraft(player.getUniqueId());
        YamlConfiguration cfg = guiFile("insert-item");
        AuctionHolder holder = new AuctionHolder(Screen.INSERT_ITEM, lastMainState(player));
        Inventory inventory = Bukkit.createInventory(holder, InventoryType.HOPPER, title(cfg, "title", "&8ɪɴsᴇʀᴛ ɪᴛᴇᴍ", 1));
        holder.setInventory(inventory);
        setConfiguredButton(inventory, cfg, "buttons.cancel", simple(material(cfg, "buttons.cancel.material", Material.RED_STAINED_GLASS_PANE), string(cfg, "buttons.cancel.name", "&#FF0000ᴄᴀɴᴄᴇʟ"), lore(cfg, "buttons.cancel.lore", List.of("&7Return to your items"))));
        setConfiguredButton(inventory, cfg, "buttons.left-fill", simple(material(cfg, "buttons.left-fill.material", Material.GRAY_STAINED_GLASS_PANE), string(cfg, "buttons.left-fill.name", " ")));
        setConfiguredButton(inventory, cfg, "buttons.right-fill", simple(material(cfg, "buttons.right-fill.material", Material.GRAY_STAINED_GLASS_PANE), string(cfg, "buttons.right-fill.name", " ")));
        setConfiguredButton(inventory, cfg, "buttons.confirm", simple(material(cfg, "buttons.confirm.material", Material.LIME_STAINED_GLASS_PANE), string(cfg, "buttons.confirm.name", "&aᴄᴏɴғɪʀᴍ"), lore(cfg, "buttons.confirm.lore", List.of("&fClick to continue"))));
        if (prefilledItem != null && !prefilledItem.getType().isAir()) {
            inventory.setItem(insertSlot("item", 2), prefilledItem.clone());
        }
        present(player, inventory, new CloseTarget(Screen.YOUR_ITEMS, lastMainState(player)));
        playConfiguredSound(player, soundKey);
    }

    public void openConfirmListing(Player player, ItemStack item, double price, boolean alreadyRemoved) {
        openConfirmListing(player, item, price, alreadyRemoved, "menu-open");
    }

    public void openConfirmListing(Player player, ItemStack item, double price, boolean alreadyRemoved, String soundKey) {
        pendingListings.put(player.getUniqueId(), new PendingListing(item.clone(), price, alreadyRemoved));
        YamlConfiguration cfg = guiFile("confirm-listing");
        int size = ItemUtil.isShulker(item) ? cfg.getInt("shulker-inventory.size", 54) : cfg.getInt("inventory.size", 27);
        AuctionHolder holder = new AuctionHolder(Screen.CONFIRM, lastMainState(player));
        Inventory inventory = Bukkit.createInventory(holder, size, title(cfg, "title", "&8ᴄᴏɴғɪʀᴍ ʟɪsᴛɪɴɢ", 1));
        holder.setInventory(inventory);
        int cancelSlot = cfg.getInt("buttons.cancel.slot", 11);
        int itemSlot = cfg.getInt("buttons.item.slot", 13);
        int confirmSlot = cfg.getInt("buttons.confirm.slot", 15);
        inventory.setItem(cancelSlot, simple(material(cfg, "buttons.cancel.material", Material.RED_STAINED_GLASS_PANE), string(cfg, "buttons.cancel.name", "&#FF0000ᴄᴀɴᴄᴇʟ"), lore(cfg, "buttons.cancel.lore", List.of("&7Do not list this item"))));
        ItemStack preview = item.clone();
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            meta.setItemName(Text.color(ItemUtil.resolveName(preview)));
            List<String> itemLore = new ArrayList<>();
            if (meta.hasLore() && meta.getLore() != null) {
                itemLore.addAll(meta.getLore());
                if (!itemLore.isEmpty()) {
                    itemLore.add(" ");
                }
            }
            itemLore.addAll(lore(cfg, "buttons.item.lore", List.of("&fYou''re going to sell", "&fthis item for &a$%price%")));
            for (int i = 0; i < itemLore.size(); i++) {
                itemLore.set(i, itemLore.get(i).replace("%price%", MoneyUtil.formatShort(price)));
            }
            meta.setLore(Text.colors(itemLore));
            preview.setItemMeta(meta);
        }
        inventory.setItem(itemSlot, preview);
        inventory.setItem(confirmSlot, simple(material(cfg, "buttons.confirm.material", Material.LIME_STAINED_GLASS_PANE), string(cfg, "buttons.confirm.name", "&aᴄᴏɴғɪʀᴍ"), lore(cfg, "buttons.confirm.lore", List.of("&fClick to sell"))));
        if (size >= 54 && ItemUtil.isShulker(item)) {
            ItemStack[] contents = ItemUtil.shulkerContents(item);
            int base = cfg.getInt("shulker-inventory.preview-start-slot", 27);
            for (int i = 0; i < Math.min(27, contents.length); i++) {
                inventory.setItem(base + i, contents[i]);
            }
        }
        present(player, inventory, alreadyRemoved ? new CloseTarget(Screen.YOUR_ITEMS, lastMainState(player)) : null);
        playConfiguredSound(player, soundKey);
    }

    public void openTransactions(Player player, int page) {
        openTransactions(player, new ViewState(page, null, "ALL", "RECENTLY_LISTED"));
    }

    public void openTransactions(Player player, ViewState state) {
        openTransactions(player, state, "menu-open");
    }

    public void openTransactions(Player player, ViewState state, String soundKey) {
        auctionService.getTransactions(player.getUniqueId()).thenAccept(list -> FoliaScheduler.runEntity(plugin, player, () -> {
            String query = state.query();
            List<TransactionEntry> filtered = list;
            if (query != null && !query.isBlank()) {
                String lower = ItemUtil.normalizeSearchText(query);
                filtered = list.stream().filter(entry ->
                        ItemUtil.normalizeSearchText(ItemUtil.resolveName(entry.item())).contains(lower)
                                || ItemUtil.normalizeSearchText(entry.counterpartyName()).contains(lower)
                ).toList();
            }
            YamlConfiguration cfg = guiFile("transactions");
            int pageSize = Math.min(45, cfg.getIntegerList("inventory.content-slots").size());
            if (pageSize <= 0) {
                pageSize = 45;
            }
            int maxPage = maxPage(filtered.size(), pageSize);
            int appliedPage = clampPage(state.page(), maxPage);
            ViewState applied = state.withPage(appliedPage);
            AuctionHolder holder = new AuctionHolder(Screen.TRANSACTIONS, applied);
            Inventory inventory = Bukkit.createInventory(holder, cfg.getInt("inventory.size", 54), title(cfg, "title", "&8ᴛʀᴀɴsᴀᴄᴛɪᴏɴs (Page %pages%)", appliedPage + 1));
            holder.setInventory(inventory);
            double spent = 0D;
            double made = 0D;
            for (TransactionEntry entry : list) {
                if (entry.purchase()) {
                    spent += entry.price();
                } else {
                    made += entry.price();
                }
            }
            List<Integer> contentSlots = contentSlots(cfg, cfg.getInt("inventory.size", 54));
            List<TransactionEntry> slice = pageSlice(filtered, appliedPage, pageSize);
            for (int i = 0; i < Math.min(contentSlots.size(), slice.size()); i++) {
                inventory.setItem(contentSlots.get(i), transactionIcon(slice.get(i)));
            }
            setConfiguredButton(inventory, cfg, "buttons.previous-page", simple(material(cfg, "buttons.previous-page.material", Material.ARROW), string(cfg, "buttons.previous-page.name", "&#00FF42ʙᴀᴄᴋ"), lore(cfg, "buttons.previous-page.lore", List.of("&fClick to go to the previous page"))));
            if (appliedPage == 0) {
                inventory.clear(cfg.getInt("buttons.previous-page.slot", 45));
            }
            setConfiguredButton(inventory, cfg, "buttons.stats", simple(material(cfg, "buttons.stats.material", Material.BOOK), string(cfg, "buttons.stats.name", "&#00FF42sᴛᴀᴛs"),
                    List.of(
                            string(cfg, "buttons.stats.spent", "&7Total Spent: &#00FF42$%spent%").replace("%spent%", MoneyUtil.formatShort(spent)),
                            string(cfg, "buttons.stats.made", "&7Total Made: &#00FF42$%made%").replace("%made%", MoneyUtil.formatShort(made))
                    )));
            setConfiguredButton(inventory, cfg, "buttons.refresh", simple(material(cfg, "buttons.refresh.material", Material.ANVIL), string(cfg, "buttons.refresh.name", "&#00FF42ᴀᴜᴄᴛɪᴏɴ"), lore(cfg, "buttons.refresh.lore", List.of("&fClick to refresh"))));
            setConfiguredButton(inventory, cfg, "buttons.search", searchButton(applied));
            setConfiguredButton(inventory, cfg, "buttons.next-page", simple(material(cfg, "buttons.next-page.material", Material.ARROW), string(cfg, "buttons.next-page.name", "&#00FF42ɴᴇxᴛ"), lore(cfg, "buttons.next-page.lore", List.of("&fClick to go to the next page"))));
            if (appliedPage >= maxPage) {
                inventory.clear(cfg.getInt("buttons.next-page.slot", 53));
            }
            present(player, inventory, new CloseTarget(Screen.YOUR_ITEMS, lastMainState(player)));
            playConfiguredSound(player, soundKey);
        }));
    }

    public void openShulkerPreview(Player player, ItemStack shulker, ViewState returnState) {
        openShulkerPreview(player, shulker, returnState, "preview-open");
    }

    public void openShulkerPreview(Player player, ItemStack shulker, ViewState returnState, String soundKey) {
        YamlConfiguration cfg = guiFile("shulker-preview");
        AuctionHolder holder = new AuctionHolder(Screen.SHULKER_PREVIEW, returnState == null ? lastMainState(player) : returnState);
        Inventory preview = Bukkit.createInventory(holder, cfg.getInt("inventory.size", 27), guiTitle(cfg, "title", "&8sʜᴜʟᴋᴇʀ ᴘʀᴇᴠɪᴇᴡ"));
        holder.setInventory(preview);
        ItemStack[] contents = ItemUtil.shulkerContents(shulker);
        int base = cfg.getInt("inventory.preview-start-slot", 0);
        for (int i = 0; i < Math.min(27, contents.length); i++) {
            preview.setItem(base + i, contents[i]);
        }
        present(player, preview, new CloseTarget(Screen.MAIN, returnState == null ? lastMainState(player) : returnState));
        playConfiguredSound(player, soundKey);
    }

    public void promptSearch(Player player, ViewState state) {
        signInputService.openSearch(player, input -> {
            String normalized = input == null || input.isBlank() ? null : input;
            if (normalized == null) {
                openMain(player, state, "back");
                return;
            }
            openMain(player, state.withPage(0).withQuery(normalized));
        });
    }

    public void promptPrice(Player player, ItemStack item, boolean alreadyRemoved) {
        signInputService.openPrice(player, text -> {
            if (text == null || text.isBlank()) {
                FoliaScheduler.runEntity(plugin, player, () -> openInsertItem(player, item.clone(), "back"));
                return;
            }
            try {
                if (ItemUtil.isBlacklisted(plugin.getConfigRegistry(), item)) {
                    Text.send(player, plugin.getConfigRegistry().messages().get("errors.blacklisted-item"));
                    if (alreadyRemoved) {
                        player.getInventory().addItem(item.clone());
                    }
                    return;
                }
                double price = MoneyUtil.parseShort(text);
                FoliaScheduler.runEntity(plugin, player, () -> {
                    openConfirmListing(player, item, price, alreadyRemoved);
                });
            } catch (Exception ex) {
                if (alreadyRemoved) {
                    player.getInventory().addItem(item);
                    removeInsertDraft(player.getUniqueId());
                }
                Text.send(player, plugin.getConfigRegistry().messages().get("errors.invalid-price"));
            }
        });
    }

    public void openBuyConfirm(Player player, ItemStack saleItem, ViewState returnState) {
        YamlConfiguration cfg = guiFile("buy-confirm");
        boolean shulker = ItemUtil.isShulker(saleItem);
        int size = shulker ? cfg.getInt("shulker-inventory.size", 54) : cfg.getInt("inventory.size", 27);
        String rawTitle = shulker
                ? string(cfg, "title-shulker", "&8ᴄᴏɴғɪʀᴍ ᴘᴜʀᴄʜᴀsᴇ ᴀɴᴅ ᴘʀᴇᴠɪᴇᴡ")
                : string(cfg, "title", "&8ᴄᴏɴғɪʀᴍ ᴘᴜʀᴄʜᴀsᴇ");
        AuctionHolder holder = new AuctionHolder(Screen.BUY_CONFIRM, returnState == null ? lastMainState(player) : returnState);
        Inventory inventory = Bukkit.createInventory(holder, size, Text.color(rawTitle));
        holder.setInventory(inventory);

        int cancelSlot = cfg.getInt("buttons.cancel.slot", 11);
        int itemSlot = cfg.getInt("buttons.item.slot", 13);
        int confirmSlot = cfg.getInt("buttons.confirm.slot", 15);

        inventory.setItem(cancelSlot, simple(material(cfg, "buttons.cancel.material", Material.RED_STAINED_GLASS_PANE), string(cfg, "buttons.cancel.name", "&#FF0000ᴄᴀɴᴄᴇʟ"), lore(cfg, "buttons.cancel.lore", List.of("&fClick to cancel"))));

        ItemStack preview = saleItem.clone();
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            String seller = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "seller_name"), PersistentDataType.STRING);
            Double price = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "auction_price"), PersistentDataType.DOUBLE);
            Long expiresAt = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "expires_at"), PersistentDataType.LONG);
            List<String> itemLore = new ArrayList<>();
            if (meta.hasLore() && meta.getLore() != null) {
                itemLore.addAll(meta.getLore());
                if (!itemLore.isEmpty()) {
                    itemLore.add(" ");
                }
            }
            itemLore.addAll(lore(cfg, "buttons.item.lore", List.of("&fPrice: &a%price%", "&fSeller: &#00FF8C%seller%", "&fTime Left: &#00FF8C%time_left%")));
            for (int i = 0; i < itemLore.size(); i++) {
                itemLore.set(i, itemLore.get(i)
                        .replace("%price%", MoneyUtil.formatShort(price == null ? 0D : price))
                        .replace("%seller%", seller == null ? "Unknown" : seller)
                        .replace("%time_left%", formatTimeLeft(expiresAt == null ? 0L : expiresAt)));
            }
            meta.setItemName(Text.color(ItemUtil.resolveName(preview)));
            meta.setDisplayName(null);
            meta.setLore(Text.colors(itemLore));
            preview.setItemMeta(meta);
        }
        inventory.setItem(itemSlot, preview);
        inventory.setItem(confirmSlot, simple(material(cfg, "buttons.confirm.material", Material.LIME_STAINED_GLASS_PANE), string(cfg, "buttons.confirm.name", "&aᴄᴏɴғɪʀᴍ"), lore(cfg, "buttons.confirm.lore", List.of("&fClick to buy"))));

        if (shulker && size >= 54) {
            ItemStack[] contents = ItemUtil.shulkerContents(saleItem);
            int base = cfg.getInt("shulker-inventory.preview-start-slot", 27);
            for (int i = 0; i < Math.min(27, contents.length); i++) {
                inventory.setItem(base + i, contents[i]);
            }
        }

        present(player, inventory, new CloseTarget(Screen.MAIN, returnState == null ? lastMainState(player) : returnState));
        playConfiguredSound(player, "menu-open");
    }

    public void completeListing(Player player, PendingListing pending, boolean closeInventory) {
        auctionService.countActive(player.getUniqueId()).thenAccept(count -> FoliaScheduler.runEntity(plugin, player, () -> {
            int limit = PermissionUtil.getSellLimit(player);
            if (limit == 0 || (limit > 0 && count >= limit)) {
                plugin.getSoundService().play(player, "fail");
                Text.send(player, plugin.getConfigRegistry().messages().get("errors.sell-limit"));
                if (pending.alreadyRemoved()) {
                    ItemStack restore = removeInsertDraft(player.getUniqueId());
                    if (restore == null || restore.getType().isAir()) {
                        restore = pending.item();
                    }
                    player.getInventory().addItem(restore.clone());
                }
                if (pendingListings.get(player.getUniqueId()) == pending) {
                    clearPending(player.getUniqueId());
                }
                return;
            }

            if (!pending.alreadyRemoved()) {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand == null || hand.getType().isAir() || !hand.isSimilar(pending.item()) || hand.getAmount() < pending.item().getAmount()) {
                    plugin.getSoundService().play(player, "fail");
                    Text.send(player, plugin.getConfigRegistry().messages().get("errors.invalid-hand-item"));
                    return;
                }
                if (hand.getAmount() == pending.item().getAmount()) {
                    player.getInventory().setItemInMainHand(null);
                } else {
                    hand.setAmount(hand.getAmount() - pending.item().getAmount());
                    player.getInventory().setItemInMainHand(hand);
                }
            }

            auctionService.list(player, pending.item(), pending.price()).thenRun(() -> FoliaScheduler.runEntity(plugin, player, () -> {
                plugin.getSoundService().play(player, "sell-success");
                if (pendingListings.get(player.getUniqueId()) == pending) {
                    clearPending(player.getUniqueId());
                }
                if (closeInventory) {
                    player.closeInventory();
                }
                Text.send(player, plugin.getConfigRegistry().messages().get("listing.created").replace("%price%", MoneyUtil.formatShort(pending.price())));
            })).exceptionally(ex -> {
                FoliaScheduler.runEntity(plugin, player, () -> {
                    if (pending.alreadyRemoved()) {
                        ItemStack restore = removeInsertDraft(player.getUniqueId());
                        if (restore == null || restore.getType().isAir()) {
                            restore = pending.item();
                        }
                        player.getInventory().addItem(restore.clone());
                    }
                    if (pendingListings.get(player.getUniqueId()) == pending) {
                        clearPending(player.getUniqueId());
                    }
                    plugin.getSoundService().play(player, "fail");
                    Text.send(player, plugin.getConfigRegistry().messages().get("errors.listing-failed"));
                });
                return null;
            });
        }));
    }

    private String formatTimeLeft(long expiresAt) {
        long left = Math.max(0L, expiresAt - System.currentTimeMillis());
        long totalMinutes = left / 60000L;
        long days = totalMinutes / 1440L;
        long hours = (totalMinutes % 1440L) / 60L;
        long minutes = totalMinutes % 60L;

        List<String> parts = new ArrayList<>();
        if (days > 0L) {
            parts.add(days + "d");
        }
        if (hours > 0L || !parts.isEmpty()) {
            parts.add(hours + "h");
        }
        if (minutes > 0L) {
            parts.add(minutes + "m");
        }
        if (parts.isEmpty()) {
            parts.add("0m");
        }
        return String.join(" ", parts);
    }

    public PendingListing getPendingListing(UUID uniqueId) {
        return pendingListings.get(uniqueId);
    }

    public PendingListing removePending(UUID uniqueId) {
        return pendingListings.remove(uniqueId);
    }

    public void clearPending(UUID uniqueId) {
        pendingListings.remove(uniqueId);
        insertDrafts.remove(uniqueId);
    }

    public void setInsertDraft(UUID uniqueId, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            insertDrafts.remove(uniqueId);
            return;
        }
        insertDrafts.put(uniqueId, item.clone());
    }

    public ItemStack getInsertDraft(UUID uniqueId) {
        ItemStack item = insertDrafts.get(uniqueId);
        return item == null ? null : item.clone();
    }

    public ItemStack removeInsertDraft(UUID uniqueId) {
        ItemStack item = insertDrafts.remove(uniqueId);
        return item == null ? null : item.clone();
    }

    public void shutdown() {
        pendingListings.clear();
        insertDrafts.clear();
    }

    public int mainSlot(String key, int fallback) {
        return guiFile("auction-main").getInt("buttons." + key + ".slot", fallback);
    }

    public int yourItemsSlot(String key, int fallback) {
        return guiFile("your-items").getInt("buttons." + key + ".slot", fallback);
    }

    public int insertSlot(String key, int fallback) {
        return guiFile("insert-item").getInt("buttons." + key + ".slot", fallback);
    }

    public int confirmSlot(String key, int fallback) {
        return guiFile("confirm-listing").getInt("buttons." + key + ".slot", fallback);
    }

    public int buyConfirmSlot(String key, int fallback) {
        return guiFile("buy-confirm").getInt("buttons." + key + ".slot", fallback);
    }

    public int transactionsSlot(String key, int fallback) {
        return guiFile("transactions").getInt("buttons." + key + ".slot", fallback);
    }

    public List<Integer> filterSlots() {
        List<Integer> slots = guiFile("filter-menu").getIntegerList("option-slots");
        return slots.isEmpty() ? List.of(0,1,2,3,4,5,6,7,8) : slots;
    }

    public List<Integer> sortSlots() {
        List<Integer> slots = guiFile("sort-menu").getIntegerList("option-slots");
        return slots.isEmpty() ? List.of(11,13,15) : slots;
    }

    public int filterBackSlot() {
        return guiFile("filter-menu").getInt("back.slot", 26);
    }

    public int sortBackSlot() {
        return guiFile("sort-menu").getInt("back.slot", 26);
    }

    private ItemStack saleIcon(AuctionEntry entry, boolean shulkerPreviewHint, boolean ownItem) {
        ItemStack icon = entry.item().clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            List<String> itemLore = new ArrayList<>(ItemUtil.detailLore(icon));
            itemLore.add("&a$" + MoneyUtil.formatShort(entry.price()));
            String timeLeft = formatTimeLeft(entry.expiresAt());
            if (ownItem) {
                itemLore.add("&8Time left:");
                itemLore.add("&8" + timeLeft);
                itemLore.add(" ");
                itemLore.add("&7Click to return this item.");
            }
            if (shulkerPreviewHint && ItemUtil.isShulker(icon)) {
                itemLore.add("&7Right-Click to preview.");
            }
            meta.setItemName(Text.color(ItemUtil.resolveName(icon)));
            meta.setDisplayName(null);
            meta.setLore(Text.colors(itemLore));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "auction_id"), PersistentDataType.LONG, entry.id());
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "seller_uuid"), PersistentDataType.STRING, entry.seller().toString());
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "seller_name"), PersistentDataType.STRING, entry.sellerName());
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "auction_price"), PersistentDataType.DOUBLE, entry.price());
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "expires_at"), PersistentDataType.LONG, entry.expiresAt());
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack transactionIcon(TransactionEntry entry) {
        ItemStack icon = entry.item().clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            List<String> itemLore;
            if (entry.purchase()) {
                itemLore = List.of("&fYou bought &#00FF42" + entry.counterpartyName() + "&f's " + ItemUtil.resolveName(icon) + " &ffor &#00FF42$" + MoneyUtil.formatShort(entry.price()) + "&f.");
            } else {
                itemLore = List.of("&#00FF42" + entry.counterpartyName() + " &fbought your " + ItemUtil.resolveName(icon) + " &ffor &#00FF42$" + MoneyUtil.formatShort(entry.price()) + "&f.");
            }
            meta.setItemName(Text.color(ItemUtil.resolveName(icon)));
            meta.setDisplayName(null);
            meta.setLore(Text.colors(itemLore));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack sortButton(ViewState state) {
        YamlConfiguration cfg = guiFile("auction-main");
        String selectedPrefix = string(cfg, "buttons.sort.selected-format", "&#00FF8C• %name%");
        String normalPrefix = string(cfg, "buttons.sort.normal-format", "&f• %name%");
        List<String> itemLore = new ArrayList<>();
        for (String sort : SORTS) {
            boolean selected = sort.equalsIgnoreCase(state.sortMode());
            itemLore.add((selected ? selectedPrefix : normalPrefix).replace("%name%", pretty(sort)));
        }
        return taggedAction(simple(material(cfg, "buttons.sort.material", Material.CAULDRON), string(cfg, "buttons.sort.name", "&#00FF8Csᴏʀᴛ"), itemLore), "sort_button");
    }

    private ItemStack filterButton(ViewState state) {
        YamlConfiguration cfg = guiFile("auction-main");
        String selectedPrefix = string(cfg, "buttons.filter.selected-format", "&#00FF8C• %name%");
        String normalPrefix = string(cfg, "buttons.filter.normal-format", "&f• %name%");
        List<String> itemLore = new ArrayList<>();
        for (String filter : FILTERS) {
            boolean selected = filter.equalsIgnoreCase(state.category());
            itemLore.add((selected ? selectedPrefix : normalPrefix).replace("%name%", pretty(filter)));
        }
        return taggedAction(simple(material(cfg, "buttons.filter.material", Material.HOPPER), string(cfg, "buttons.filter.name", "&#00FF8Cғɪʟᴛᴇʀ"), itemLore), "filter_button");
    }

    private ItemStack searchButton(ViewState state) {
        YamlConfiguration cfg = guiFile("auction-main");
        List<String> itemLore = new ArrayList<>(lore(cfg, "buttons.search.lore", List.of("&fClick to search")));
        if (state.query() != null && !state.query().isBlank()) {
            itemLore.add(string(cfg, "buttons.search.lore-current", "&7Current: &#00FF8C%value%").replace("%value%", state.query()));
        }
        return taggedAction(simple(material(cfg, "buttons.search.material", Material.OAK_SIGN), string(cfg, "buttons.search.name", "&#00FF8Csᴇᴀʀᴄʜ"), itemLore), "search_button");
    }

    public boolean consumeSuppressedClose(UUID uniqueId) {
        return suppressedCloses.remove(uniqueId);
    }

    public void suppressNextClose(Player player) {
        suppressedCloses.add(player.getUniqueId());
        closeTargets.remove(player.getUniqueId());
    }

    public void handleManualClose(Player player, Screen screen, ViewState state) {
        CloseTarget target = closeTargets.remove(player.getUniqueId());
        if (target == null) {
            return;
        }
        if (target.screen() == Screen.MAIN) {
            openMainAfterManualClose(player, target.state() == null ? lastMainState(player) : target.state());
        } else if (target.screen() == Screen.YOUR_ITEMS) {
            openYourItemsAfterManualClose(player);
        }
    }

    private void present(Player player, Inventory inventory, CloseTarget target) {
        if (player.getOpenInventory() != null && player.getOpenInventory().getTopInventory() != null && player.getOpenInventory().getTopInventory().getHolder() instanceof AuctionHolder) {
            suppressedCloses.add(player.getUniqueId());
        }
        if (target == null) {
            closeTargets.remove(player.getUniqueId());
        } else {
            closeTargets.put(player.getUniqueId(), target);
        }
        player.openInventory(inventory);
    }

    private void playConfiguredSound(Player player, String soundKey) {
        if (soundKey == null || soundKey.isBlank()) {
            return;
        }
        plugin.getSoundService().play(player, soundKey);
    }

    private void openMainAfterManualClose(Player player, ViewState state) {
        AuctionService.BrowseRequest request = new AuctionService.BrowseRequest(state.query(), state.category(), state.sortMode());
        auctionService.browse(request).thenAccept(list -> FoliaScheduler.runEntity(plugin, player, () -> {
            YamlConfiguration cfg = guiFile("auction-main");
            int size = cfg.getInt("inventory.size", 54);
            int pageSize = Math.min(45, cfg.getIntegerList("inventory.content-slots").size());
            if (pageSize <= 0) {
                pageSize = 45;
            }
            int maxPage = maxPage(list.size(), pageSize);
            int page = clampPage(state.page(), maxPage);
            ViewState applied = state.withPage(page);
            lastMainStates.put(player.getUniqueId(), applied);

            AuctionHolder holder = new AuctionHolder(Screen.MAIN, applied);
            Inventory inventory = Bukkit.createInventory(holder, size, title(cfg, "title", "&8AUCTION (/Page %pages%)", page + 1));
            holder.setInventory(inventory);

            List<Integer> contentSlots = contentSlots(cfg, size);
            List<AuctionEntry> slice = pageSlice(list, page, pageSize);
            for (int i = 0; i < Math.min(contentSlots.size(), slice.size()); i++) {
                inventory.setItem(contentSlots.get(i), saleIcon(slice.get(i), true, false));
            }

            setConfiguredButton(inventory, cfg, "buttons.previous-page", simple(material(cfg, "buttons.previous-page.material", Material.ARROW), string(cfg, "buttons.previous-page.name", "&#00FF42ʙᴀᴄᴋ"), lore(cfg, "buttons.previous-page.lore", List.of("&fClick to go to the previous page"))));
            if (page == 0) {
                inventory.clear(cfg.getInt("buttons.previous-page.slot", 45));
            }
            setConfiguredButton(inventory, cfg, "buttons.sort", sortButton(applied));
            setConfiguredButton(inventory, cfg, "buttons.filter", filterButton(applied));
            setConfiguredButton(inventory, cfg, "buttons.refresh", simple(material(cfg, "buttons.refresh.material", Material.ANVIL), string(cfg, "buttons.refresh.name", "&#00FF42ᴀᴜᴄᴛɪᴏɴ"), lore(cfg, "buttons.refresh.lore", List.of("&fClick to refresh"))));
            setConfiguredButton(inventory, cfg, "buttons.search", searchButton(applied));
            setConfiguredButton(inventory, cfg, "buttons.your-items", simple(material(cfg, "buttons.your-items.material", Material.CHEST), string(cfg, "buttons.your-items.name", "&#00FF42ʏᴏᴜʀ ɪᴛᴇᴍs"), lore(cfg, "buttons.your-items.lore", List.of("&fClick to view the items you have listed."))));
            setConfiguredButton(inventory, cfg, "buttons.next-page", simple(material(cfg, "buttons.next-page.material", Material.ARROW), string(cfg, "buttons.next-page.name", "&#00FF42ɴᴇxᴛ"), lore(cfg, "buttons.next-page.lore", List.of("&fClick to go to the next page"))));
            if (page >= maxPage) {
                inventory.clear(cfg.getInt("buttons.next-page.slot", 53));
            }

            openWithoutSuppress(player, inventory, null);
            plugin.getSoundService().play(player, "back");
        }));
    }

    private void openYourItemsAfterManualClose(Player player) {
        auctionService.getClaims(player.getUniqueId()).thenAccept(claims -> {
            deliverStoredReturns(player, claims);
            auctionService.getOwn(player.getUniqueId()).thenAccept(list -> FoliaScheduler.runEntity(plugin, player, () -> {
                YamlConfiguration cfg = guiFile("your-items");
                AuctionHolder holder = new AuctionHolder(Screen.YOUR_ITEMS, lastMainState(player));
                Inventory inventory = Bukkit.createInventory(holder, cfg.getInt("inventory.size", 27), title(cfg, "title", "&8ᴀᴜᴄᴛɪᴏɴ - ʏᴏᴜʀ ɪᴛᴇᴍs", 1));
                holder.setInventory(inventory);
                int slot = 0;
                for (AuctionEntry entry : list.stream().limit(26).toList()) {
                    inventory.setItem(slot++, saleIcon(entry, false, true));
                }
                int sellSlot = Math.min(slot, 25);
                if (sellSlot <= 25) {
                    inventory.setItem(sellSlot, taggedAction(simple(material(cfg, "buttons.sell.material", Material.MAP), string(cfg, "buttons.sell.name", "&#00FF42Sell Item"), lore(cfg, "buttons.sell.lore", List.of("&fClick to list an item", " ", "&8Or", "&8Hold an item and type", "&8/ah sell (price)", " ", "&8Click your listed items to return them"))), "sell_button"));
                }
                setConfiguredButton(inventory, cfg, "buttons.transactions", simple(material(cfg, "buttons.transactions.material", Material.WRITTEN_BOOK), string(cfg, "buttons.transactions.name", "&#00FF42ᴛʀᴀɴsᴀᴄᴛɪᴏɴs"), lore(cfg, "buttons.transactions.lore", List.of("&fClick to see your purchases and sales."))));
                openWithoutSuppress(player, inventory, new CloseTarget(Screen.MAIN, lastMainState(player)));
                plugin.getSoundService().play(player, "back");
            }));
        });
    }

    private void openWithoutSuppress(Player player, Inventory inventory, CloseTarget target) {
        if (target == null) {
            closeTargets.remove(player.getUniqueId());
        } else {
            closeTargets.put(player.getUniqueId(), target);
        }
        player.openInventory(inventory);
    }
    private YamlConfiguration guiFile(String name) {
        return plugin.getConfigRegistry().gui(name);
    }

    private List<Integer> contentSlots(YamlConfiguration cfg, int size) {
        List<Integer> slots = cfg.getIntegerList("inventory.content-slots");
        if (!slots.isEmpty()) {
            return slots;
        }
        List<Integer> generated = new ArrayList<>();
        for (int i = 0; i < Math.min(45, size); i++) {
            generated.add(i);
        }
        return generated;
    }

    private void fillConfigured(Inventory inventory, YamlConfiguration cfg, String root, Material fallbackMaterial, String fallbackName) {
        ConfigurationSection section = cfg.getConfigurationSection(root);
        if (section == null) {
            return;
        }
        for (int slot : section.getIntegerList("slots")) {
            inventory.setItem(slot, simple(material(cfg, root + ".material", fallbackMaterial), string(cfg, root + ".name", fallbackName), lore(cfg, root + ".lore", List.of())));
        }
    }

    private void setConfiguredButton(Inventory inventory, YamlConfiguration cfg, String root, ItemStack item) {
        inventory.setItem(cfg.getInt(root + ".slot"), item);
    }

    private Material material(YamlConfiguration cfg, String path, Material fallback) {
        String raw = cfg.getString(path);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
        return material == null ? fallback : material;
    }

    private String string(YamlConfiguration cfg, String path, String fallback) {
        return cfg.getString(path, fallback);
    }

    private List<String> lore(YamlConfiguration cfg, String path, List<String> fallback) {
        List<String> list = cfg.getStringList(path);
        return list.isEmpty() ? fallback : list;
    }

    private ItemStack simple(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setItemName(Text.color(name));
            meta.setDisplayName(null);
            if (lore.length > 0) {
                meta.setLore(Text.colors(Arrays.stream(lore).toList()));
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack taggedAction(ItemStack stack, String action) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "menu_action"), PersistentDataType.STRING, action);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack simple(Material material, String name, List<String> lore) {
        return simple(material, name, lore.toArray(String[]::new));
    }

    private String guiTitle(YamlConfiguration cfg, String path, String fallback) {
        return Text.color(string(cfg, path, fallback));
    }

    private String title(YamlConfiguration cfg, String path, String fallback, int page) {
        return Text.color(string(cfg, path, fallback).replace("%pages%", String.valueOf(page)));
    }

    private static int maxPage(int size, int pageSize) {
        return Math.max(0, (size - 1) / pageSize);
    }

    private static int clampPage(int page, int maxPage) {
        return Math.max(0, Math.min(page, maxPage));
    }

    private static <T> List<T> pageSlice(List<T> list, int page, int pageSize) {
        int from = page * pageSize;
        if (from >= list.size()) {
            return List.of();
        }
        int to = Math.min(list.size(), from + pageSize);
        return list.subList(from, to);
    }

    private static String pretty(String raw) {
        String[] split = raw.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : split) {
            if (part.isEmpty()) {
                continue;
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return out.toString().trim();
    }

    public record CloseTarget(Screen screen, ViewState state) {}

    public record ViewState(int page, String query, String category, String sortMode) {
        public ViewState withPage(int newPage) { return new ViewState(newPage, query, category, sortMode); }
        public ViewState withQuery(String newQuery) { return new ViewState(page, newQuery, category, sortMode); }
        public ViewState withCategory(String newCategory) { return new ViewState(page, query, newCategory, sortMode); }
        public ViewState withSort(String newSort) { return new ViewState(page, query, category, newSort); }
    }

    public record PendingListing(ItemStack item, double price, boolean alreadyRemoved) {}
}
