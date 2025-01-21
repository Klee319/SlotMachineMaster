package com.github.klee.slotMachinePlugin.utils;

import com.github.klee.slotMachinePlugin.SlotMachinePlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultIntegration {

    private final SlotMachinePlugin plugin;
    private Economy economy;

    public VaultIntegration(SlotMachinePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public double getBalance(OfflinePlayer player) {
        if (economy == null) return 0.0;
        return economy.getBalance(player);
    }

    public void deposit(OfflinePlayer player, double amount) {
        if (economy == null) return;
        economy.depositPlayer(player, amount);
    }

    public void withdraw(OfflinePlayer player, double amount) {
        if (economy == null) return;
        economy.withdrawPlayer(player, amount);
    }
}
