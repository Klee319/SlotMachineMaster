package com.github.klee.slotMachinePlugin;

import com.github.klee.slotMachinePlugin.MachineManager.MachineData;
import com.github.klee.slotMachinePlugin.SlotConfig.Reward;
import com.github.klee.slotMachinePlugin.utils.ExpressionParser;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;


public class SlotMachineListener implements Listener {

    private static final Set<Material> BUTTONS = Set.of(
            Material.STONE_BUTTON, Material.OAK_BUTTON, Material.SPRUCE_BUTTON, Material.BIRCH_BUTTON,
            Material.JUNGLE_BUTTON, Material.ACACIA_BUTTON, Material.DARK_OAK_BUTTON,
            Material.CRIMSON_BUTTON, Material.WARPED_BUTTON, Material.MANGROVE_BUTTON,
            Material.BAMBOO_BUTTON, Material.CHERRY_BUTTON
    );
    // 額縁オフセット
    private static final Map<String, OffsetDefinition> OFFSET_MAP = new HashMap<>();

    static {
        OFFSET_MAP.put("WALL_NORTH", new OffsetDefinition(
                List.of(new BlockPos(-1, 1, 1), new BlockPos(0, 1, 1), new BlockPos(1, 1, 1))
        ));
        OFFSET_MAP.put("WALL_SOUTH", new OffsetDefinition(
                List.of(new BlockPos(1, 1, -1), new BlockPos(0, 1, -1), new BlockPos(-1, 1, -1))
        ));
        OFFSET_MAP.put("WALL_EAST", new OffsetDefinition(
                List.of(new BlockPos(-1, 1, -1), new BlockPos(-1, 1, 0), new BlockPos(-1, 1, 1))
        ));
        OFFSET_MAP.put("WALL_WEST", new OffsetDefinition(
                List.of(new BlockPos(1, 1, 1), new BlockPos(1, 1, 0), new BlockPos(1, 1, -1))

        ));
    }

    private final double defaultVolume = 0.5F;
    private final double defaultPitch = 1.0;
    private final double defaultRadius = 10.0;
    private final SlotMachinePlugin plugin;
    private final Set<UUID> spinningPlayers = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, UUID> busyMachineMap = Collections.synchronizedMap(new HashMap<>());

    public SlotMachineListener(SlotMachinePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;
        if (!BUTTONS.contains(event.getClickedBlock().getType())) return;
        List<MetadataValue> metas = event.getClickedBlock().getMetadata("MachineId");
        if (metas.isEmpty()) return;
        String machineId = metas.get(0).asString();

        var machine = MachineManager.getMachine(machineId);
        if (machine == null) return;

        Player player = event.getPlayer();

        // 多重起動
        if (spinningPlayers.contains(player.getUniqueId())) {
            player.sendMessage("§cすでにスロットを回しています。");
            return;
        }
        if (busyMachineMap.containsKey(machineId)) {
            player.sendMessage("§cこのスロットは回転中です。");
            return;
        }

        var config = plugin.getSlotManager().getSlotConfig(machine.getSlotConfigName());
        if (config == null) {
            player.sendMessage("§cスロット設定が見つかりません: " + machine.getSlotConfigName());
            return;
        }
        if (!checkFrame(event.getClickedBlock())) {
            player.sendMessage("§c額縁が正しく配置されていません。");
            return;
        }
        startSlot(player, event.getClickedBlock(), machineId, machine, config);
    }

    private boolean checkFrame(Block buttonBlock) {
        // 1) ボタンのBlockDataが Switch であることを確認
        BlockData bd = buttonBlock.getBlockData();
        if (!(bd instanceof Switch sw)) {
            return false;
        }

        // 2) ボタンが取り付けられている面(CEILING, FLOOR, WALL) & 向き
        FaceAttachable.AttachedFace face = sw.getAttachedFace();
        BlockFace facing = sw.getFacing();



        // 3) OFFSET_MAP から、(face + "_" + facing) で対応する OffsetDefinition を取得
        String key = face + "_" + facing;
        OffsetDefinition offDef = OFFSET_MAP.get(key);
        if (offDef == null) {
            // 未対応の向き
            return false;
        }

        // 4) 定義されたオフセットそれぞれに対し、
        //    「同じ面に取り付けられた額縁が最低1つあるか」を確認
        for (BlockPos off : offDef.reelOffsets) {
            // このオフセットのブロック位置
            Block targetBlock = buttonBlock.getRelative(off.dx, off.dy, off.dz);

            // 近辺のItemFrameを検索 (±0.1ブロックの範囲)
            List<ItemFrame> framesFound = new ArrayList<>();
            targetBlock.getWorld().getNearbyEntities(targetBlock.getBoundingBox().expand(0.1),
                    e -> e instanceof ItemFrame).forEach(e -> framesFound.add((ItemFrame) e));

            // 「同じ面にある」 = frame.getFacing() == requiredFrameFacing
            // このオフセット位置に一つでも 同じ面向きのフレームがあればOK
            boolean foundFrameOnThisOffset = false;
            for (ItemFrame f : framesFound) {
                if (f.getFacing() == facing) {
                    foundFrameOnThisOffset = true;
                    break;
                }
            }

            // もし一つも見つからなければ、このオフセットに必要な額縁が無い
            if (!foundFrameOnThisOffset) {
                return false;
            }
        }

        // すべてのオフセットについて「同じ面の額縁」を1つ以上確認できたらOK
        return true;
    }

