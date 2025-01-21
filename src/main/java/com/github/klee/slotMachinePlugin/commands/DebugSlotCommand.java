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

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /slot debug <machineId> <count>
 * 毎回初期状態からスタートするように、debug用の一時MachineDataを作ってシミュレーションする
 */
public class DebugSlotCommand implements CommandExecutor {

    private final SlotMachinePlugin plugin;
    private double totalMoneyGain;
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

        // 本番用 MachineData
        MachineData original = MachineManager.getMachine(machineId);
        if(original==null){
            player.sendMessage("§cデータが見当たりません: "+machineId);
            return true;
        }

        // 1) slotconfig を取得
        SlotConfig config = plugin.getSlotManager().getSlotConfig(original.getSlotConfigName());
        if(config==null){
            player.sendMessage("§cスロット設定が読み込めません: "+original.getSlotConfigName());
            return true;
        }

        // 2) debug用 MachineData を初期化 (毎回0から)
        MachineData debugData = createDebugMachineData(original, config);
        totalMoneyGain=0;
        // 3) debug 実行
        doDebugSimulate(player, debugData, count);

        return true;
    }

    /**
     * original MachineData を参考に、debug用に「stock=0」「variables=初期値」にした MachineData を作成
     */
    private MachineData createDebugMachineData(MachineData original, SlotConfig config) {
        MachineData debugMd = new MachineData();
        // 同じslotConfigName
        debugMd.setSlotConfigName(original.getSlotConfigName());
        // stock=0 (あるいは初期値 if you prefer)
        debugMd.setStock(0);

        // variables = config.getVariables() の initialValue をセット
        Map<String,Double> varMap = new HashMap<>();
        if(config.getVariables()!=null){
            for(VariableDefinition vd : config.getVariables()){
                varMap.put(vd.getVarName(), vd.getInitialValue());
            }
        }
        debugMd.setVariables(varMap);

        // ボタンの位置情報などは本番と同じ
        debugMd.setWorldName(original.getWorldName());
        debugMd.setX(original.getX());
        debugMd.setY(original.getY());
        debugMd.setZ(original.getZ());

        return debugMd;
    }

    private void doDebugSimulate(Player player, MachineData machine, int count) {
        player.sendMessage("§e==== DebugSlot Start: machineId="+ machine.getSlotConfigName()
                + " count="+count+" ====");

        double totalCost=0.0;
        Map<String,Integer> itemGainMap = new HashMap<>();

        // i回ループ
        for(int i=0; i<count; i++){
            // 最新 config
            SlotConfig cfg = plugin.getSlotManager().getSlotConfig(machine.getSlotConfigName());
            if(cfg==null){
                player.sendMessage("§cスロット設定 '"+machine.getSlotConfigName()+"' がありません。");
                break;
            }

            // top-level event (1回チェックでOK, 連鎖対応ならwhile loop)
            runTopLevelEventOnce(cfg, machine, totalMoneyGain, itemGainMap);

            // spinCost
            double costThisTime = parseDoubleExpression(cfg.getSpinCost(), machine);
            if(costThisTime<=0) costThisTime=1;
            totalCost += costThisTime;

            // pattern抽選
            List<PatternConfig> patterns = cfg.getPatterns();
            if(patterns==null|| patterns.isEmpty()){
                player.sendMessage("§7patterns が未定義:"+cfg);
                continue;
            }
            // 確率合計
            double sum=0;
            for(PatternConfig pc: patterns){
                sum += parseDoubleExpression(pc.getProbability(), machine);
            }
            PatternConfig ptn= drawPatternOrMiss(patterns, sum, machine);
            if(ptn!=null){
                // 報酬
                for(Reward rw : ptn.getRewards()){
                    if(rw==null) continue;
                    switch(rw.getType().toLowerCase()){
                        case "money"-> {
                            double amt= parseRewardValue(rw.getValue(), machine);
                            totalMoneyGain+=amt;
                        }
                        case "item"-> {
                            double qDouble= parseRewardValue(rw.getQuantity(), machine);
                            if(qDouble<1) qDouble=1;
                            int q=(int)qDouble;
                            itemGainMap.put(rw.getValue(),
                                    itemGainMap.getOrDefault(rw.getValue(),0)+ q);
                        }
                    }
                }
                // nextSlotOnWin
                if(ptn.getNextSlotOnWin()!=null && !ptn.getNextSlotOnWin().isEmpty()){
                    machine.setSlotConfigName(ptn.getNextSlotOnWin());
                }

                // pattern event(1回チェック)
                runPatternEventOnce(ptn, machine, totalMoneyGain, itemGainMap);
            }
        }

        double paybackRate = (totalCost>0)? (totalMoneyGain/ totalCost)*100 : 0;

        // 結果表示
        player.sendMessage("§7[Debug Result]");
        player.sendMessage(" §7回数: "+count);
        player.sendMessage(" §7出金合計: "+totalCost);
        player.sendMessage(" §7お金の入金合計: "+totalMoneyGain);
        player.sendMessage(" §7還元率(お金のみ): "+String.format("%.2f", paybackRate)+" %");

        if(!itemGainMap.isEmpty()){
            player.sendMessage(" §7アイテム獲得:");
            for(var e : itemGainMap.entrySet()){
                player.sendMessage("   §f"+e.getKey()+" x"+e.getValue());
            }
        } else {
            player.sendMessage(" §7アイテム獲得: なし");
        }

        player.sendMessage("§e==== DebugSlot End ====");
    }

    /**
     * トップレベルのevent を1回だけチェック
     * (複数連鎖させるなら while(true) で回す方式に拡張も可)
     */
    private void runTopLevelEventOnce(SlotConfig cfg, MachineData machine,
                                      double totalMoneyGain,
                                      Map<String,Integer> itemGainMap) {
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
                            double amt= parseRewardValue(rw.getValue(),machine);
                            totalMoneyGain+= amt;
                        }
                        case "item"-> {
                            double qDouble= parseRewardValue(rw.getQuantity(),machine);
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
     * パターン内 event
     */
    private void runPatternEventOnce(PatternConfig ptn, MachineData machine,
                                     double totalMoneyGain,
                                     Map<String,Integer> itemGainMap) {
        if(ptn.getEvent()==null||ptn.getEvent().isEmpty()) return;
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
                            totalMoneyGain+= amt;
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
        return (Math.abs(val)>0.000001);
    }

    private void applyVarCalc(String expr, MachineData machine){
        if(expr==null|| !expr.contains("="))return;
        String[] sp= expr.split("=");
        if(sp.length!=2)return;
        String left= sp[0].trim();
        String right= sp[1].trim();

        Map<String,Double> varMap= machine.getVariables();
        if(varMap==null){
            varMap= new HashMap<>();
            machine.setVariables(varMap);
        }

        for(var e: varMap.entrySet()){
            right= right.replaceAll("\\b"+e.getKey()+"\\b", e.getValue().toString());
        }
        right= right.replaceAll("\\bstock\\b", String.valueOf(machine.getStock()));

        double newVal=0;
        try{
            newVal= ExpressionParser.eval(right);
        }catch(Exception ex){
            plugin.getLogger().warning("[DebugSlot] applyVarCalc error:"+ex.getMessage());
            return;
        }
        if(left.equals("stock")){
            machine.setStock((int)newVal);
        } else {
            if(!varMap.containsKey(left)){
                varMap.put(left,0.0);
            }
            varMap.put(left,newVal);
        }
    }

    private double parseDoubleExpression(Object expr, MachineData machine){
        if(expr==null) return 0;
        String str= String.valueOf(expr);

        Map<String,Double> varMap= machine.getVariables();
        if(varMap==null){
            varMap=new HashMap<>();
            machine.setVariables(varMap);
        }
        for(var e: varMap.entrySet()){
            str= str.replaceAll("\\b"+e.getKey()+"\\b", e.getValue().toString());
        }
        // stock
        str= str.replaceAll("\\bstock\\b", String.valueOf(machine.getStock()));

        try{
            return ExpressionParser.eval(str);
        }catch(Exception ex){
            plugin.getLogger().warning("[DebugSlot] parseDoubleExpression error:"+ex.getMessage()+" expr="+str);
            return 0;
        }
    }

    private double parseRewardValue(String expr, MachineData machine){
        if(expr==null|| expr.isEmpty()) return 0;
        expr= expr.replaceAll("\\bstock\\b", String.valueOf(machine.getStock()));
        Map<String,Double> varMap= machine.getVariables();
        if(varMap==null){
            varMap=new HashMap<>();
            machine.setVariables(varMap);
        }
        for(var e: varMap.entrySet()){
            expr= expr.replaceAll("\\b"+ e.getKey()+"\\b", e.getValue().toString());
        }
        try{
            return ExpressionParser.eval(expr);
        }catch(Exception ex){
            plugin.getLogger().warning("[DebugSlot] parseRewardValue error:"+ex.getMessage()+" expr="+expr);
            return 0;
        }
    }

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
}
