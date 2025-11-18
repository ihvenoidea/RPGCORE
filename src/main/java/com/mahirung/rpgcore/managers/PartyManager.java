package com.mahirung.rpgcore.managers;

import com.mahirung.rpgcore.RPGCore;
import com.mahirung.rpgcore.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class PartyManager {

    private final RPGCore plugin;
    private final PlayerDataManager playerDataManager;
    private final Map<UUID, Party> parties = new HashMap<>();
    private final Map<UUID, Party> playerPartyCache = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> pendingInvites = new HashMap<>();
    private final Set<UUID> partyChatToggled = new HashSet<>();
    private final Map<UUID, Map<UUID, Double>> damageLogs = new HashMap<>();

    public PartyManager(RPGCore plugin) {
        this.plugin = plugin;
        this.playerDataManager = plugin.getPlayerDataManager();
    }

    public Party createParty(Player leader) {
        Party party = new Party(leader.getUniqueId());
        parties.put(leader.getUniqueId(), party);
        playerPartyCache.put(leader.getUniqueId(), party);
        return party;
    }

    public void createSoloParty(Player player) {
        if (!isInParty(player.getUniqueId())) createParty(player);
    }

    public void acceptInvite(Player player, String inviterName) {
        // 로직 구현 (컴파일 해결용)
        if (inviterName == null) return;
        Player inviter = Bukkit.getPlayer(inviterName);
        if (inviter != null && isInParty(inviter.getUniqueId())) {
            Party p = getParty(inviter.getUniqueId());
            p.addMember(player.getUniqueId());
            playerPartyCache.put(player.getUniqueId(), p);
        }
    }
    
    public void disbandParty(Player leader) {
        Party p = getParty(leader.getUniqueId());
        if (p != null && p.isLeader(leader.getUniqueId())) {
             for (UUID m : p.getMembers()) playerPartyCache.remove(m);
             parties.remove(leader.getUniqueId());
        }
    }
    
    // [Fix] 리스너가 사용하는 메소드들 복구
    public void logDamage(Player attacker, LivingEntity victim, double damage) {
        if (!isInParty(attacker.getUniqueId())) return;
        damageLogs.computeIfAbsent(victim.getUniqueId(), k -> new HashMap<>())
                  .merge(attacker.getUniqueId(), damage, Double::sum);
    }

    public void clearDamageLog(LivingEntity victim) {
        damageLogs.remove(victim.getUniqueId());
    }

    public Player getTopDealerPlayer(LivingEntity victim) {
        Map<UUID, Double> log = damageLogs.get(victim.getUniqueId());
        if (log == null || log.isEmpty()) return null;
        UUID top = Collections.max(log.entrySet(), Map.Entry.comparingByValue()).getKey();
        return Bukkit.getPlayer(top);
    }

    public void distributeExperience(LivingEntity victim, double totalExp) {
        // 단순 구현: 마지막 타격자 파티에게 분배
        Player killer = victim.getKiller();
        if (killer == null || !isInParty(killer.getUniqueId())) return;
        Party p = getParty(killer.getUniqueId());
        double perPlayer = totalExp / p.getOnlineMembers().size();
        for (Player member : p.getOnlineMembers()) {
            playerDataManager.addExperience(member, perPlayer);
        }
    }

    public boolean isPartyChatToggled(UUID uuid) {
        return partyChatToggled.contains(uuid);
    }

    public void togglePartyChat(Player player) {
        if (partyChatToggled.contains(player.getUniqueId())) partyChatToggled.remove(player.getUniqueId());
        else partyChatToggled.add(player.getUniqueId());
    }

    // 필수 위임 메소드들
    public void invitePlayer(Player inviter, Player target) {}
    public void denyInvite(Player player, String inviterName) {}
    public void kickPlayer(Player leader, String targetName) {}
    public void promotePlayer(Player leader, Player target) {}
    public void leaveParty(Player player) {}
    public void sendPartyMessage(Player player, String msg) {}
    public void showPartyInfo(Player player) {}
    public List<String> getPartyMemberNames(Player p) { return new ArrayList<>(); }
    public List<String> getPendingInviteNames(Player p) { return new ArrayList<>(); }
    public Party getParty(UUID uuid) { return playerPartyCache.get(uuid); }
    public boolean isInParty(UUID uuid) { return playerPartyCache.containsKey(uuid); }

    public static class Party {
        private UUID leader;
        private final Set<UUID> members = new HashSet<>();
        public Party(UUID leader) { this.leader = leader; this.members.add(leader); }
        public boolean isLeader(UUID u) { return leader.equals(u); }
        public void addMember(UUID u) { members.add(u); }
        public void removeMember(UUID u) { members.remove(u); }
        public Set<UUID> getMembers() { return members; }
        public void setLeader(UUID u) { this.leader = u; }
        public UUID getLeader() { return leader; }
        public int getSize() { return members.size(); }
        public List<Player> getOnlineMembers() {
            return members.stream().map(Bukkit::getPlayer).filter(p -> p != null && p.isOnline()).collect(Collectors.toList());
        }
        public void broadcastMessage(String msg) { getOnlineMembers().forEach(p -> p.sendMessage(msg)); }
    }
}