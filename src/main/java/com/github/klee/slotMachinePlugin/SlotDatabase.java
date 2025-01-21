package com.github.klee.slotMachinePlugin;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.*;
import java.util.logging.Level;

/**
 * SQLiteデータベース "slotData.db" に
 * slot_records テーブル (id, uuid, slot_id, timestamp, profit) を保管するクラス。
 */
public class SlotDatabase {

    private final Plugin plugin;
    private Connection connection;

    public SlotDatabase(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * DB初期化:
     *  - pluginフォルダが存在しなければ作成
     *  - slotData.db に接続 (無ければ自動生成)
     *  - slot_records テーブルをIF NOT EXISTSで作成
     */
    public void init() {
        try {
            // plugins/SlotMachinePlugin/ フォルダが存在しない場合は作る
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            // DBファイルへのパス
            File dbFile = new File(plugin.getDataFolder(), "slotData.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url);

            // テーブルが無ければ作る
            try (Statement st = connection.createStatement()) {
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS slot_records (
                       id INTEGER PRIMARY KEY AUTOINCREMENT,
                       uuid TEXT NOT NULL,
                       slot_id TEXT NOT NULL,
                       timestamp BIGINT NOT NULL,
                       profit DOUBLE NOT NULL
                    );
                """);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[SlotDatabase] initエラー", e);
        }
    }

    /**
     * コネクション取得
     * - もしnull or isClosed() なら init() で再接続を試みる
     */
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            init();
            if (connection == null || connection.isClosed()) {
                throw new SQLException("DB接続を復旧できませんでした");
            }
        }
        return connection;
    }

    /**
     * DBクローズ
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * レコード追加
     * - getConnection() で再接続を確保し、使い終わったらcloseしない
     *   (ステートメントだけ閉じる)
     */
    public void record(String uuid, String slotId, long timestamp, double profit) {
        // まずコネクションを取得 (再接続が必要ならinitを呼ぶ)
        try (Connection conn = getConnection()) {
            if (conn == null) {
                return;
            }
            String sql = "INSERT INTO slot_records (uuid, slot_id, timestamp, profit) VALUES (?,?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid);
                ps.setString(2, slotId);
                ps.setLong(3, timestamp);
                ps.setDouble(4, profit);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SlotDatabase] recordエラー", e);
        }
    }
}
