package com.maris7.auctionhouse;

import com.github.retrooper.packetevents.PacketEvents;
import com.maris7.auctionhouse.command.AuctionCommand;
import com.maris7.auctionhouse.command.AuctionReloadCommand;
import com.maris7.auctionhouse.config.ConfigRegistry;
import com.maris7.auctionhouse.db.DatabaseManager;
import com.maris7.auctionhouse.gui.AuctionHolder;
import com.maris7.auctionhouse.gui.GuiManager;
import com.maris7.auctionhouse.listener.GuiListener;
import com.maris7.auctionhouse.listener.PlayerListener;
import com.maris7.auctionhouse.service.AuctionService;
import com.maris7.auctionhouse.service.SignInputService;
import com.maris7.auctionhouse.service.SoundService;
import com.maris7.auctionhouse.util.FoliaScheduler;
import com.maris7.auctionhouse.util.Text;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class MarisAuctionPlugin extends JavaPlugin {

    private static MarisAuctionPlugin instance;

    private ConfigRegistry configRegistry;
    private DatabaseManager databaseManager;
    private AuctionService auctionService;
    private GuiManager guiManager;
    private SignInputService signInputService;
    private SoundService soundService;
    private Economy economy;
    private FoliaScheduler.TaskHandle mysqlTimeoutLogTask;
    private FoliaScheduler.TaskHandle expiredSweepTask;
    private SettingsHook settingsHook;

    public static MarisAuctionPlugin getInstance() {
        return instance;
    }

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        this.configRegistry = new ConfigRegistry(this);
        this.configRegistry.loadAll();

        if (!validateDependencies()) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        hookVault();

        this.settingsHook = new SettingsHook(this);
        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.start();
        this.auctionService = new AuctionService(this, databaseManager);
        this.signInputService = new SignInputService(this, PacketEvents.getAPI() != null);
        this.soundService = new SoundService(this);
        this.guiManager = new GuiManager(this, auctionService, signInputService);
        expiredSweepTask = FoliaScheduler.runAsyncTimer(this, () -> {
            if (auctionService != null && databaseManager.isAuctionAvailable()) {
                auctionService.sweepExpiredAsync();
            }
        }, 20L * 60L, 20L * 300L);

        Bukkit.getPluginManager().registerEvents(new GuiListener(guiManager), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(guiManager, signInputService), this);

        AuctionCommand auctionCommand = new AuctionCommand(this, auctionService, guiManager);
        registerCommand("ah", auctionCommand);

        PluginCommand reload = getCommand("ahreload");
        if (reload != null) {
            reload.setExecutor(new AuctionReloadCommand(this));
        }
    }

    @Override
    public void onDisable() {
        if (mysqlTimeoutLogTask != null) {
            mysqlTimeoutLogTask.cancel();
            mysqlTimeoutLogTask = null;
        }
        if (expiredSweepTask != null) {
            expiredSweepTask.cancel();
            expiredSweepTask = null;
        }
        if (guiManager != null) {
            guiManager.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
    }

    private boolean validateDependencies() {
        Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
        Plugin nbtApi = Bukkit.getPluginManager().getPlugin("NBTAPI");
        Plugin packetEvents = Bukkit.getPluginManager().getPlugin("PacketEvents");

        if (vault == null) {
            getLogger().severe("Vault is required.");
            return false;
        }
        if (nbtApi == null) {
            getLogger().severe("NBTAPI is required.");
            return false;
        }
        if (packetEvents == null || PacketEvents.getAPI() == null) {
            getLogger().severe("PacketEvents is required.");
            return false;
        }
        return true;
    }

    private void registerCommand(String name, AuctionCommand executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    public void reloadPlugin() {
        configRegistry.loadAll();
    }

    private void hookVault() {
        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (provider == null || provider.getProvider() == null) {
            throw new IllegalStateException("Vault economy provider not found");
        }
        this.economy = provider.getProvider();
    }

    public void handleMysqlTimeout() {
        FoliaScheduler.runGlobal(this, () -> {
            if (mysqlTimeoutLogTask == null) {
                logMysqlTimeout();
                mysqlTimeoutLogTask = FoliaScheduler.runGlobalTimer(this, this::logMysqlTimeout, 20L * 300L, 20L * 300L);
            }
            if (signInputService != null) {
                signInputService.clearAll();
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                FoliaScheduler.runEntity(this, player, () -> {
                    InventoryView view = player.getOpenInventory();
                    if (view != null && view.getTopInventory() != null && view.getTopInventory().getHolder() instanceof AuctionHolder) {
                        player.closeInventory();
                    }
                });
            }
        });
    }

    private void logMysqlTimeout() {
        Bukkit.getConsoleSender().sendMessage(Text.color("&cMySQL Timed Out. Please restart them"));
    }

    public ConfigRegistry getConfigRegistry() {
        return configRegistry;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public AuctionService getAuctionService() {
        return auctionService;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public SignInputService getSignInputService() {
        return signInputService;
    }

    public SoundService getSoundService() {
        return soundService;
    }

    public boolean isAuctionEnabled(java.util.UUID uuid) {
        return settingsHook == null || settingsHook.isEnabled(uuid, "AUCTION_TOGGLE", true);
    }

    public boolean isFastBuyEnabled(java.util.UUID uuid) {
        return settingsHook != null && settingsHook.isEnabled(uuid, "AUCTION_FAST_BUY", false);
    }

    public boolean isFastSellEnabled(java.util.UUID uuid) {
        return settingsHook != null && settingsHook.isEnabled(uuid, "AUCTION_FAST_SELL", false);
    }

    public Economy getEconomy() {
        return economy;
    }

}

