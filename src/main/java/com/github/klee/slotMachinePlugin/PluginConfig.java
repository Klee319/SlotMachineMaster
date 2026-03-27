package com.github.klee.slotMachinePlugin;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

public class PluginConfig {

    private final Plugin plugin;
    private final Gson gson = new Gson();

    private List<String> entryFileNames = List.of("main", "normal");

    public PluginConfig(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File configFile = new File(plugin.getDataFolder(), "config.json");
        if (!configFile.exists()) {
            plugin.saveResource("config.json", false);
        }

        try {
            String content = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
            JsonReader reader = new JsonReader(new StringReader(content));
            reader.setLenient(true);
            RawConfig raw = gson.fromJson(reader, RawConfig.class);
            if (raw != null && raw.entryFileNames != null && !raw.entryFileNames.isEmpty()) {
                entryFileNames = Collections.unmodifiableList(raw.entryFileNames);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("config.json の読み込みに失敗: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("config.json のパースに失敗: " + e.getMessage());
        }
    }

    public void reload() {
        load();
    }

    public List<String> getEntryFileNames() {
        return entryFileNames;
    }

    private static class RawConfig {
        List<String> entryFileNames;
    }
}
