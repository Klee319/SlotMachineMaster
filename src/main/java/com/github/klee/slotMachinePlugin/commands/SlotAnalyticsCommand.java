package com.github.klee.slotMachinePlugin.commands;

import com.github.klee.slotMachinePlugin.SlotDatabase;
import com.github.klee.slotMachinePlugin.SlotMachinePlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * /slot analytics <topSlot|topUser> <periodInDays(double)>
 * 例) /slot analytics topSlot 0.25 → 過去6時間 (0.25日)
 *
 * 今回の修正:
 * - スロット別( topSlot ) / ユーザ別( topUser )ともに
 *   入金(inc) / 支出(cost) を計算し、 "還元率PF" = inc / cost
 *   (costが0なら cost=1 として割る)
 */
public class SlotAnalyticsCommand implements CommandExecutor {

    private final SlotMachinePlugin plugin;

    public SlotAnalyticsCommand(SlotMachinePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String lbl, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /slot analytics <topSlot|topUser> <days(double)>");
            sender.sendMessage("§7(例) 0.25=6時間, 1=1日, 1.5=36時間");
            return true;
        }

        String subCmd = args[0];
        double days;
        try {
            days = Double.parseDouble(args[1]);
        } catch (Exception e) {
            sender.sendMessage("§c数値形式で指定してください。 (例: 0.25=6時間)");
            return true;
        }

        switch (subCmd.toLowerCase()) {
            case "topslot" -> showTopSlots(sender, days);
            case "topuser" -> showTopUsers(sender, days);
            default -> sender.sendMessage("§cサブコマンドが不明です: " + subCmd);
        }
        return true;
    }

    /**
     * days <= 1.0 の場合は "過去XX時間" と表示、
     * days > 1.0 の場合は "過去X日" と表示
     */
    private String formatPeriod(double days) {
        if (days <= 0) {
            return "全期間";
        }
        if (days <= 1.0) {
            // 例: 0.25 => 6時間
            //     0.5 => 12時間
            //     1.0 => 24時間
            double hours = days * 24.0;
            return String.format("過去 %.0f時間", hours);
        } else {
            return String.format("過去 %.1f日", days);
        }
    }

    /**
     * スロットID別の還元率ランキング
     * 還元率PF = inc/cost
     * cost=0なら cost=1
     */
    private void showTopSlots(CommandSender sender, double days) {
        long now = System.currentTimeMillis();
        long oldestTime;
        if (days > 0) {
            long periodMs = (long) (days * 24.0 * 3600.0 * 1000.0);
            oldestTime = now - periodMs;
        } else {
            oldestTime = 0L; // 全期間
        }

        SlotDatabase db = plugin.getSlotDatabase();
        if (db == null) {
            sender.sendMessage("§cDB未初期化。");
            return;
        }

        List<SlotStat> list = new ArrayList<>();
        try (Connection conn = db.getConnection()) {
            String sql = """
               SELECT slot_id,
                      sum(CASE WHEN profit>0 THEN profit ELSE 0 END) as totalIncome,
                      sum(CASE WHEN profit<0 THEN profit ELSE 0 END) as totalMinus
               FROM slot_records
               WHERE timestamp >= ?
               GROUP BY slot_id
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, oldestTime);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String sid = rs.getString("slot_id");
                        double inc = rs.getDouble("totalIncome");  // 入金
                        double minus = rs.getDouble("totalMinus"); // 負(出金)
                        double cost = -minus; // 出金の合計
                        if(cost <= 0) cost = 1.0; // 0除算回避
                        double rate = inc / cost;

                        list.add(new SlotStat(sid, inc, cost, rate));
                    }
                }
            }
        } catch (Exception ex) {
            sender.sendMessage("§cDBエラー:" + ex.getMessage());
            return;
        }

        // 還元率降順
        list.sort((a,b)-> Double.compare(b.rate, a.rate));

        String periodStr = formatPeriod(days);
        sender.sendMessage("§a=== スロット別 還元率PF ( "+periodStr+" ) ===");

        int rank=1;
        for(SlotStat st : list){
            sender.sendMessage(rank+"位: §e"+ st.slotId
                    +" §7(入金:"+String.format("%.0f", st.income)
                    +", 出金:"+String.format("%.0f", st.cost)
                    +", PF:"+String.format("%.2f", st.rate)+")");
            rank++;
            if(rank>10) break;
        }
    }

    /**
     * ユーザー別利益ランキング
     * 還元率PF = inc/cost
     * cost=0なら cost=1
     */
    private void showTopUsers(CommandSender sender, double days) {
        long now = System.currentTimeMillis();
        long oldestTime = (days>0)? (now - (long)(days * 24*3600*1000)) : 0L;

        SlotDatabase db = plugin.getSlotDatabase();
        if(db==null){
            return;
        }
        List<UserStat> userList= new ArrayList<>();

        try(Connection conn= db.getConnection()){
            String sql= """
              SELECT uuid,
                     sum(CASE WHEN profit>0 THEN profit ELSE 0 END) as totalIncome,
                     sum(CASE WHEN profit<0 THEN profit ELSE 0 END) as totalMinus
              FROM slot_records
              WHERE timestamp >= ?
              GROUP BY uuid
              ORDER BY totalIncome DESC
            """;
            try(PreparedStatement ps= conn.prepareStatement(sql)){
                ps.setLong(1, oldestTime);
                try(ResultSet rs= ps.executeQuery()){
                    while(rs.next()){
                        String uuidStr= rs.getString("uuid");
                        double inc= rs.getDouble("totalIncome");
                        double minus= rs.getDouble("totalMinus");
                        double cost= -minus;
                        if(cost<=0) cost=1.0;
                        double rate= inc/cost;

                        userList.add(new UserStat(uuidStr, inc, cost, rate));
                    }
                }
            }
        }catch(Exception ex){
            sender.sendMessage("§cDBエラー:"+ex.getMessage());
            return;
        }

        // rate降順
        userList.sort((a,b)-> Double.compare(b.rate, a.rate));

        String periodStr= formatPeriod(days);
        sender.sendMessage("§a=== ユーザー別 還元率PF ( "+periodStr+" ) ===");
        int rank=1;
        for(UserStat us: userList){
            var op= Bukkit.getOfflinePlayer(java.util.UUID.fromString(us.uuid));
            String pname= op.getName();
            if(pname==null) pname= us.uuid.substring(0,8);

            sender.sendMessage(rank+"位: §b"+pname
                    +" §7(入金:"+String.format("%.0f", us.income)
                    +", 出金:"+String.format("%.0f", us.cost)
                    +", PF:"+String.format("%.2f", us.rate)+")");
            if(rank>=10) break;
            rank++;
        }
    }

    private record SlotStat(String slotId, double income, double cost, double rate) {}
    private record UserStat(String uuid, double income, double cost, double rate) {}
}
