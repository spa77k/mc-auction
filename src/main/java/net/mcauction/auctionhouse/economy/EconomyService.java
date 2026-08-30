package net.mcauction.auctionhouse.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyService {

    private Economy economy;

    public boolean setup(Plugin plugin) {
        RegisteredServiceProvider<Economy> provider =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (provider == null) {
            return false;
        }
        economy = provider.getProvider();
        return economy != null;
    }

    public boolean isReady() {
        return economy != null;
    }

    public boolean has(OfflinePlayer player, double amount) {
        return economy.has(player, amount);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    public String format(double amount) {
        return economy.format(amount);
    }

    public String format(int amount) {
        return economy.format(amount);
    }
}