    private void startSlot(Player player, Block buttonBlock,
                           String machineId, MachineManager.MachineData machine,
                           SlotConfig config) {

        // 1) 金銭コスト
        double cost = parseDoubleExpression(config.getSpinCost(), machine);
        boolean needMoney = (cost > 0);

        // 2) アイテムコスト
        SlotConfig.ItemCost itemCost = config.getItemCost(); // { name, amount }
        boolean needItem = (itemCost != null && itemCost.getName() != null && !itemCost.getName().isEmpty() && itemCost.getAmount() > 0);

        // 2a) 両方設定されているなら両方必要
        //     片方のみ設定なら、その要件だけでOK

        // 3) チェック: 金が足りるか？
        if (needMoney) {
            if (plugin.getVaultIntegration() == null) {
                player.sendMessage("§cVault連携がありません。お金コストを使用できません。");
                return;
            }
            double bal = plugin.getVaultIntegration().getBalance(player);
            if (bal < cost) {
                player.sendMessage("§cお金が足りません。: ¥" + (int)cost);
                return;
            }
        }

        // 4) チェック: アイテムが足りるか？
        if (needItem) {
            // itemCostの name がMaterialか itemConfigで宣言した変数か判断
            String rawName = itemCost.getName();
            int requiredAmt = itemCost.getAmount();

            // 例: plugin.getItemConfigManager().getItemByKey(rawName) でItemStackを取得 or null
            ItemStack custom = plugin.getItemConfigManager().getItemByKey(rawName);
            if (custom != null) {
                // カスタムアイテム => compare NBT含むかは実装次第
                int invCount = countItemStack(player, custom);
                if (invCount < requiredAmt) {
                    player.sendMessage("§c必要アイテムが足りません: " + rawName + " x" + requiredAmt);
                    return;
                }
            } else {
                // fallback: Material
                Material mat = Material.matchMaterial(rawName);
                if (mat == null) {
                    player.sendMessage("§citemCostの名前が無効: " + rawName);
                    return;
                }
                int invCount = countMaterial(player, mat);
                if (invCount < requiredAmt) {
                    player.sendMessage("§c必要アイテムが足りません: " + mat.name() + " x" + requiredAmt);
                    return;
                }
            }
        }

        // 5) コストを引く (両方あれば両方)
        if (needMoney) {
            plugin.getVaultIntegration().withdraw(player, cost);
            player.sendMessage("§e" + (int)cost + "¥を支払いました。");
        }
        if (needItem) {
            String rawName = itemCost.getName();
            int requiredAmt = itemCost.getAmount();

            ItemStack custom = plugin.getItemConfigManager().getItemByKey(rawName);
            if (custom != null) {
                removeItemStack(player, custom, requiredAmt);
                player.sendMessage("§e"+ rawName +" x"+ requiredAmt +" を支払いました。");
            } else {
                Material mat = Material.matchMaterial(rawName);
                if (mat == null) {
                    player.sendMessage("§cアイテムコストの名前が無効(削除失敗): " + rawName);
                } else {
                    removeMaterial(player, mat, requiredAmt);
                    player.sendMessage("§e"+ mat.name() +" x"+ requiredAmt +" を支払いました。");
                }
            }
        }


        // 最上位eventは毎回実行
        runEventsIfNeeded(machine, config, player, machineId);

        spinningPlayers.add(player.getUniqueId());
        busyMachineMap.put(machineId, player.getUniqueId());


// startSound
        var sParam = config.getDefaultSoundSettings().getStartSound();
        if (sParam != null
                && sParam.getType() != null) {
            double vol = (sParam.getVolume() > 0) ? sParam.getVolume() : defaultVolume;
            double pitch = (sParam.getPitch() > 0) ? sParam.getPitch() : defaultPitch;
            double rad = ((sParam.getRadius() > 0) || (sParam.getRadius() == -1)) ? sParam.getRadius() : defaultRadius;
            playSoundWithRadius(player, buttonBlock, sParam.getType(), vol, pitch, rad);
        }

        BlockData bd = buttonBlock.getBlockData();
        if (!(bd instanceof Switch sw)) {
            return;
        }
        FaceAttachable.AttachedFace face = sw.getAttachedFace();
        BlockFace facing = sw.getFacing();
        String key = face + "_" + facing;
        OffsetDefinition offDef = OFFSET_MAP.get(key);
        if (offDef == null) {
            endSpin(player.getUniqueId(), machineId);
            return;
        }

        // 額縁3
        var frames = findFrames(buttonBlock, offDef.reelOffsets);
        if (frames.size() < 3) {
            endSpin(player.getUniqueId(), machineId);
            return;
        }

        // shuffleTime, shuffleSpeed も式対応
        double shuffleTime = parseDoubleExpression(config.getShuffleTime(), machine);
        double shuffleSpeed = parseDoubleExpression(config.getShuffleSpeed(), machine);
        if (shuffleSpeed <= 0) shuffleSpeed = 1.0;
        long shuffleInterval = (long) (4L / shuffleSpeed);
        long totalShuffleTicks = (long) (shuffleTime * 20);

        // pattern抽選
        var ptn = drawPatternOrMiss(config, machine);
        boolean isMiss = (ptn == null);
        boolean useRotatingSound;
        List<String> finalItems;
        if (!isMiss) {
            finalItems = ptn.getItems();
        } else {
            // ハズレ
            finalItems = pickNonWinning3(collectAllItemsFromPatterns(config), config);
        }

        // シャッフルプール
        List<String> shufflePool = collectAllItemsFromPatterns(config);

// rotatingSound
        if (config.getDefaultSoundSettings() != null
                && config.getDefaultSoundSettings().getRotatingSound() != null
                && config.getDefaultSoundSettings().getRotatingSound().getType() != null
                && !config.getDefaultSoundSettings().getRotatingSound().getType().isEmpty()) {
            useRotatingSound = true;
            // フレームの音を消す
            for (ItemFrame f : frames) {
                f.setSilent(true);
            }
        } else {
            useRotatingSound = false;
        }

        List<Integer> tasks = new ArrayList<>();
        for (ItemFrame f : frames) {
            int tid = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                // シャッフル中のアイテムを変数 or Material のどちらかで表示
                String itemName = pickRandom(shufflePool);

                ItemStack customItem = plugin.getItemConfigManager().getItemByKey(itemName);
                if (customItem != null) {
                    f.setItem(customItem.clone());
                } else {
                    Material mat = Material.matchMaterial(itemName);
                    if (mat == null) mat = Material.STONE;
                    f.setItem(new ItemStack(mat));
                }

// シャッフル中に音再生
                if (useRotatingSound) {
                    var sParams = config.getDefaultSoundSettings().getRotatingSound();
                    String soundKey = sParams.getType();
                    double vol = (sParams.getVolume() > 0) ? sParams.getVolume() : defaultVolume;
                    double pitch = (sParams.getPitch() > 0) ? sParams.getPitch() : defaultPitch;
                    double rad = (sParams.getRadius() != 0) ? sParams.getRadius() : defaultRadius;
                    playSoundWithRadius(player, buttonBlock, soundKey, vol, pitch, rad);
                }
                List<SlotConfig.ParticleSetting> dps = config.getDefaultParticleSettings();
                if (dps != null && !dps.isEmpty()) {
                    for (SlotConfig.ParticleSetting ps : dps) {
                        spawnWinParticles(buttonBlock, ps, player);
                    }
                }

            }, 0L, shuffleInterval);
            tasks.add(tid);
        }

        // 時間後 => 順番停止
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            stopFramesOneByOne(player, frames, config, finalItems, tasks, isMiss ? null :  machineId, machine, buttonBlock, useRotatingSound);
        }, totalShuffleTicks);
    }

    private int countMaterial(Player player, Material mat) {
        int count=0;
        for(ItemStack invItem : player.getInventory().getContents()){
            if(invItem!=null && invItem.getType()==mat){
                count+= invItem.getAmount();
            }
        }
        return count;
    }

    /**
     * 指定数だけ Material mat を削除
     */
    private void removeMaterial(Player player, Material mat, int required) {
        int remain= required;
        for(int i=0; i< player.getInventory().getSize(); i++){
            ItemStack slot = player.getInventory().getItem(i);
            if(slot!=null && slot.getType()==mat){
                if(slot.getAmount() <= remain){
                    remain -= slot.getAmount();
                    player.getInventory().setItem(i,null);
                    if(remain<=0) break;
                } else {
                    slot.setAmount(slot.getAmount()- remain);
                    break;
                }
            }
        }
    }
    private int countItemStack(Player player, ItemStack custom) {
        int count=0;
        for(ItemStack invItem : player.getInventory().getContents()){
            if(invItem!=null && invItem.isSimilar(custom)){
                count+= invItem.getAmount();
            }
        }
        return count;
    }
    private void removeItemStack(Player player, ItemStack custom, int required) {
        int remain= required;
        for(int i=0; i< player.getInventory().getSize(); i++){
            ItemStack slot = player.getInventory().getItem(i);
            if(slot!=null && slot.isSimilar(custom)){
                if(slot.getAmount() <= remain){
                    remain -= slot.getAmount();
                    player.getInventory().setItem(i,null);
                    if(remain<=0) break;
                } else {
                    slot.setAmount(slot.getAmount()- remain);
                    break;
                }
            }
        }
    }
    private void stopFramesOneByOne(Player player,
                                    List<ItemFrame> frames,
                                    SlotConfig config,
                                    List<String> finalItems,
                                    List<Integer> shuffleTasks,
                                    String machineId,
                                    MachineManager.MachineData machine,
                                    Block buttonBlock,
                                    boolean useRotatingSound) {

        double spinSpeed = parseDoubleExpression(config.getSpinSpeed(), machine);
        if (spinSpeed <= 0) spinSpeed = 1.0;
        long baseDelay = (long) (10L / spinSpeed);

        long delay = 0;
        for (int i = 0; i < frames.size(); i++) {
            final int idx = i;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                // シャッフル停止
                plugin.getServer().getScheduler().cancelTask(shuffleTasks.get(idx));

                ItemFrame frame = frames.get(idx);

                String itemName = (idx < finalItems.size()) ? finalItems.get(idx) : "STONE";
                ItemStack custom = plugin.getItemConfigManager().getItemByKey(itemName);
                if (custom != null) {
                    ItemStack clone = custom.clone();
                    setSlotMachineItemKey(clone, itemName);
                    frame.setItem(clone);
                } else {
                    Material mat = Material.matchMaterial(itemName);
                    if (mat == null) mat = Material.STONE;
                    ItemStack matStack = new ItemStack(mat);
                    setSlotMachineItemKey(matStack, itemName);
                    frame.setItem(matStack);
                }

// reelStopSound
                if (config.getDefaultSoundSettings() != null
                        && config.getDefaultSoundSettings().getReelStopSound() != null) {
                    var ss = config.getDefaultSoundSettings().getReelStopSound();
                    String soundKey = ss.getType();
                    double vol = (ss.getVolume() > 0) ? ss.getVolume() : defaultVolume;
                    double pitch = (ss.getPitch() > 0) ? ss.getPitch() : defaultPitch;
                    double rad = (ss.getRadius() != 0) ? ss.getRadius() : defaultRadius;
                    playSoundWithRadius(player, buttonBlock, soundKey, vol, pitch, rad);
                }


                if (idx == frames.size() - 1) {
                    // finish
                    finishSlot(player, machineId, machine, config, buttonBlock);
                    endSpin(player.getUniqueId(), machineId);

                    // ★ 回転終了後に setSilent(false) して通常音に戻す
                    if (useRotatingSound) {
                        for (ItemFrame ff : frames) {
                            ff.setSilent(false);
                        }
                    }
                }
            }, delay);
            delay += baseDelay;
        }
    }


    private void finishSlot(Player player,
                            String machineId,
                            MachineData machine,
                            SlotConfig config,
                            Block buttonBlock) {
        var frames = findFrames(buttonBlock, findOffsetDefinition(buttonBlock));
        if (frames.size() < 3) {
            doLose(player, machineId, machine, config);
            updateSignStock(machineId, buttonBlock);
            return;
        }

        List<String> actual = new ArrayList<>();
        for (ItemFrame f : frames) {
            ItemStack stack = f.getItem();
            // ① カスタムキー取得
            String varName = getSlotMachineItemKey(stack);
            if (varName != null) {
                actual.add(varName);
            } else {
                // ② fallback (Material名)
                Material mm = stack.getType();
                actual.add(mm.name());
            }
        }
        var realPat = checkWhichPattern(config, actual);
        if (realPat == null) {
            doLose(player, machineId, machine, config);
        } else {
            doWin(player, machineId, machine, realPat);
        }
        double spinCost = parseDoubleExpression(config.getSpinCost(), machine);
        SlotMachinePlugin.addProfit(player.getUniqueId(), machineId, -spinCost);
        updateSignStock(machineId, buttonBlock);
    }

    // ★ 新規追加
