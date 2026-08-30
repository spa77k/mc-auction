package net.mcauction.auctionhouse.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ItemBuilder {

    private final ItemStack itemStack;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.itemStack = new ItemStack(material);
        this.meta = itemStack.getItemMeta();
    }

    public ItemBuilder(ItemStack base) {
        this.itemStack = base.clone();
        this.meta = itemStack.getItemMeta();
    }

    public ItemBuilder name(String name) {
        meta.setDisplayName(name);
        return this;
    }

    public ItemBuilder lore(List<String> lore) {
        meta.setLore(lore);
        return this;
    }

    public ItemBuilder lore(String... lines) {
        List<String> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(line);
        }
        meta.setLore(lore);
        return this;
    }

    public ItemStack build() {
        itemStack.setItemMeta(meta);
        return itemStack;
    }
}
