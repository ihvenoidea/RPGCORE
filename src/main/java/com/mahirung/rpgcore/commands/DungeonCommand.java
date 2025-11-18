package com.mahirung.rpgcore.commands;

import com.mahirung.rpgcore.RPGCore;
import com.mahirung.rpgcore.managers.DungeonManager;
import com.mahirung.rpgcore.util.ChatUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

        // 권한 체크
        if (!player.hasPermission("rpgcore.dungeon.use")) {
            player.sendMessage(ChatUtil.format("&c[RPGCore] &f이 명령어를 실행할 권한이 없습니다."));
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        try {
            switch (subCommand) {
                case "enter":
                case "입장":
                    handleDungeonEnter(player, args);
                    break;
                case "list":
                    dungeonManager.showDungeonList(player);
                    break;
                case "exit":
                case "나가기":
                    dungeonManager.exitDungeon(player);
                    break;
                default:
                    sendHelpMessage(player);
                    break;
            }
        } catch (Exception e) {
            player.sendMessage(ChatUtil.format("&c[Dungeon] &f명령어 실행 중 오류가 발생했습니다. 관리자에게 문의하세요."));
            plugin.getLogger().severe("[DungeonCommand] 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    private void handleDungeonEnter(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatUtil.format("&c[Dungeon] &f사용법: /dungeon enter <던전_ID>"));
            return;
        }
        String dungeonId = args[1];
        dungeonManager.attemptEnterDungeon(player, dungeonId);
    }
    
    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatUtil.format("&a===== [ RPGCore 던전 도움말 ] ====="));
        player.sendMessage(ChatUtil.format("&e/dungeon enter <던전_ID> &7- 해당 ID의 던전에 입장을 시도합니다."));
        player.sendMessage(ChatUtil.format("&e/dungeon list &7- 입장 가능한 던전 목록을 봅니다."));
        player.sendMessage(ChatUtil.format("&e/dungeon exit &7- 현재 있는 던전에서 나갑니다."));
        player.sendMessage(ChatUtil.format("&a================================="));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return completions;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("rpgcore.dungeon.use")) {
            return completions;
        }

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("enter", "list", "exit");
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("enter")) {
            List<String> dungeonIds = dungeonManager.getAllDungeonIds();
            StringUtil.copyPartialMatches(args[1], dungeonIds, completions);
            return completions;
        }
        return completions;
    }
}
