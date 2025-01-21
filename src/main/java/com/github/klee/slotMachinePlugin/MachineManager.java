package com.github.klee.slotMachinePlugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * マシンIDごとのデータを管理し、保存/読み込み。
 * ここでは「相対パス」で slotConfigName を記録。
 */
public class MachineManager {

    private static final Map<String, MachineData> machineDataMap = new HashMap<>();
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static File dataFile;

    public static void init(File pluginDataFolder) {
        dataFile = new File(pluginDataFolder, "machines.json");
    }

    public static boolean hasMachineId(String machineId) {
        return machineDataMap.containsKey(machineId);
    }

    public static MachineData getMachine(String machineId) {
        return machineDataMap.get(machineId);
    }

    public static void setMachineData(String machineId, MachineData data) {
        machineDataMap.put(machineId, data);
    }

    public static Map<String, MachineData> getAllMachines() {
        return machineDataMap;
    }

    public static void loadAllMachines() {
        if (dataFile == null || !dataFile.exists()) return;
        try (Reader rd = new InputStreamReader(new FileInputStream(dataFile), StandardCharsets.UTF_8)) {
            java.lang.reflect.Type t = new com.google.gson.reflect.TypeToken<HashMap<String, MachineData>>() {
            }.getType();
            Map<String, MachineData> loaded = gson.fromJson(rd, t);
            if (loaded != null) {
                machineDataMap.clear();
                machineDataMap.putAll(loaded);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveAllMachines() {
        if (dataFile == null) return;
        try (Writer wt = new OutputStreamWriter(new FileOutputStream(dataFile), StandardCharsets.UTF_8)) {
            gson.toJson(machineDataMap, wt);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * MachineData: 台ごとの情報を保持
     * - slotConfigName: "demo_slot" or "demo_slot/demo_slot_bonus" or "../demo_slot"
     * - stock, variables
     * - ボタンの位置(worldName, x,y,z)
     */
    public static class MachineData {
        private String slotConfigName;  // 相対パス(拡張子.jsonは省略)
        private int stock;

        private String worldName;
        private int x, y, z;

        // varName -> currentValue
        private Map<String, Double> variables = new HashMap<>();

        public String getSlotConfigName() {
            return slotConfigName;
        }

        public void setSlotConfigName(String slotConfigName) {
            this.slotConfigName = slotConfigName;
        }

        public int getStock() {
            return stock;
        }

        public void setStock(int stock) {
            this.stock = stock;
        }

        public String getWorldName() {
            return worldName;
        }

        public void setWorldName(String worldName) {
            this.worldName = worldName;
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }

        public int getZ() {
            return z;
        }

        public void setZ(int z) {
            this.z = z;
        }

        public Map<String, Double> getVariables() {
            return variables;
        }

        public void setVariables(Map<String, Double> variables) {
            this.variables = variables;
        }
    }
}
