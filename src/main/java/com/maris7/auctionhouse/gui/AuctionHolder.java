package com.maris7.auctionhouse.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class AuctionHolder implements InventoryHolder {

    private final Screen screen;
    private final GuiManager.ViewState state;
    private Inventory inventory;

    public AuctionHolder(Screen screen, GuiManager.ViewState state) {
        this.screen = screen;
        this.state = state;
    }

    public Screen screen() {
        return screen;
    }

    public GuiManager.ViewState state() {
        return state;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
