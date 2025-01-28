package com.github.klee.slotMachinePlugin.commands;

import com.github.klee.slotMachinePlugin.MachineManager;
import com.github.klee.slotMachinePlugin.MachineManager.MachineData;
import com.github.klee.slotMachinePlugin.SlotConfig;
import com.github.klee.slotMachinePlugin.SlotConfig.VariableDefinition;
import com.github.klee.slotMachinePlugin.SlotConfig.PatternConfig;
import com.github.klee.slotMachinePlugin.SlotConfig.Reward;
import com.github.klee.slotMachinePlugin.SlotMachinePlugin;
import com.github.klee.slotMachinePlugin.utils.ExpressionParser;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * /slot debug <machineId> <count>
 * 毎回初期状態からスタートするように、debug用の一時MachineDataを作ってシミュレーション
 */
public class DebugSlotCommand implements CommandExecutor {

    private final SlotMachinePlugin plugin;

    /**
     * デバッグ実行中の合計入金額
     */
    private double totalMoneyGain;

    /**
     * スロット設定由来の変数をキャッシュするマップ
     * (stock も含め、イベントや回転終了後まで保持)
     */
    private Map<String, Double> configVarCache = new HashMap<>();

    public DebugSlotCommand(SlotMachinePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤーのみ実行可能です。");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§c/slot debug <machineId> <count>");
            return true;
        }
        String machineId = args[0];
        int count;
        try {
            count = Integer.parseInt(args[1]);
        } catch (Exception e) {
            player.sendMessage("§c回数は数値を指定してください。");
            return true;
        }

        if(!MachineManager.hasMachineId(machineId)) {
            player.sendMessage("§cMachineId '"+machineId+"' は存在しません。");
            return true;
        }

        MachineData original = MachineManager.getMachine(machineId);
        if(original == null){
            player.sendMessage("§cデータが見当たりません: "+machineId);
            return true;
        }

        // スロット設定取得
        SlotConfig config = plugin.getSlotManager().getSlotConfig(original.getSlotConfigName());
        if(config == null){
            player.sendMessage("§cスロット設定が読み込めません: "+original.getSlotConfigName());
            return true;
        }

        // デバッグ用 MachineData を初期化 (stock=0, machineVar=空)
        MachineData debugData = createDebugMachineData(original, config);

        // 合計入金を0に
        this.totalMoneyGain = 0;

        // configVarCache 初期化
        configVarCache.clear();
        if(config.getVariables() != null){
            for(VariableDefinition vd : config.getVariables()){
                configVarCache.put(vd.getVarName(), vd.getInitialValue());
            }
        }
        // machineのstockをキャッシュ
        configVarCache.put("stock", (double)debugData.getStock());

        // シミュレーション
        doDebugSimulate(player, debugData, config, count);

        return true;
    }

    private MachineData createDebugMachineData(MachineData original, SlotConfig config) {
        MachineData debugMd = new MachineData();
        debugMd.setSlotConfigName(original.getSlotConfigName());
        // デバッグ用に stock=0
        debugMd.setStock(0);
        debugMd.setVariables(new HashMap<>());
        debugMd.setWorldName(original.getWorldName());
        debugMd.setX(original.getX());
        debugMd.setY(original.getY());
        debugMd.setZ(original.getZ());
        return debugMd;
    }

    private void doDebugSimulate(Player player, MachineData machine, SlotConfig config, int count) {
        player.sendMessage("§e==== DebugSlot Start: machineId="+ machine.getSlotConfigName()
                + " count="+count+" ====");

        double totalCost=0.0;
        Map<String,Integer> itemGainMap = new HashMap<>();

        for(int i=0; i<count; i++){
            // スロット切り替えの可能性があるので、その都度取得
            SlotConfig currentCfg = plugin.getSlotManager().getSlotConfig(machine.getSlotConfigName());
            if(currentCfg==null){
                player.sendMessage("§cスロット設定 '"+machine.getSlotConfigName()+"' がありません。");
                break;
            }

            // 1. イベント
            runTopLevelEventOnce(currentCfg, machine, itemGainMap);

            // 2. スピンコスト
            double costThisTime = parseDoubleExpression(currentCfg.getSpinCost(), machine);
            if(costThisTime<=0) costThisTime=1;
            totalCost += costThisTime;

            // 3. パターン抽選
            List<PatternConfig> patterns = currentCfg.getPatterns();
            if(patterns==null || patterns.isEmpty()){
                break;
            }
            double sum=0;
            for(PatternConfig pc : patterns){
                sum += parseDoubleExpression(pc.getProbability(), machine);
            }
            PatternConfig ptn= drawPatternOrMiss(patterns, sum, machine);

            if(ptn != null) {
                // -------------------------------
                // ★★ 勝った場合 => ptn.getStockChange() 反映
                // -------------------------------
                double stBefore = getStock(); // キャッシュ上のstock
                double newStock = ptn.getStockValue();
                if(Objects.equals(ptn.getStockOperation(), "ADD")){
                    newStock += stBefore;
                }
                else if(Objects.equals(ptn.getStockOperation(), "SUB")){
                    newStock = stBefore-newStock;
                }
                configVarCache.put("stock", newStock);

                // 報酬
                for(Reward rw : ptn.getRewards()){
                    if(rw==null) continue;
                    switch(rw.getType().toLowerCase()){
                        case "money"-> {
                            double amt= parseRewardValue(rw.getValue(), machine);
                            this.totalMoneyGain += amt;
                        }
                        case "item"-> {
                            double qDouble= parseRewardValue(rw.getQuantity(), machine);
                            if(qDouble<1) qDouble=1;
                            int q=(int)qDouble;
                            itemGainMap.put(rw.getValue(),
                                    itemGainMap.getOrDefault(rw.getValue(),0)+q);
                        }
                    }
                }
                // nextSlotOnWin
                if(ptn.getNextSlotOnWin()!=null && !ptn.getNextSlotOnWin().isEmpty()){
                    machine.setSlotConfigName(ptn.getNextSlotOnWin());
                }
                // pattern event
                runPatternEventOnce(ptn, machine, itemGainMap);

            } else {
                // -------------------------------
                // ★★ 負けた場合 => loseStockChange 反映
                // -------------------------------
                double loseChange = currentCfg.getLoseStockValue();
                double stBefore = getStock();
                if(Objects.equals(currentCfg.getLoseStockOperation(), "ADD")){
                    double newStock = stBefore + loseChange;
                    configVarCache.put("stock", newStock);
                }
                else if(Objects.equals(currentCfg.getLoseStockOperation(), "SUB")) {
                    double newStock = stBefore - loseChange;
                    configVarCache.put("stock", newStock);
                }
            }

            // 回転終了後に stock を machine に同期
            syncStockToMachine(machine);
            System.out.println(configVarCache);
            System.out.println("stock="+machine.getStock());
        }

        double paybackRate = (totalCost>0)? (this.totalMoneyGain / totalCost)*100 : 0;

        // 結果表示
        player.sendMessage("§7[Debug Result]");
        player.sendMessage(" §7回数: "+count);
        player.sendMessage(" §7出金合計(コスト): "+totalCost);
        player.sendMessage(" §7お金の入金合計: "+this.totalMoneyGain);
        player.sendMessage(" §7還元率(お金のみ): "+String.format("%.2f", paybackRate)+" %");

        if(!itemGainMap.isEmpty()){
            player.sendMessage(" §7アイテム獲得:");
            for(var e : itemGainMap.entrySet()){
                player.sendMessage("   §f"+ e.getKey() +" x"+e.getValue());
            }
        } else {
            player.sendMessage(" §7アイテム獲得: なし");
        }

        player.sendMessage("§e==== DebugSlot End ====");
    }

    /**
     * トップレベルのevent
     */
    private void runTopLevelEventOnce(SlotConfig cfg, MachineData machine, Map<String,Integer> itemGainMap) {
        if(cfg.getEvent()==null || cfg.getEvent().isEmpty()) return;
        for(var evt: cfg.getEvent()){
            if(checkCondition(evt.getCondition(), machine)){
                if(evt.getVarCalc()!=null && !evt.getVarCalc().isEmpty()){
                    applyVarCalc(evt.getVarCalc(), machine);
                }
                for(Reward rw: evt.getRewards()){
                    if(rw==null) continue;
                    switch(rw.getType().toLowerCase()){
                        case "money"-> {
                            double amt= parseRewardValue(rw.getValue(), machine);
                            this.totalMoneyGain += amt;
                        }
                        case "item"-> {
                            double qDouble= parseRewardValue(rw.getQuantity(), machine);
                            if(qDouble<1) qDouble=1;
                            int q=(int)qDouble;
                            itemGainMap.put(rw.getValue(),
                                    itemGainMap.getOrDefault(rw.getValue(),0)+q);
                        }
                    }
                }
                if(evt.getNextSlotOnWin()!=null && !evt.getNextSlotOnWin().isEmpty()){
                    machine.setSlotConfigName(evt.getNextSlotOnWin());
                }
            }
        }
    }

    /**
     * パターン内の event
     */
    private void runPatternEventOnce(PatternConfig ptn, MachineData machine, Map<String,Integer> itemGainMap) {
        if(ptn.getEvent()==null || ptn.getEvent().isEmpty()) return;
        for(var evt: ptn.getEvent()){
            if(checkCondition(evt.getCondition(), machine)){
                if(evt.getVarCalc()!=null && !evt.getVarCalc().isEmpty()){
                    applyVarCalc(evt.getVarCalc(), machine);
                }
                for(Reward rw: evt.getRewards()){
                    if(rw==null) continue;
                    switch(rw.getType().toLowerCase()){
                        case "money"-> {
                            double amt= parseRewardValue(rw.getValue(), machine);
                            this.totalMoneyGain += amt;
                        }
                        case "item"-> {
                            double qDouble= parseRewardValue(rw.getQuantity(), machine);
                            if(qDouble<1) qDouble=1;
                            int q=(int)qDouble;
                            itemGainMap.put(rw.getValue(),
                                    itemGainMap.getOrDefault(rw.getValue(),0)+q);
                        }
                    }
                }
                if(evt.getNextSlotOnWin()!=null && !evt.getNextSlotOnWin().isEmpty()){
                    machine.setSlotConfigName(evt.getNextSlotOnWin());
                }
            }
        }
    }

    private boolean checkCondition(String cond, MachineData machine){
        if(cond==null||cond.isEmpty()) return false;
        if(cond.equals("1")) return true;
        double val= parseDoubleExpression(cond, machine);
        return (Math.abs(val)>1e-7);
    }

    /**
     * 変数を計算する式: 例 "stock=stock+10"
     */
    private void applyVarCalc(String expr, MachineData machine){
        if(expr==null|| !expr.contains("="))return;
        String[] sp= expr.split("=");
        if(sp.length!=2)return;

        String left= sp[0].trim();
        String right= sp[1].trim();

        String replaced = replaceVariables(right, machine);
        double newVal=0;
        try{
            newVal= ExpressionParser.eval(replaced);
        }catch(Exception ex){
            plugin.getLogger().warning("[DebugSlot] applyVarCalc error:"+ex.getMessage()
                    +" expr="+expr);
            return;
        }

        if(left.equals("stock")){
            configVarCache.put("stock", newVal);
        } else {
            // configVarCache にあるなら書き換え、なければ machine変数へ
            if(configVarCache.containsKey(left)){
                configVarCache.put(left, newVal);
            } else {
                Map<String,Double> mVars = machine.getVariables();
                if(mVars==null){
                    mVars=new HashMap<>();
                    machine.setVariables(mVars);
                }
                mVars.put(left,newVal);
            }
        }
    }

    /**
     * 数値式を評価して double を返す
     */
    private double parseDoubleExpression(Object expr, MachineData machine){
        if(expr==null) return 0;
        String str= String.valueOf(expr);

        String replaced = replaceVariables(str, machine);
        try{
            return ExpressionParser.eval(replaced);
        }catch(Exception ex){
            plugin.getLogger().warning("[DebugSlot] parseDoubleExpression error:"+ex.getMessage()+" expr="+replaced);
            return 0;
        }
    }

    /**
     * 報酬値を評価
     */
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

    /**
     * パターン抽選
     */
    private PatternConfig drawPatternOrMiss(List<PatternConfig> patterns, double sumProb, MachineData machine){
        if(patterns==null||patterns.isEmpty())return null;
        if(sumProb<=0)return null;
        double r= ThreadLocalRandom.current().nextDouble(sumProb);
        double cum=0;
        for(PatternConfig pc: patterns){
            double p= parseDoubleExpression(pc.getProbability(), machine);
            cum+= p;
            if(r< cum){
                return pc;
            }
        }
        return null;
    }

    /**
     * 回転終了時に configVarCache("stock") を MachineData に同期
     */
    private void syncStockToMachine(MachineData machine){
        if(!configVarCache.containsKey("stock")) return;
        double stVal = configVarCache.get("stock");
        machine.setStock((int) stVal);
    }

    /**
     * configVarCacheから "stock" を取得
     */
    private double getStock(){
        if(!configVarCache.containsKey("stock")) return 0;
        return configVarCache.get("stock");
    }

    /**
     * 文字列の変数を configVarCache + machineVar + stock で置換
     */
    private String replaceVariables(String expr, MachineData machine){
        if(expr==null) return "";
        // 1) configVarCache
        for(var e : configVarCache.entrySet()){
            expr = expr.replaceAll("\\b"+ e.getKey()+"\\b", e.getValue().toString());
        }
        // 2) machineVar
        Map<String,Double> mVars = machine.getVariables();
        if(mVars==null){
            mVars = new HashMap<>();
            machine.setVariables(mVars);
        }
        for(var e : mVars.entrySet()){
            expr = expr.replaceAll("\\b"+ e.getKey()+"\\b", e.getValue().toString());
        }

        // 3) 未定義 => 0
        Pattern pat = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b");
        Matcher m = pat.matcher(expr);
        while(m.find()){
            String unknown = m.group(1);
            // 既に置換済みでなければ 0
            // ただし厳密に "stock" は configVarCacheで置換済み
            if(!unknown.equals("") && !unknown.matches("\\d+")){
                plugin.getLogger().warning("[DebugSlot] 未定義変数 '"+unknown+"' => 0  expr="+expr);
                expr = expr.replaceAll("\\b"+unknown+"\\b", "0");
            }
        }
        return expr;
    }
}