// SlotMachineListener や共通ユーティリティ等で:
    private String translateColorAndNewline(String text, MachineManager.MachineData machine) {
        if (text == null) return "";

        // \n → 改行
        text = text.replace("\\n", "\n");
        // <[varName]> → varValue
        if (machine != null && machine.getVariables() != null) {
            for (Map.Entry<String, Double> e : machine.getVariables().entrySet()) {
                String placeholder = "<" + e.getKey() + ">";
                text = text.replace(placeholder, String.valueOf(e.getValue()));
            }
        }

        // & → カラーコード
        return ChatColor.translateAlternateColorCodes('&', text);
    }


    // ★ 新規追加
    // ★ CHANGED CODE START
    private void spawnWinParticles(Block buttonBlock, SlotConfig.ParticleSetting ps, Player player) {
        // "particle" が空なら終了
        if (ps.getParticle().isEmpty()) {
            return;
        }

        // 1) ボタンがSwitch(ボタン)かどうか
        BlockData bd = buttonBlock.getBlockData();
        if (!(bd instanceof Switch sw)) {
            return;
        }

        // 2) ボタン取り付け面(CEILING,FLOOR,WALL)とfacing
        FaceAttachable.AttachedFace face = sw.getAttachedFace();
        BlockFace facing = sw.getFacing();
        String key = face + "_" + facing;

        // 3) OFFSET_MAPから該当オフセット定義を取得
        OffsetDefinition offDef = OFFSET_MAP.get(key);
        if (offDef == null) {
            // 定義されていない向き
            return;
        }

        // 4) パーティクル設定 (Particle, count, offset, speed, color etc.)
        Particle particle;
        try {
            particle = Particle.valueOf(ps.getParticle().toUpperCase());
        } catch (Exception ex) {
            plugin.getLogger().warning("[spawnWinParticles] Particle設定が不正: " + ex.getMessage());
            return;
        }
        int count = ps.getCount();
        double speed = ps.getSpeed();
        double offX = transformOffset(ps.getOffset(), facing)[0];
        double offY = transformOffset(ps.getOffset(), facing)[1];
        double offZ = transformOffset(ps.getOffset(), facing)[2];

        if (Objects.equals(ps.getPoint(), "button") || Objects.equals(ps.getPoint(), "BUTTON")|| Objects.equals(ps.getPoint(), "Button")) {
            // ボタン中心でパーティクルを発生
            Location baseLoc = buttonBlock.getLocation().add(0.5, 0.5, 0.5);
            // REDSTONE の色指定対応
            if (particle == Particle.DUST && ps.getColor() != null) {
                float r = (float) ps.getColor()[0];
                float g = (float) ps.getColor()[1];
                float b = (float) ps.getColor()[2];
                Particle.DustOptions dust = new Particle.DustOptions(
                        Color.fromRGB((int) (r * 255), (int) (g * 255), (int) (b * 255)), 1.0F
                );
                player.getWorld().spawnParticle(particle,
                        baseLoc.getX(), baseLoc.getY(), baseLoc.getZ(),
                        count, offX, offY, offZ, speed, dust
                );
            } else {
                // 通常パーティクル
                player.getWorld().spawnParticle(particle,
                        baseLoc.getX(), baseLoc.getY(), baseLoc.getZ(),
                        count, offX, offY, offZ, speed
                );
            }
        } else if(Objects.equals(ps.getPoint(), "frame")||Objects.equals(ps.getPoint(), "FRAME")||Objects.equals(ps.getPoint(), "Frame")) {        // 5) 各オフセット位置のブロック中心付近でパーティクルを発生
            for (BlockPos offset : offDef.reelOffsets) {
                // ボタン基準に相対ブロックを取得
                Block target = buttonBlock.getRelative(offset.dx, offset.dy, offset.dz);

                // ブロックの中心位置 (x+0.5, y+0.5, z+0.5)
                Location baseLoc = target.getLocation().add(0.5, 0.5, 0.5);

                // REDSTONE の色指定対応
                if (particle == Particle.DUST && ps.getColor() != null) {
                    float r = (float) ps.getColor()[0];
                    float g = (float) ps.getColor()[1];
                    float b = (float) ps.getColor()[2];
                    Particle.DustOptions dust = new Particle.DustOptions(
                            Color.fromRGB((int) (r * 255), (int) (g * 255), (int) (b * 255)), 1.0F
                    );
                    player.getWorld().spawnParticle(particle,
                            baseLoc.getX(), baseLoc.getY(), baseLoc.getZ(),
                            count, offX, offY, offZ, speed, dust
                    );
                } else {
                    // 通常パーティクル
                    player.getWorld().spawnParticle(particle,
                            baseLoc.getX(), baseLoc.getY(), baseLoc.getZ(),
                            count, offX, offY, offZ, speed
                    );
                }
            }
        }

    }
