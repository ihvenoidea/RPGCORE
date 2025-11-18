package com.mahirung.rpgcore.commands;

import com.mahirung.rpgcore.RPGCore;
import com.mahirung.rpgcore.managers.DungeonManager;
import com.mahirung.rpgcore.util.ChatUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 던전 관련 명령어 (/dungeon)
 * - /dungeon list : 던전 목록 보기
 * - /dungeon enter <id> : 던전 입장
 * - /dungeon exit : 던전 퇴장
 */
public class DungeonCommand implements CommandExecutor, TabCompleter {

    private final RPGCore plugin;
    private final DungeonManager dungeonManager;

    public DungeonCommand(RPGCore plugin) {
        this.plugin = plugin;
        this.dungeonManager = plugin.getDungeonManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatUtil.format("&c[RPGCore] &f이 명령어는 플레이어만 사용할 수 있습니다."));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "list":
                dungeonManager.showDungeonList(player);
                break;

            case "enter":
                if (args.length < 2) {
                    player.sendMessage(ChatUtil.format("&c[Dungeon] &f입장할 던전 ID를 입력하세요."));
                    return true;
                }
                String dungeonId = args[1];
                dungeonManager.attemptEnterDungeon(player, dungeonId);
                break;

            case "exit":
                dungeonManager.exitDungeon(player);
                break;

            default:
                sendHelp(player);
                break;
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatUtil.format("&a===== [ Dungeon 명령어 도움말 ] ====="));
        player.sendMessage(ChatUtil.format("&e/dungeon list &7- 던전 목록 보기"));
        player.sendMessage(ChatUtil.format("&e/dungeon enter <id> &7- 던전 입장"));
        player.sendMessage(ChatUtil.format("&e/dungeon exit &7- 던전 퇴장"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();
        Player player = (Player) sender;

        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            subs.add("list");
            subs.add("enter");
            subs.add("exit");
            return subs;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("enter")) {
            return new ArrayList<>(dungeonManager.getAllDungeonIds());
        }

        return Collections.emptyList();
    }
}
