package com.github.klee.slotMachinePlugin;

import com.github.klee.slotMachinePlugin.commands.*;
import com.github.klee.slotMachinePlugin.utils.VaultIntegration;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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

        // slotConfigs フォルダとテンプレート
        File slotConfigFolder = new File(folder, "slotConfigs");
        if (!slotConfigFolder.exists()) {
            slotConfigFolder.mkdirs();
            createSlotConfigTemplate(slotConfigFolder);
        }

        // itemConfigs フォルダとテンプレート
        File itemConfigFolder = new File(folder, "itemConfigs");
        if (!itemConfigFolder.exists()) {
            itemConfigFolder.mkdirs();
            createItemConfigTemplate(itemConfigFolder);
        }
    }

    private void createSlotConfigTemplate(File folder) {
        File template = new File(folder, "example.jsonc");
        if (template.exists()) return;

        String content = """
{
  // === 基本設定 ===
  "reels": 3,                     // リール数（額縁の数）
  "shuffleTime": 2.0,             // シャッフル時間（秒）
  "spinCost": 100,                // 1回のコスト（お金）
  "shuffleSpeed": 0.1,            // シャッフル速度
  "spinSpeed": 0.3,               // 回転速度

  // === アイテムコスト（お金の代わりにアイテム消費、任意） ===
  // "itemCost": {
  //   "name": "DIAMOND",          // アイテム名 or itemConfigsのキー
  //   "amount": 1
  // },

  // === 負けた時の設定 ===
  "loseStockOperation": "add",    // "add" / "set" / "sub"
  "loseStockValue": 10,           // 負けた時にストックに加算する値
  "loseMessage": "&c残念！ハズレです",

  // === デフォルトサウンド ===
  "defaultSoundSettings": {
    "startSound": { "type": "minecraft:block.note_block.pling", "volume": 1.0, "pitch": 1.0, "radius": 2.0 },
    "rotatingSound": { "type": "minecraft:block.note_block.bass", "volume": 0.5, "pitch": 1.0, "radius": 2.0 },
    "reelStopSound": { "type": "minecraft:block.note_block.snare", "volume": 1.0, "pitch": 1.0, "radius": 2.0 },
    "endLoseSound": { "type": "minecraft:entity.villager.no", "volume": 1.0, "pitch": 1.0, "radius": 2.0 }
  },

  // === デフォルトパーティクル ===
  "defaultParticleSettings": [
    { "particle": "FLAME", "count": 10, "speed": 0.1, "offset": [0.5, 0.5, 0.5], "point": "button" }
  ],

  // === 変数定義（イベント用、任意） ===
  "variables": [
    { "varName": "bonus", "initialValue": 0 }
  ],

  // === パターン（当選役） ===
  "patterns": [
    {
      "probability": "0.01",                              // 当選確率 (1%)
      "items": ["DIAMOND", "DIAMOND", "DIAMOND"],         // 表示アイテム
      "rewards": [
        { "type": "money", "value": "stock*10" },         // お金報酬（stockの10倍）
        { "type": "item", "value": "DIAMOND", "quantity": "5" }
      ],
      "stockOperation": "set",
      "stockValue": 0,
      "winMessage": "&6&lジャックポット！",
      "patternSound": { "type": "minecraft:ui.toast.challenge_complete", "volume": 1.0, "pitch": 1.0, "radius": 10.0 },
      "broadcastSettings": {
        "message": "&e{player}がジャックポットを当てました！",
        "broadcastSound": { "type": "minecraft:entity.ender_dragon.death", "volume": 1.0, "pitch": 1.0, "radius": 50.0 }
      },
      "particleSettings": [
        { "particle": "TOTEM_OF_UNDYING", "count": 100, "speed": 1.0, "offset": [1, 1, 1], "point": "player" }
      ]
    },
    {
      "probability": "0.1",                               // 当選確率 (10%)
      "items": ["GOLD_INGOT", "GOLD_INGOT", "GOLD_INGOT"],
      "rewards": [{ "type": "money", "value": "500" }],
      "winMessage": "&a金の延べ棒揃い！ +500"
    },
    {
      "probability": "0.2",                               // 当選確率 (20%)
      "items": ["IRON_INGOT", "IRON_INGOT", "IRON_INGOT"],
      "rewards": [{ "type": "money", "value": "200" }],
      "winMessage": "&7鉄の延べ棒揃い！ +200"
    }
  ],

  // === グローバルイベント（任意） ===
  "event": [
    {
      "condition": "stock >= 1000",
      "varCalc": "bonus = bonus + 1",
      "message": "&eストック1000超え！ボーナスカウント: {bonus}"
    }
  ]
}
""";

        try (FileWriter writer = new FileWriter(template)) {
            writer.write(content);
            getLogger().info("[SlotMachinePlugin] テンプレート作成: slotConfigs/example.jsonc");
        } catch (IOException e) {
            getLogger().warning("[SlotMachinePlugin] テンプレート作成失敗: " + e.getMessage());
        }
    }

    private void createItemConfigTemplate(File folder) {
        File template = new File(folder, "example.json");
        if (template.exists()) return;

        String content = """
{
  "exampleSword": "手に持ったアイテムで /slot itemstack を実行するとBase64文字列が出力されます",
  "exampleArmor": "その文字列をここに貼り付けてください"
}
""";

        try (FileWriter writer = new FileWriter(template)) {
            writer.write(content);
            getLogger().info("[SlotMachinePlugin] テンプレート作成: itemConfigs/example.json");
        } catch (IOException e) {
            getLogger().warning("[SlotMachinePlugin] テンプレート作成失敗: " + e.getMessage());
        }
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