// ★ CHANGED CODE END

    private Double[] transformOffset(double[] offset,
                                     BlockFace face) {
        // 回転結果
        double rx = 0, ry = 0, rz = 0;
        double lx = offset[0], ly = offset[1], lz = offset[2];
        switch (face) {
            case NORTH -> {
                rx = -lx;       // x→world x
                ry = ly;       // y→world y
                rz = lz;       // z→world z
            }
            case SOUTH -> {
                rx = lx;
                ry = ly;
                rz = -lz;
            }
            case EAST -> {
                rx = lz;
                ry = ly;
                rz = -lx;
            }
            case WEST -> {
                rx = lz;
                ry = ly;
                rz = lx;
            }
            default -> {
                // 想定外
            }
        }


        // 戻り値 ダブル型で返す
        return new Double[]{rx, ry, rz};
    }

    private void doLose(Player player,
                        String machineId,
                        MachineData machine,
                        SlotConfig config) {
        // ★ カラー＆改行対応
        if (config.getLoseMessage() != null && !config.getLoseMessage().isEmpty()) {
            String msg = translateColorAndNewline(config.getLoseMessage(), machine);
            player.sendMessage(msg);
        }
        if (config.getDefaultSoundSettings() != null
                && config.getDefaultSoundSettings().getEndLoseSound() != null) {
            var sParam = config.getDefaultSoundSettings().getEndLoseSound();
            String soundKey = sParam.getType();
            double vol = (sParam.getVolume() > 0) ? sParam.getVolume() : defaultVolume;
            double pitch = (sParam.getPitch() > 0) ? sParam.getPitch() : defaultPitch;
            double rad = (sParam.getRadius() != 0) ? sParam.getRadius() : defaultRadius;
            World machineWorld = Bukkit.getWorld(machine.getWorldName());
            Location buttonLoc = new Location(machineWorld, machine.getX(), machine.getY(), machine.getZ());
            Block buttonBlock = buttonLoc.getBlock();
            playSoundWithRadius(player, buttonBlock, soundKey, vol, pitch, rad);
        }

        applyLoseStockOperation(machineId, config);
    }


    private void doWin(Player player,
                       String machineId,
                       MachineData machine,
                       SlotConfig.PatternConfig pattern) {
        // 報酬計算
        List<SlotConfig.Reward> rewardList = pattern.getRewards();
        double moneyWon = 0;
        if (rewardList != null) {
            for (SlotConfig.Reward rw : rewardList) {
                if (rw != null && rw.getType().equalsIgnoreCase("money")) {
                    moneyWon = parseRewardValue(rw.getValue(), machine);
                }
                giveReward(player, machine, rw,machineId);
            }
        }

        World machineWorld = Bukkit.getWorld(machine.getWorldName());
        Location buttonLoc = new Location(machineWorld, machine.getX(), machine.getY(), machine.getZ());
        Block buttonBlock = buttonLoc.getBlock();


        // (2) pattern.winMessage で "<>" を moneyWon に置き換え
        if (pattern.getWinMessage() != null && !pattern.getWinMessage().isEmpty()) {
            String localMsg = pattern.getWinMessage().replace("<profit>", String.valueOf(moneyWon));
            // カラーコード対応など行うなら:
            localMsg = translateColorAndNewline(localMsg, machine);
            player.sendMessage(localMsg);
        }

        SlotConfig.PatternConfig.PatternSoundParam patternSound = pattern.getPatternSound();
        // (3) pattern.winSound
        if (patternSound != null && !patternSound.getType().isEmpty()) {
            double vol = (patternSound.getVolume() > 0) ? patternSound.getVolume() : defaultVolume;
            double pitch = (patternSound.getPitch() > 0) ? patternSound.getPitch() : defaultPitch;
            double rad = (patternSound.getRadius() != 0) ? patternSound.getRadius() : defaultRadius;

            playSoundWithRadius(player, buttonBlock, patternSound.getType(), vol, pitch, rad);
        }
        // (6) broadcast設定チェック
        SlotConfig.BroadcastSettings bs = pattern.getBroadcastSettings();
        if (bs != null) {
            broadcastWinToAll(player, machine, bs, moneyWon);
        }

        applyWinStockOperation(machineId, pattern);

        // pattern event
        if (pattern.getEvent() != null && !pattern.getEvent().isEmpty()) {
            runPatternEventsIfNeeded(machine, pattern.getEvent(), player,machineId);
        }
        // changeVars


        // ★ パーティクル生成を追加(後述)
        if (pattern.getParticleSettings() != null && !pattern.getParticleSettings().isEmpty()) {
            for (SlotConfig.ParticleSetting ps : pattern.getParticleSettings()) {
                spawnWinParticles(buttonBlock, ps, player);
            }
        }

        String next = pattern.getNextSlotOnWin();
        if (next != null && !next.isEmpty()) {
            machine.setSlotConfigName(next);
            MachineManager.saveAllMachines();
        }
    }

    private void broadcastWinToAll(Player winner,
                                   MachineData machine,
                                   SlotConfig.BroadcastSettings bs,
                                   double moneyWon) {
        String rawMsg = bs.getMessage();
        if (rawMsg == null) rawMsg = "";
        rawMsg = rawMsg
                .replace("<playerName>", winner.getName())
                .replace("<profit>", String.valueOf(moneyWon))
                .replace("<slotName>", machine.getSlotConfigName());
        String finalMsg = translateColorAndNewline(rawMsg, machine);

        SlotConfig.BroadcastSettings.BroadcastSoundParam bSound = bs.getBroadcastSound();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!finalMsg.isEmpty()) {
                p.sendMessage(finalMsg);
            }
            if (bSound != null && !bSound.getType().isEmpty()) {
                double vol = (bSound.getVolume() > 0) ? bSound.getVolume() : defaultVolume;
                double pitch = (bSound.getPitch() > 0) ? bSound.getPitch() : defaultPitch;
                double rad = (bSound.getRadius() != 0) ? bSound.getRadius() : defaultRadius;
                playSoundWithRadius(winner, null, bSound.getType(), vol, pitch, rad);
            }
        }
    }

    private void runEventsIfNeeded(MachineData machine, SlotConfig config, Player player,String machineId) {
        // イベントが無いならreturn
        if (config.getEvent() == null || config.getEvent().isEmpty()) return;

        // 変数マップ確保
        var varMap = machine.getVariables();
        if (varMap == null) {
            varMap = new HashMap<>();
            machine.setVariables(varMap);
        }

        // ボタン位置(サウンド再生用)
        World machineWorld = Bukkit.getWorld(machine.getWorldName());
        Location buttonLoc = new Location(machineWorld, machine.getX(), machine.getY(), machine.getZ());
        Block buttonBlock = buttonLoc.getBlock();

        for (SlotConfig.EventDefinition evt : config.getEvent()) {
            // conditionチェック
            if (checkCondition(evt.getCondition(), machine)) {
                // varCalc
                if (evt.getVarCalc() != null && !evt.getVarCalc().isEmpty()) {
                    applyVarCalc(evt.getVarCalc(), machine);
                }

                // eventSound
                SlotConfig.EventDefinition.EventSoundParam es = evt.getEventSound();
                if (es != null && es.getType() != null && !es.getType().isEmpty()) {
                    double vol = (es.getVolume() > 0) ? es.getVolume() : defaultVolume;
                    double pitch = (es.getPitch() > 0) ? es.getPitch() : defaultPitch;
                    double rad = (es.getRadius() != 0) ? es.getRadius() : defaultRadius;
                    playSoundWithRadius(player, buttonBlock, es.getType(), vol, pitch, rad);
                }

                // message
                if (evt.getMessage() != null && !evt.getMessage().isEmpty()) {
                    String msg = translateColorAndNewline(evt.getMessage(), machine);
                    player.sendMessage(msg);
                }

                // ★ 報酬(複数対応) → giveReward
                //   例: evt.getRewards() が List<Reward>
                for (SlotConfig.Reward rw : evt.getRewards()) {
                    giveReward(player, machine, rw,machineId);
                }

                // nextSlotOnWin
                if (evt.getNextSlotOnWin() != null && !evt.getNextSlotOnWin().isEmpty()) {
                    machine.setSlotConfigName(evt.getNextSlotOnWin());
                    MachineManager.saveAllMachines();
                }
            }
        }
        MachineManager.saveAllMachines();
    }

    private void runPatternEventsIfNeeded(MachineData machine, List<SlotConfig.EventDefinition> events, Player player,String machineId) {
        if (events == null || events.isEmpty()) return;

        var varMap = machine.getVariables();
        if (varMap == null) {
            varMap = new HashMap<>();
            machine.setVariables(varMap);
        }

        World machineWorld = Bukkit.getWorld(machine.getWorldName());
        Location buttonLoc = new Location(machineWorld, machine.getX(), machine.getY(), machine.getZ());
        Block buttonBlock = buttonLoc.getBlock();

        for (SlotConfig.EventDefinition evt : events) {
            // condition check
            if (checkCondition(evt.getCondition(), machine)) {
                // varCalc
                if (evt.getVarCalc() != null && !evt.getVarCalc().isEmpty()) {
                    applyVarCalc(evt.getVarCalc(), machine);
                }

                // eventSound
                SlotConfig.EventDefinition.EventSoundParam es = evt.getEventSound();
                if (es != null && es.getType() != null && !es.getType().isEmpty()) {
                    double vol = (es.getVolume() > 0) ? es.getVolume() : defaultVolume;
                    double pitch = (es.getPitch() > 0) ? es.getPitch() : defaultPitch;
                    double rad = (es.getRadius() != 0) ? es.getRadius() : defaultRadius;
                    playSoundWithRadius(player, buttonBlock, es.getType(), vol, pitch, rad);
                }

                // message
                if (evt.getMessage() != null && !evt.getMessage().isEmpty()) {
                    String msg = translateColorAndNewline(evt.getMessage(), machine);
                    player.sendMessage(msg);
                }

                // ★ 報酬(複数) → giveReward
                for (SlotConfig.Reward rw : evt.getRewards()) {
                    giveReward(player, machine, rw,machineId);
                }

                // nextSlotOnWin
                if (evt.getNextSlotOnWin() != null && !evt.getNextSlotOnWin().isEmpty()) {
                    machine.setSlotConfigName(evt.getNextSlotOnWin());
                    MachineManager.saveAllMachines();
                }
            }
        }
        MachineManager.saveAllMachines();
    }


    private boolean checkCondition(String cond, MachineManager.MachineData machine) {
        if (cond == null || cond.isEmpty()) return false;
        // "1" → 常にtrue
        if (cond.equals("1")) return true;

        // 1) 変数置換
        Map<String, Double> varMap = machine.getVariables();
        if (varMap == null) varMap = new HashMap<>();

        // 変数を [varName -> 値] で置換
        String replaced = cond;
        for (var entry : varMap.entrySet()) {
            String varName = entry.getKey();
            double varVal = entry.getValue();
            // <変数名> → varVal
            replaced = replaced.replaceAll("\\b" + varName + "\\b", String.valueOf(varVal));
        }

        // 2) stock 置換
        //   condition内に "stock" があれば machineのstockを挿入
        replaced = replaced.replaceAll("\\bstock\\b", String.valueOf(machine.getStock()));

        // 3) 数式として eval
        try {
            double val = ExpressionParser.eval(replaced);
            // 0 なら false, 非0なら true とみなす例
            return (Math.abs(val) > 0.000001);
        } catch (Exception e) {
            plugin.getLogger().warning("[checkCondition] eval error: " + e.getMessage());
            return false;
        }
    }

    private void applyVarCalc(String expr, MachineManager.MachineData machine) {
        // exprに "varName=式" の形が必須
        if (expr == null || !expr.contains("=")) {
            plugin.getLogger().warning("[applyVarCalc] no '=' in expr: " + expr);
            return;
        }
        String[] sp = expr.split("=");
        if (sp.length != 2) {
            plugin.getLogger().warning("[applyVarCalc] invalid varCalc: " + expr);
            return;
        }
        String varName = sp[0].trim();  // 左辺
        String right = sp[1].trim();  // 右辺

        Map<String, Double> varMap = machine.getVariables();
        if (varMap == null) {
            varMap = new HashMap<>();
            machine.setVariables(varMap);
        }

        // 右辺に含まれる 変数名/stock を置換
        String replaced = right;
        for (var e : varMap.entrySet()) {
            String k = e.getKey();
            double v = e.getValue();
            replaced = replaced.replaceAll("\\b" + k + "\\b", String.valueOf(v));
        }
        // stock の置換
        replaced = replaced.replaceAll("\\bstock\\b", String.valueOf(machine.getStock()));

        // 式をeval
        double newVal;
        try {
            newVal = ExpressionParser.eval(replaced);
        } catch (Exception ex) {
            plugin.getLogger().warning("[applyVarCalc] error: " + ex.getMessage());
            return;
        }

        // 左辺が "stock" なら machine.setStock((int)newVal)
        if (varName.equals("stock")) {
            // stock を整数扱いする例
            int s = (int) newVal;
            machine.setStock(s);
            plugin.getLogger().info("[applyVarCalc] stock => " + s);
        } else {
            // 通常変数
            if (!varMap.containsKey(varName)) {
                varMap.put(varName, 0.0);
            }
            varMap.put(varName, newVal);
            plugin.getLogger().info("[applyVarCalc] " + varName + " => " + newVal);
        }
    }


    /**
     * 文字列内の変数名を varMap の値に置き換える
     */
    private String replaceVariables(String expr, @NotNull Map<String, Double> varMap) {
        String replaced = expr;
        for (var e : varMap.entrySet()) {
            // \bvarName\b => e.getValue()
            replaced = replaced.replaceAll("\\b" + e.getKey() + "\\b", e.getValue().toString());
        }
        // "stock" はここで置換しない( parseRewardValue など別)
        return replaced;
    }

    //========================
    // pattern抽選
    //========================
    private SlotConfig.PatternConfig drawPatternOrMiss(SlotConfig config, MachineData machine) {
        var list = config.getPatterns();
        if (list == null || list.isEmpty()) return null;

        double sum = 0;
        for (var pc : list) {
            double prob = parseDoubleExpression(pc.getProbability(), machine);
            if (prob < 0 || prob > 100) {
                throw new RuntimeException("確率範囲外:" + prob);
            }
            sum += prob;
        }
        if (sum > 100) {
            throw new RuntimeException("合計>100");
        }

        // 修正: 0〜100 の範囲で乱数を取り、sum未満ならパターン、それ以外はハズレ
        double r = ThreadLocalRandom.current().nextDouble(100.0);

        double cum = 0;
        for (var pc : list) {
            double p = parseDoubleExpression(pc.getProbability(), machine);
            cum += p;
            if (r < cum) {
                return pc;
            }
        }
        // ここまでマッチしなければハズレ
        return null;
    }


    //========================
    // stock操作
    //========================
    private void applyLoseStockOperation(String machineId, SlotConfig config) {
        var md = MachineManager.getMachine(machineId);
        if (md == null) return;
        int cur = md.getStock();

        String op = config.getLoseStockOperation();
        if (op == null) op = "ADD";
        int val = config.getLoseStockValue();
        switch (op.toUpperCase()) {
            case "ADD" -> cur += val;
            case "SUB" -> cur -= val;
            case "SET" -> cur = val;
        }
        md.setStock(cur);
        MachineManager.saveAllMachines();
    }

    private void applyWinStockOperation(String machineId, SlotConfig.PatternConfig pc) {
        var md = MachineManager.getMachine(machineId);
        if (md == null) return;
        int cur = md.getStock();

        String op = pc.getStockOperation();
        if (op == null) op = "ADD";
        int val = pc.getStockValue();
        switch (op.toUpperCase()) {
            case "ADD" -> cur += val;
            case "SUB" -> cur -= val;
            case "SET" -> cur = val;
        }
        md.setStock(cur);
        MachineManager.saveAllMachines();
    }

    //========================
    // 報酬
    //========================
    private void giveReward(Player player, MachineData machine, Reward rw,String machineID) {
        if (rw == null) return;

        switch (rw.getType().toLowerCase()) {
            case "money" -> {
                double val = parseRewardValue(rw.getValue(), machine);
                if (plugin.getVaultIntegration() != null && val > 0) {
                    plugin.getVaultIntegration().deposit(player, val);
                }
                if (val > 0) {
                    SlotMachinePlugin.addProfit(player.getUniqueId(),machineID, val);
                }
            }
            case "item" -> {
                // ★ 変更: "value" が「変数名」か「Material名」かを判定
                String rawVal = rw.getValue();
                double qDouble = parseRewardValue(rw.getQuantity(), machine);
                if (qDouble < 1) qDouble = 1;
                int q = (int) qDouble;
                // 1) itemConfigs から探す
                ItemStack stack = plugin.getItemConfigManager().getItemByKey(rawVal);
                if (stack != null) {
                    ItemStack copy = stack.clone();
                    copy.setAmount(q);
                    player.getInventory().addItem(copy);
                } else {
                    // fallback: Material として
                    Material mat = Material.matchMaterial(rawVal);
                    if (mat == null) mat = Material.STONE;
                    player.getInventory().addItem(new ItemStack(mat, q));
                }
            }
        }
    }


    private double parseRewardValue(String expr, MachineData machine) {
        if (machine == null) return 0;
        // stock置換
        expr = expr.replace("stock", String.valueOf(machine.getStock()));

        // 変数置換
        // 変数置換
        Map<String, Double> varMap = machine.getVariables();
        if (varMap == null) varMap = new HashMap<>();

        for (var e : varMap.entrySet()) {
            expr = expr.replaceAll("\\b" + e.getKey() + "\\b", e.getValue().toString());
        }

        return ExpressionParser.eval(expr);
    }

    //========================
    // ハズレ用
    //========================
    private List<String> pickNonWinning3(List<String> pool, SlotConfig config) {
        for (int tries = 0; tries < 500; tries++) {
            List<String> candidate = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                candidate.add(pickRandom(pool));
            }
            if (!matchesAnyPattern(candidate, config.getPatterns())) {
                return candidate;
            }
        }
        return Arrays.asList("STONE", "STONE", "STONE");
    }

    private boolean matchesAnyPattern(List<String> items, List<SlotConfig.PatternConfig> patterns) {
        if (patterns == null) return false;
        for (var pc : patterns) {
            List<String> p = pc.getItems();
            if (p.size() == 3) {
                if (p.get(0).equalsIgnoreCase(items.get(0))
                        && p.get(1).equalsIgnoreCase(items.get(1))
                        && p.get(2).equalsIgnoreCase(items.get(2))) {
                    return true;
                }
            }
        }
        return false;
    }

    private SlotConfig.PatternConfig checkWhichPattern(SlotConfig config, List<String> items) {
        if (config.getPatterns() == null) return null;
        for (var pc : config.getPatterns()) {
            List<String> p = pc.getItems();
            if (p.size() == 3) {
                if (p.get(0).equalsIgnoreCase(items.get(0))
                        && p.get(1).equalsIgnoreCase(items.get(1))
                        && p.get(2).equalsIgnoreCase(items.get(2))) {
                    return pc;
                }
            }
        }
        return null;
    }

    //========================
    // Utility
    //========================
    private List<String> collectAllItemsFromPatterns(SlotConfig config) {
        List<String> result = new ArrayList<>();
        if (config.getPatterns() != null) {
            for (var pc : config.getPatterns()) {
                result.addAll(pc.getItems());
            }
        }
        if (result.isEmpty()) result.add("STONE");
        return result;
    }

    private String pickRandom(List<String> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    private void endSpin(UUID uuid, String machineId) {
        spinningPlayers.remove(uuid);
        busyMachineMap.remove(machineId);
    }


    private void updateSignStock(String machineId, Block buttonBlock) {
        Sign sign = findAdjacentSign(buttonBlock);
        if (sign == null) return;
        var md = MachineManager.getMachine(machineId);
        if (md == null) return;
        sign.setLine(0, "§eStock: " + md.getStock());
        sign.update(true, false);
    }

    private Sign findAdjacentSign(Block baseBlock) {
        for (BlockFace face : BlockFace.values()) {
            if (face == BlockFace.SELF) continue;
            Block adj = baseBlock.getRelative(face);
            if (adj.getState() instanceof Sign s) {
                return s;
            }
        }
        return null;
    }

    private List<ItemFrame> findFrames(Block buttonBlock, List<BlockPos> offsets) {
        List<ItemFrame> result = new ArrayList<>();

        // ボタンのBlockDataをチェック
        BlockData bd = buttonBlock.getBlockData();
        if (!(bd instanceof Switch sw)) {
            return result; // スイッチ(ボタン)でない
        }

        // ボタンが向いている方向 (壁なら NORTH/EAST/SOUTH/WEST、天井=DOWN、床=UP)
        BlockFace facing = sw.getFacing();

        // offsets をもとに「探すべきブロック位置」それぞれで近接するアイテムフレームを検索
        for (BlockPos off : offsets) {
            Block targetBlock = buttonBlock.getRelative(off.dx, off.dy, off.dz);

            // targetBlock の近辺(少し余裕を持たせる)にあるエンティティの中で ItemFrame を抽出
            targetBlock.getWorld().getNearbyEntities(targetBlock.getBoundingBox().expand(0.1),
                    e -> e instanceof ItemFrame).forEach(e -> {

                ItemFrame frame = (ItemFrame) e;
                // frame.getFacing() が requiredFrameFacing と一致する (= 同じ面)
                // → 採用
                if (frame.getFacing() == facing) {
                    result.add(frame);
                }
            });
        }

        return result;
    }


    private List<BlockPos> findOffsetDefinition(Block buttonBlock) {
        var bd = buttonBlock.getBlockData();
        if (!(bd instanceof Switch sw)) return Collections.emptyList();
        FaceAttachable.AttachedFace face = sw.getAttachedFace();
        var facing = sw.getFacing();
        String key = face + "_" + facing;
        var offDef = OFFSET_MAP.get(key);
        if (offDef == null) return Collections.emptyList();
        return offDef.reelOffsets;
    }

    /**
     * 変数式を評価 (double)
     * 例: "var1+20", "30", "var2*2.5"
     */
    private double parseDoubleExpression(Object maybeExpr, MachineData machine) {
        // config定義が数値 or 文字列かもしれない → unify as string
        if (maybeExpr == null) return 0;
        String str = String.valueOf(maybeExpr);
        // 変数置換
        String replaced = replaceVariables(str, machine.getVariables());
        return ExpressionParser.eval(replaced);
    }

    private void setSlotMachineItemKey(ItemStack stack, String varName) {
        if (stack == null) return;
        var meta = stack.getItemMeta();
        if (meta == null) return;
        var pdc = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "SlotMachineItemKey");
        pdc.set(key, PersistentDataType.STRING, varName);
        stack.setItemMeta(meta);
    }

    // ユーティリティ: ItemStack から varName を取得 (なければnull)
    private String getSlotMachineItemKey(ItemStack stack) {
        if (stack == null) return null;
        var meta = stack.getItemMeta();
        if (meta == null) return null;
        var pdc = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "SlotMachineItemKey");
        return pdc.get(key, PersistentDataType.STRING);
    }

    private void playSoundWithRadius(Player player, Block buttonBlock,
                                     String soundKey,
                                     double volume,
                                     double pitch,
                                     double radius) {
        // ボタンブロックのワールド/座標を使う
        if (soundKey == null || soundKey.isEmpty()) return;
        Sound snd;
        try {
            String sname = soundKey
                    .replace("minecraft:", "")
                    .toUpperCase()
                    .replace(".", "_");
            snd = Sound.valueOf(sname);
        } catch (Exception ex) {
            plugin.getLogger().warning("[Slot] Unknown sound:" + soundKey);
            return;
        }


        if (radius == -2 || buttonBlock == null) {
            // 全員
            for (Player p : player.getWorld().getPlayers()) {
                p.playSound(p.getLocation(), snd, (float) volume, (float) pitch);
            }
        } else if (radius == -1) {
            player.playSound(player.getLocation(), snd, (float) volume, (float) pitch);
        } else {
            // 半径内プレイヤー
            for (Player p : buttonBlock.getWorld().getPlayers()) {
                // ボタンブロックの中心
                Location center = buttonBlock.getLocation().add(0.5, 0.5, 0.5);
                // プレイヤーとの距離
                double d = p.getLocation().distance(center);
                float vol = (float) (1 - (d / radius)) * (float) volume;
                p.playSound(p.getLocation(), snd, vol, (float) pitch);

            }
        }
    }
    //========================
    // Data classes
    //========================
    public record BlockPos(int dx, int dy, int dz) {
    }
    public record OffsetDefinition(List<BlockPos> reelOffsets) {
    }

}
