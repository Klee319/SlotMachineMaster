package com.github.klee.slotMachinePlugin.commands;

import com.github.klee.slotMachinePlugin.MachineManager;
import com.github.klee.slotMachinePlugin.MachineManager.MachineData;
import com.github.klee.slotMachinePlugin.SlotMachinePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

/**
 * /deleteslot <machineId>
 * 指定MachineIdのスロットを削除
 */
public class DeleteSlotCommand implements CommandExecutor {

    private final SlotMachinePlugin plugin;

    public DeleteSlotCommand(SlotMachinePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String lbl, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤーのみ実行可能です。");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage("§c/deleteslot <machineId>");
            return true;
        }
        String machineId = args[0];

        // MachineManagerにそのIDがあるかチェック
        if (!MachineManager.hasMachineId(machineId)) {
            player.sendMessage("§cMachineId '" + machineId + "' は存在しません。");
            return true;
        }

        // ボタンブロックからmetadataを外す(見つかれば)
        MachineData md = MachineManager.getMachine(machineId);
        if (md != null) {
            // ワールド & ブロック検索
            var w = Bukkit.getWorld(md.getWorldName());
            if (w != null) {
                Block b = w.getBlockAt(md.getX(), md.getY(), md.getZ());
                if (b.getType() != Material.AIR && b.hasMetadata("MachineId")) {
                    // もし他のSlot名が付いている場合を考慮
                    var metas = b.getMetadata("MachineId");
                    for (MetadataValue mv : metas) {
                        if (mv.asString().equals(machineId)) {
                            b.removeMetadata("MachineId", plugin);
                            player.sendMessage("§eボタンブロックの MachineId '" + machineId + "' メタデータを削除しました。");
                            break;
                        }
                    }
                }
            }
        }

        // MachineManagerから削除
        MachineManager.getAllMachines().remove(machineId);
        MachineManager.saveAllMachines();

        player.sendMessage("§aスロット '" + machineId + "' を削除しました。");
        return true;
    }
}
