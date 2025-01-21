package com.github.klee.slotMachinePlugin.commands;

import com.github.klee.slotMachinePlugin.SlotMachinePlugin;
import com.github.klee.slotMachinePlugin.SlotManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * /slotreload
 * JSONコンフィグを再読み込み
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

        // JSONコンフィグを再読み込み
        slotManager.loadAllSlotConfigs();

        sender.sendMessage("§aスロット設定をリロードしました。");
        return true;
    }
}

