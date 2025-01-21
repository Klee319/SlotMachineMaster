package com.github.klee.slotMachinePlugin.commands;

import com.github.klee.slotMachinePlugin.MachineManager;
import com.github.klee.slotMachinePlugin.SlotMachinePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;

/**
 * /slot <subcommand> [args...]
 * subcommand:
 * help        -> コマンドの説明一覧
 * list        -> 登録済みスロット一覧
 * analytics   -> /slot analytics <topSlot|topUser> [days(double)]
 * debug       -> /slot debug <machineId> <count>
 * set         -> /slot set <machineId> <configPath>
 * delete      -> /slot delete <machineId>
 * itemstack   -> /slot itemstack
 * reload      -> /slot reload
 */
public class SlotCommand implements CommandExecutor, TabCompleter {

    private final SlotMachinePlugin plugin;
    // 既存コマンドクラスをサブコマンドとして紐付け
    private final Map<String, CommandExecutor> subCommands = new HashMap<>();

    public SlotCommand(SlotMachinePlugin plugin,
                       CommandExecutor analyticsCmd,
                       CommandExecutor debugCmd,
                       CommandExecutor setCmd,
                       CommandExecutor deleteCmd,
                       CommandExecutor itemStackCmd,
                       CommandExecutor reloadCmd) {
        this.plugin = plugin;

        // サブコマンド -> 実際のコマンド実装
        subCommands.put("analytics", analyticsCmd);
        subCommands.put("debug", debugCmd);
        subCommands.put("set", setCmd);
        subCommands.put("delete", deleteCmd);
        subCommands.put("itemstack", itemStackCmd);
        subCommands.put("reload", reloadCmd);

        // help と list はこのクラス内で実装
        // (subCommandsには登録しない)
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }
        String sub = args[0].toLowerCase();

        // (1) help サブコマンド
        if (sub.equals("help")) {
            sendHelp(sender);
            return true;
        }

        // (2) list サブコマンド
        if (sub.equals("list")) {
            listSlots(sender);
            return true;
        }

        // (3) 他のサブコマンド
        CommandExecutor exe = subCommands.get(sub);
        if (exe == null) {
            sender.sendMessage("§c不明なサブコマンド: " + sub);
            sendUsage(sender);
            return true;
        }
        // 残りの引数
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        return exe.onCommand(sender, command, label, subArgs);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // /slot <sub> ...
        if (args.length == 1) {
            // 最初の引数 => サブコマンド候補
            List<String> subs = Arrays.asList(
                    "help", "list", "analytics", "debug", "set", "delete", "itemstack", "reload"
            );
            String current = args[0].toLowerCase();
            List<String> result = new ArrayList<>();
            for (String s : subs) {
                if (s.startsWith(current)) result.add(s);
            }
            return result;
        }
        // 第2引数以降
        // args[0] によって分岐
        String sub = args[0].toLowerCase();
        if (sub.equals("analytics")) {
            // /slot analytics <topSlot|topUser> <days(double)>
            // 例: 第2引数=topSlot or topUser, 第3引数=[days]
            if (args.length == 2) {
                // 候補: topSlot, topUser
                return partialMatch(args[1], Arrays.asList("topSlot", "topUser"));
            }
            if (args.length == 3) {
                // 候補: "[days]"
                return Collections.singletonList("[days]");
            }
            return Collections.emptyList();

        } else if (sub.equals("debug")) {
            // /slot debug <machineId> <count>
            if (args.length == 2) {
                // machineId候補 = 登録済みslot
                return partialMatch(args[1], new ArrayList<>(MachineManager.getAllMachines().keySet()));
            }
            if (args.length == 3) {
                // 候補: "[count]"
                return Collections.singletonList("[count]");
            }
            return Collections.emptyList();

        } else if (sub.equals("set")) {
            // /slot set <machineId> <configPath>
            if (args.length == 2) {
                // machineId -> 新規 or 既存? ここでは既存一覧をヒント表示
                return partialMatch(args[1], new ArrayList<>(MachineManager.getAllMachines().keySet()));
            }
            if (args.length == 3) {
                // configPath => "[configPath]"
                return Collections.singletonList("[configPath]");
            }
            return Collections.emptyList();

        } else if (sub.equals("delete")) {
            // /slot delete <machineId>
            if (args.length == 2) {
                // machineId -> 既存
                return partialMatch(args[1], new ArrayList<>(MachineManager.getAllMachines().keySet()));
            }
            return Collections.emptyList();

        } else if (sub.equals("itemstack")) {
            // /slot itemstack : 追加の引数なし
            return Collections.emptyList();

        } else if (sub.equals("reload")) {
            // /slot reload : 追加の引数なし
            return Collections.emptyList();

        } else if (sub.equals("help") || sub.equals("list")) {
            // 追加引数特になし
            return Collections.emptyList();
        }

        return Collections.emptyList();
    }

    /**
     * help
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§e==== /slot help ====");
        sender.sendMessage("§7/slot list");
        sender.sendMessage("§7  登録されたスロットID一覧を表示");
        sender.sendMessage("§7/slot analytics <topSlot/topUser> [days(per 0.25)]");
        sender.sendMessage("§7  指定期間のランキングを表示");
        sender.sendMessage("§7/slot debug <machineId> <count>");
        sender.sendMessage("§7  指定回数シミュレーションして出金/入金を表示");
        sender.sendMessage("§7/slot set <machineId> <configPath>");
        sender.sendMessage("§7  指定ボタンにスロットをセット");
        sender.sendMessage("§7/slot delete <machineId>");
        sender.sendMessage("§7  指定スロットを削除");
        sender.sendMessage("§7/slot itemstack");
        sender.sendMessage("§7  手に持っているItemStackをコンソール出力");
        sender.sendMessage("§7/slot reload");
        sender.sendMessage("§7  スロット設定をリロード");
    }

    /**
     * list
     */
    private void listSlots(CommandSender sender) {
        Set<String> allIds = MachineManager.getAllMachines().keySet();
        if (allIds.isEmpty()) {
            sender.sendMessage("§7[Slot] 登録されたスロットがありません。");
        } else {
            sender.sendMessage("§e[Slot] 登録スロット一覧 (count=" + allIds.size() + "):");
            for (String id : allIds) {
                sender.sendMessage("  §f- " + id);
            }
        }
    }

    /**
     * args途中まで入力した際に候補を部分一致で返す
     */
    private List<String> partialMatch(String current, Collection<String> candidates) {
        List<String> result = new ArrayList<>();
        String low = current.toLowerCase();
        for (String c : candidates) {
            if (c.toLowerCase().startsWith(low)) {
                result.add(c);
            }
        }
        return result;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§e/slot <help,list,analytics,debug,set,delete,itemstack,reload>");
        sender.sendMessage("§7  '/slot help' で詳細を確認してください。");
    }
}
