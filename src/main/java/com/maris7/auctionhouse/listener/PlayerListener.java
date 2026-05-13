package com.maris7.auctionhouse.listener;

import com.maris7.auctionhouse.gui.GuiManager;
import com.maris7.auctionhouse.service.SignInputService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerListener implements Listener {

    private final GuiManager guiManager;
    private final SignInputService signInputService;

    public PlayerListener(GuiManager guiManager, SignInputService signInputService) {
        this.guiManager = guiManager;
        this.signInputService = signInputService;
    }


    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        guiManager.clearPending(event.getPlayer().getUniqueId());
        signInputService.clear(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        if (!signInputService.matches(event.getPlayer().getUniqueId(), event.getBlock().getLocation())) {
            return;
        }
        signInputService.complete(event.getPlayer(), event.getLines());
    }
}
