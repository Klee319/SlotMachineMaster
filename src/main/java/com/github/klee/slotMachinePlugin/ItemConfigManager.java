package com.github.klee.slotMachinePlugin;

import com.github.klee.slotMachinePlugin.utils.ItemStackUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

/**
 * itemConfigs フォルダ内の *.json を読み込み、
 * "キー(変数名)" => "Base64文字列" のペアを読み取り、ItemStackに復元。
 */
public class ItemConfigManager {

    private final Plugin plugin;
    private final Map<String, ItemStack> globalItemMap = new HashMap<>();

    public ItemConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void loadAllItemConfigs() {
        globalItemMap.clear();
        File folder = new File(plugin.getDataFolder(), "itemConfigs");
        if (!folder.exists()) {
            folder.mkdirs();
            return;
        }
        loadRecursively(folder);
        plugin.getLogger().info("[ItemConfigManager] 全ItemConfigsを読み込み完了。合計: " + globalItemMap.size() + "件");
    }

    private void loadRecursively(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;

        Gson gson = new Gson();
        for (File f : files) {
            if (f.isDirectory()) {
                loadRecursively(f);
            } else if (f.getName().endsWith(".json")) {
                try (FileReader rd = new FileReader(f)) {
                    // "変数名" -> "Base64文字列" の map
                    Map<String, String> data = gson.fromJson(
                            rd, new TypeToken<Map<String, String>>() {
                            }.getType()
                    );
                    if (data != null) {
                        for (var e : data.entrySet()) {
                            String keyName = e.getKey();        // e.g. "sword1"
                            String base64 = e.getValue();       // Base64 string
                            // base64 -> ItemStack
                            try {
                                ItemStack is = ItemStackUtil.itemFromBase64(base64);
                                globalItemMap.put(keyName, is);
                                plugin.getLogger().info("[ItemConfig] '" + keyName + "' をBase64から復元完了");
                            } catch (Exception ex) {
                                plugin.getLogger().warning("[ItemConfig] '" + keyName + "' の復元失敗: " + ex.getMessage());
                            }
                        }
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("[ItemConfig] 読み込みエラー: " + f.getName() + " / " + ex.getMessage());
                }
            }
        }
    }

    public ItemStack getItemByKey(String varName) {
        return globalItemMap.get(varName);
    }

    public Map<String, ItemStack> getGlobalItemMap() {
        return globalItemMap;
    }
}
