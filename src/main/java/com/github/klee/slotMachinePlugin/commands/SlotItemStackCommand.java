package com.github.klee.slotMachinePlugin.commands;

import com.github.klee.slotMachinePlugin.SlotMachinePlugin;
import com.github.klee.slotMachinePlugin.utils.ItemStackUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * /slotItemStack
 * プレイヤーが手に持っているアイテムの詳細をコンソール出力
 */
public class SlotItemStackCommand implements CommandExecutor {

    private final SlotMachinePlugin plugin;

    public SlotItemStackCommand(SlotMachinePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // プレイヤーのみ
        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤーのみ実行可能です。");
            return true;
        }

        // メインハンドのアイテム取得
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand.getType().isAir()) {
            player.sendMessage("§c手にアイテムを持っていません。");
            return true;
        } else {
            try {
                String encode = ItemStackUtil.itemToBase64(inHand);
                plugin.getLogger().info("[SlotItemStack]: " + encode);
                player.sendMessage("§aコンソールに出力しました！");
            } catch (Exception ex) {
                player.sendMessage("§cシリアライズ失敗: " + ex.getMessage());
            }
        }

        // 返答
        player.sendMessage("§aコンソールに出力しました。");
        return true;
    }
}
