package com.github.klee.slotMachinePlugin;

import com.github.klee.slotMachinePlugin.commands.*;
import com.github.klee.slotMachinePlugin.utils.VaultIntegration;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalTime;
import java.util.*;
import java.util.logging.Level;

/**
 * メインプラグインクラス
 */
public class SlotMachinePlugin extends JavaPlugin {
    private static SlotMachinePlugin instance;
    private static final Map<UUID, Map<String, List<Double>>> ephemeralProfitMap = new HashMap<>();
    private ItemConfigManager itemConfigManager;
    private SlotManager slotManager;
    private VaultIntegration vaultIntegration;
    private SlotDatabase slotDatabase;
    public static SlotMachinePlugin getInstance() {
        return instance;
    }
    private static boolean shuttingDown = false;
    @Override
    public void onEnable() {
        instance = this;

        // DB init (CREATE TABLE IF NOT EXISTS) だけ
        slotDatabase = new SlotDatabase(this);
        slotDatabase.init();


        // 6:00,18:00 にセーブ
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            LocalTime now = LocalTime.now();
            if (now.getHour() == 6 && now.getMinute() == 0) {
                saveSlotDataScheduled();
            } else if (now.getHour() == 18 && now.getMinute() == 0) {
                saveSlotDataScheduled();
            }
        }, 20L * 60, 20L * 60);

        setupVault();
        createDefaultFolders();

        // MachineManager
        MachineManager.init(getDataFolder());
        MachineManager.loadAllMachines();

        // SlotManager
        slotManager = new SlotManager(this);
        slotManager.loadAllSlotConfigs();
        itemConfigManager = new ItemConfigManager(this);
        itemConfigManager.loadAllItemConfigs();
        // ボタンにメタデータ再付与
        rebindAllMachineMetadata();

        // リスナー
        getServer().getPluginManager().registerEvents(new SlotMachineListener(this), this);

        // 既存コマンドのインスタンスを作成
        SlotAnalyticsCommand analyticsCmd = new SlotAnalyticsCommand(this);
        DebugSlotCommand debugCmd = new DebugSlotCommand(this);
        SetSlotButtonCommand setCmd = new SetSlotButtonCommand(this);
        DeleteSlotCommand deleteCmd = new DeleteSlotCommand(this);
        SlotItemStackCommand itemStackCmd = new SlotItemStackCommand(this);
        ReloadSlotCommand reloadCmd = new ReloadSlotCommand(this, slotManager);

        // 新しい1つの /slot コマンドに集約
        SlotCommand mainCmd = new SlotCommand(this, analyticsCmd, debugCmd, setCmd, deleteCmd, itemStackCmd, reloadCmd);

        // plugin.yml に "slot" コマンドを定義しておき、ここでExecutor/TabCompleterをセット
        Objects.requireNonNull(getCommand("slot")).setExecutor(mainCmd);
        Objects.requireNonNull(getCommand("slot")).setTabCompleter(mainCmd);

        getLogger().info("SlotMachinePlugin enabled.");
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
        MachineManager.saveAllMachines();
        saveSlotDataScheduled();
        if (slotDatabase != null) {
            slotDatabase.close();
        }
        getLogger().info("[SlotMachinePlugin] onDisable");
    }

    private void setupVault() {
        vaultIntegration = new VaultIntegration(this);
        if (!vaultIntegration.setupEconomy()) {
            getLogger().warning("Vaultが見つかりませんでした。");
        }
    }

    public SlotManager getSlotManager() {
        return slotManager;
    }

    public VaultIntegration getVaultIntegration() {
        return vaultIntegration;
    }

    public ItemConfigManager getItemConfigManager() {
        return itemConfigManager;
    }

    private void createDefaultFolders() {
        File folder = getDataFolder();
        if (!folder.exists()) folder.mkdirs();
        File slotConfigFolder = new File(folder, "slotConfigs");
        if (!slotConfigFolder.exists()) slotConfigFolder.mkdirs();
    }

    /**
     * マシン情報を読み込み→ボタン再bind
     */
    private void rebindAllMachineMetadata() {
        for (Map.Entry<String, MachineManager.MachineData> e : MachineManager.getAllMachines().entrySet()) {
            String machineId = e.getKey();
            MachineManager.MachineData md = e.getValue();

            World w = Bukkit.getWorld(md.getWorldName());
            if (w == null) {
                getLogger().warning("ワールドが無効: " + md.getWorldName());
                continue;
            }
            Block b = w.getBlockAt(md.getX(), md.getY(), md.getZ());
            if (!b.getType().name().endsWith("_BUTTON")) {
                continue;
            }
            b.setMetadata("MachineId", new FixedMetadataValue(this, machineId));
        }
    }

    public static void addProfit(UUID playerUuid, String slotId, double profit) {
        if (shuttingDown) {
            return;
        }
        // 例: profit= -500 (コスト), +1000 (報酬) etc.
        ephemeralProfitMap
                .computeIfAbsent(playerUuid, k -> new HashMap<>())
                .computeIfAbsent(slotId, k -> new ArrayList<>())
                .add(profit);
    }

    /**
     * 定期的 or サーバ終了時に呼ばれ、
     * ephemeralProfitMap にある全レコードをDBへ書き込み、
     * メモリをクリアする
     */
    public void saveSlotDataScheduled() {
        long timestamp = System.currentTimeMillis();

        // スナップショット
        Map<UUID, Map<String, List<Double>>> snapshot;
        synchronized (ephemeralProfitMap) {
            snapshot = new HashMap<>();
            for (var e : ephemeralProfitMap.entrySet()) {
                UUID user = e.getKey();
                Map<String, List<Double>> slotMap = e.getValue();

                Map<String, List<Double>> slotMapCopy = new HashMap<>();
                for (var sEntry : slotMap.entrySet()) {
                    slotMapCopy.put(sEntry.getKey(), new ArrayList<>(sEntry.getValue()));
                }
                snapshot.put(user, slotMapCopy);
            }
            ephemeralProfitMap.clear();
        }

        // ★ 1) まとめて Connection を取得
        try (Connection conn = slotDatabase.getConnection()) {
            if(conn==null || conn.isClosed()){
                return;
            }

            // ★ 2) ここで PreparedStatement を先に作っておく (バインドだけ変える)
            String sql = "INSERT INTO slot_records (uuid, slot_id, timestamp, profit) VALUES (?,?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {

                // ★ 3) スナップショットをループし、同じpsを使って複数INSERT
                for (var userEntry : snapshot.entrySet()) {
                    UUID user = userEntry.getKey();
                    Map<String, List<Double>> slotMap = userEntry.getValue();

                    for (var slotEntry : slotMap.entrySet()) {
                        String slotId = slotEntry.getKey();
                        List<Double> profits = slotEntry.getValue();

                        for (double profit : profits) {
                            ps.setString(1, user.toString());
                            ps.setString(2, slotId);
                            ps.setLong(3, timestamp);
                            ps.setDouble(4, profit);

                            ps.addBatch();
                            // or batch: ps.addBatch();  → まとめて ps.executeBatch();
                        }
                        ps.executeBatch();
                    }
                }
            }

        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "[SlotMachinePlugin] DBへの書き込み失敗", ex);
        }

    }
    public SlotDatabase getSlotDatabase() {
        return slotDatabase;
    }
}
