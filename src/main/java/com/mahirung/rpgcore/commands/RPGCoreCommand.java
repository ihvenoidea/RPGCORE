package com.mahirung.rpgcore.commands;

import com.mahirung.rpgcore.RPGCore;
import com.mahirung.rpgcore.util.ChatUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RPGCore 메인 관리 명령어 (/rpgcore)
 * - /rpgcore reload : 설정 리로드
 * - /rpgcore version : 버전 확인
 * - /rpgcore help : 도움말
 */
public class RPGCoreCommand implements CommandExecutor, TabCompleter {

    private final RPGCore plugin;

    public RPGCoreCommand(RPGCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload":
                if (!sender.hasPermission("rpgcore.admin.reload")) {
                    sender.sendMessage(ChatUtil.format("&c[RPGCore] &f이 명령어를 실행할 권한이 없습니다."));
                    return true;
                }
                plugin.reloadPlugin();
                sender.sendMessage(ChatUtil.format("&a[RPGCore] &f설정을 리로드했습니다."));
                break;

            case "version":
                sender.sendMessage(ChatUtil.format("&a[RPGCore] &f버전: {0}", plugin.getDescription().getVersion()));
                break;

            case "help":
            default:
                sendHelp(sender);
                break;
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatUtil.format("&a===== [ RPGCore 명령어 도움말 ] ====="));
        sender.sendMessage(ChatUtil.format("&e/rpgcore reload &7- 설정 리로드 (관리자)"));
        sender.sendMessage(ChatUtil.format("&e/rpgcore version &7- 플러그인 버전 확인"));
        sender.sendMessage(ChatUtil.format("&e/rpgcore help &7- 이 도움말 보기"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            subs.add("reload");
            subs.add("version");
            subs.add("help");
            return subs;
        }
        return Collections.emptyList();
    }
}
