package com.mahirung.rpgcore.commands;

import com.mahirung.rpgcore.RPGCore;
import com.mahirung.rpgcore.managers.PartyManager;
import com.mahirung.rpgcore.util.ChatUtil;
import org.bukkit.Bukkit;
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
import java.util.stream.Collectors;

public class PartyCommand implements CommandExecutor, TabCompleter {

    private final RPGCore plugin;
    private final PartyManager partyManager;

    private static final List<String> SUB_COMMANDS = Arrays.asList(
            "create", "invite", "accept", "deny", "leave", "kick", "promote", "leader", "chat", "list", "help"
    );

    public PartyCommand(RPGCore plugin) {
        this.plugin = plugin;
        this.partyManager = plugin.getPartyManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("플레이어만 사용 가능");
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            sendHelpMessage(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        try {
            switch (sub) {
                case "create": partyManager.createSoloParty(player); break;
                case "invite":
                    if(args.length < 2) player.sendMessage("사용법: /party invite <유저>");
                    else {
                        Player t = Bukkit.getPlayer(args[1]);
                        if(t != null) partyManager.invitePlayer(player, t);
                        else player.sendMessage("유저를 찾을 수 없습니다.");
                    }
                    break;
                case "accept":
                    // [Fix] null 인자를 명시적으로 전달하여 PartyManager의 메소드 시그니처와 일치시킴
                    if(args.length < 2) partyManager.acceptInvite(player, null);
                    else partyManager.acceptInvite(player, args[1]);
                    break;
                case "deny":
                    if(args.length < 2) partyManager.denyInvite(player, null);
                    else partyManager.denyInvite(player, args[1]);
                    break;
                case "leave": partyManager.leaveParty(player); break;
                case "kick":
                    if(args.length < 2) player.sendMessage("사용법: /party kick <유저>");
                    else partyManager.kickPlayer(player, args[1]);
                    break;
                case "promote":
                    if(args.length < 2) player.sendMessage("사용법: /party promote <유저>");
                    else {
                        Player t = Bukkit.getPlayer(args[1]);
                        if(t != null) partyManager.promotePlayer(player, t);
                    }
                    break;
                case "chat": partyManager.togglePartyChat(player); break;
                case "list": partyManager.showPartyInfo(player); break;
                default: sendHelpMessage(player);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatUtil.format("&a/party create, invite, accept, deny, leave, kick, list"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return StringUtil.copyPartialMatches(args[0], SUB_COMMANDS, new ArrayList<>());
        return Collections.emptyList();
    }
}