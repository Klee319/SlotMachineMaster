package com.github.klee.slotMachinePlugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;


public class SlotManager {

    private final Plugin plugin;
    private final Map<String, SlotConfig> cacheMap = new HashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public SlotManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void loadAllSlotConfigs() {
        cacheMap.clear();

        File folder = new File(plugin.getDataFolder(), "slotConfigs");
        if (!folder.exists()) {
            folder.mkdirs();
            return;
        }

        // ディレクトリ再帰
        loadRecursively(folder, folder);
    }

    private void loadRecursively(File root, File current) {
        File[] files = current.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                // 再帰
                loadRecursively(root, f);
            } else {
                // .json or .jsonc
                if (f.getName().endsWith(".json") || f.getName().endsWith(".jsonc")) {
                    String relativePath = getRelativePathWithoutExt(root, f);
                    try {
                        // 1) ファイル文字列読み込み
                        String content = Files.readString(f.toPath(), StandardCharsets.UTF_8);

                        // 2) コメント削除 (簡易)
                        //    行コメント //... と ブロックコメント /* ... */
                        content = content.replaceAll("//.*", "");
                        content = content.replaceAll("/\\*.*?\\*/", "");

                        // 3) Gson パース
                        SlotConfig cfg = gson.fromJson(content, SlotConfig.class);
                        if (cfg != null) {
                            cacheMap.put(relativePath, cfg);
                            plugin.getLogger().info("スロット設定を読み込み: " + relativePath + " from " + f.getName());
                        }
                    } catch (IOException e) {
                        plugin.getLogger().warning("設定ファイル読み込みエラー: " + f.getName());
                        e.printStackTrace();
                    } catch (Exception ex) {
                        plugin.getLogger().warning("JSONパースエラー: " + f.getName() + " : " + ex.getMessage());
                    }
                }
            }
        }
    }

    private String getRelativePathWithoutExt(File root, File f) {
        String fullRoot = root.getAbsolutePath();
        String fullFile = f.getAbsolutePath();

        // remove .json or .jsonc
        if (fullFile.endsWith(".json")) {
            fullFile = fullFile.substring(0, fullFile.length() - 5);
        } else if (fullFile.endsWith(".jsonc")) {
            fullFile = fullFile.substring(0, fullFile.length() - 6);
        }

        // substring root
        if (fullFile.startsWith(fullRoot)) {
            fullFile = fullFile.substring(fullRoot.length());
        }
        // remove leading "/" or "\"
        while (fullFile.startsWith("\\") || fullFile.startsWith("/")) {
            fullFile = fullFile.substring(1);
        }
        // replace backslash→slash
        fullFile = fullFile.replace('\\', '/');

        return fullFile;
    }

    /**
     * 相対パスで検索
     *
     * @param relativePath "demo_slot" や "subdir/someSlot" 等
     */
    public SlotConfig getSlotConfig(String relativePath) {
        return cacheMap.get(relativePath);
    }
}
