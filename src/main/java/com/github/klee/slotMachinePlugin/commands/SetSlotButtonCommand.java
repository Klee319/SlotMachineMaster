package com.github.klee.slotMachinePlugin.commands;

import com.github.klee.slotMachinePlugin.MachineManager;
import com.github.klee.slotMachinePlugin.MachineManager.MachineData;
import com.github.klee.slotMachinePlugin.SlotConfig;
import com.github.klee.slotMachinePlugin.SlotMachinePlugin;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.HashMap;
import java.util.Map;

/**
 * /setslotbutton <machineId> <configPath>
 * 同じ machineId が既にあれば変数を再初期化しない
 */
public class SetSlotButtonCommand implements CommandExecutor {

    private final SlotMachinePlugin plugin;

    public SetSlotButtonCommand(SlotMachinePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤーのみ実行可能です。");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§c/setslotbutton <machineId> <configPath>");
            return true;
        }
        String machineId = args[0];
        String configPath = args[1];

        Block block = player.getTargetBlockExact(5);
        if (block == null) {
            player.sendMessage("§cボタンが見当たりません。");
            return true;
        }
        if (!block.getType().name().endsWith("_BUTTON")) {
            player.sendMessage("§cボタンではありません。");
            return true;
        }
        //ボタンが天井か地面にある場合はリターン
        BlockData bd = block.getBlockData();
        if (!(bd instanceof Switch sw)) {
            return true;
        }
        if (sw.getAttachedFace() == FaceAttachable.AttachedFace.CEILING || sw.getAttachedFace() == FaceAttachable.AttachedFace.FLOOR) {
            player.sendMessage("§cボタンが天井か地面にあるため設定できません。");
            return true;
        }

        MachineData md;
        boolean isNew;
        if (MachineManager.hasMachineId(machineId)) {
            player.sendMessage("§c既存のマシンがあります");
            return true;
        } else {
            // 新規
            md = new MachineData();
            md.setStock(0);
            isNew = true;
        }

        md.setSlotConfigName(configPath);

        World w = block.getWorld();
        md.setWorldName(w.getName());
        md.setX(block.getX());
        md.setY(block.getY());
        md.setZ(block.getZ());

        // 新規の場合のみ variables を初期化
        if (isNew) {
            SlotConfig cfg = plugin.getSlotManager().getSlotConfig(configPath);
            if (cfg != null && cfg.getVariables() != null) {
                Map<String, Double> varMap = new HashMap<>();
                cfg.getVariables().forEach(vd -> {
                    varMap.put(vd.getVarName(), vd.getInitialValue());
                });
                md.setVariables(varMap);
            }
        }

        MachineManager.setMachineData(machineId, md);
        block.setMetadata("MachineId", new FixedMetadataValue(plugin, machineId));
        MachineManager.saveAllMachines();

        player.sendMessage("§aボタンに machineId='" + machineId + "', config='" + configPath + "' を設定しました。");
        return true;
    }
}
