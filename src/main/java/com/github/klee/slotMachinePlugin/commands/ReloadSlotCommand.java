package com.github.klee.slotMachinePlugin.commands;

import com.github.klee.slotMachinePlugin.SlotMachinePlugin;
import com.github.klee.slotMachinePlugin.SlotManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * /slot reload
 * 全設定ファイルを再読み込み
 */
public class ReloadSlotCommand implements CommandExecutor {

    private final SlotMachinePlugin plugin;
    private final SlotManager slotManager;

    public ReloadSlotCommand(SlotMachinePlugin plugin, SlotManager slotManager) {
        this.plugin = plugin;
        this.slotManager = slotManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        plugin.getPluginConfig().reload();
        slotManager.loadAllSlotConfigs();
        plugin.getItemConfigManager().loadAllItemConfigs();

        int count = slotManager.getSlotConfigKeys().size();
        sender.sendMessage("§a設定をリロードしました。 §7(スロット: " + count + "件)");
        return true;
    }
}
