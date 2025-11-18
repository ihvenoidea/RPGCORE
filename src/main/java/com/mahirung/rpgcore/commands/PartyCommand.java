package com.mahirung.rpgcore.commands;

import com.mahirung.rpgcore.RPGCore;
import com.mahirung.rpgcore.managers.PartyManager;
import com.mahirung.rpgcore.managers.PartyManager.Party;
import com.mahirung.rpgcore.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 파티 관련 명령어 (/party)
 * - /party create : 파티 생성
 * - /party invite <플레이어> : 파티 초대
 * - /party accept : 초대 수락
 * - /party leave : 파티 탈퇴
 * - /party disband : 파티 해체
 * - /party list : 파티원 목록 보기
 */
public class PartyCommand implements CommandExecutor, TabCompleter {

    private final RPGCore plugin;
    private final PartyManager partyManager;

    public PartyCommand(RPGCore plugin) {
        this.plugin = plugin;
        this.partyManager = plugin.getPartyManager();
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
            case "create":
                partyManager.createParty(player);
                break;

            case "invite":
                if (args.length < 2) {
                    player.sendMessage(ChatUtil.format("&c[Party] &f초대할 플레이어 이름을 입력하세요."));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null || !target.isOnline()) {
                    player.sendMessage(ChatUtil.format("&c[Party] &f해당 플레이어를 찾을 수 없습니다."));
                    return true;
                }
                partyManager.invitePlayer(player, target);
                break;

            case "accept":
                partyManager.acceptInvite(player);
                break;

            case "leave":
                partyManager.leaveParty(player);
                break;

            case "disband":
                partyManager.disbandParty(player);
                break;

            case "list":
                Party party = partyManager.getParty(player.getUniqueId());
                if (party == null) {
                    player.sendMessage(ChatUtil.format("&c[Party] &f현재 파티에 속해 있지 않습니다."));
                } else {
                    player.sendMessage(ChatUtil.format("&a[Party] &f파티원 목록:"));
                    for (Player member : party.getOnlineMembers()) {
                        player.sendMessage(ChatUtil.format("&e- {0}", member.getName()));
                    }
                }
                break;

            default:
                sendHelp(player);
                break;
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatUtil.format("&a===== [ Party 명령어 도움말 ] ====="));
        player.sendMessage(ChatUtil.format("&e/party create &7- 파티 생성"));
        player.sendMessage(ChatUtil.format("&e/party invite <플레이어> &7- 파티 초대"));
        player.sendMessage(ChatUtil.format("&e/party accept &7- 초대 수락"));
        player.sendMessage(ChatUtil.format("&e/party leave &7- 파티 탈퇴"));
        player.sendMessage(ChatUtil.format("&e/party disband &7- 파티 해체"));
        player.sendMessage(ChatUtil.format("&e/party list &7- 파티원 목록 보기"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            subs.add("create");
            subs.add("invite");
            subs.add("accept");
            subs.add("leave");
            subs.add("disband");
            subs.add("list");
            return subs;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                names.add(online.getName());
            }
            return names;
        }

        return Collections.emptyList();
    }
}
