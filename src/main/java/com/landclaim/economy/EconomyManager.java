package com.landclaim.economy;

import com.landclaim.LandClaimPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyManager {

    private final LandClaimPlugin plugin;
    private Economy economy;

    public EconomyManager(LandClaimPlugin plugin) {
        this.plugin = plugin;
        setupEconomy();
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = plugin.getServer()
                .getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return true;
    }

    public boolean hasBalance(OfflinePlayer player, double amount) {
        if (economy == null) return false;
        return economy.has(player, amount);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (economy == null) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (economy == null) return false;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    public double getBalance(OfflinePlayer player) {
        if (economy == null) return 0;
        return economy.getBalance(player);
    }

    public Economy getEconomy() {
        return economy;
    }
}
