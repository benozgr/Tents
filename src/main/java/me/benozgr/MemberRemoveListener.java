package me.benozgr;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MemberRemoveListener implements Listener {

    private final CadirClaim plugin;
    private final Map<UUID, Claim> pendingRemovals = new HashMap<>();

    public MemberRemoveListener(CadirClaim plugin) {
        this.plugin = plugin;
    }

    public void addPendingRemoval(UUID owner, Claim claim) {
        pendingRemovals.put(owner, claim);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (pendingRemovals.containsKey(playerId)) {
            event.setCancelled(true);

            String targetName = event.getMessage();
            Player target = Bukkit.getPlayer(targetName);

            if (target == null) {
                player.sendMessage(plugin.getMessage("player-not-found", Map.of("player", targetName)));
                return;
            }

            Claim claim = pendingRemovals.get(playerId);

            // Check if the target is a member
            if (!claim.getMembers().contains(target.getUniqueId())) {
                player.sendMessage(plugin.getMessage("not-a-member", Map.of("player", targetName)));
                return;
            }

            if (player.getUniqueId().equals(target.getUniqueId())) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMessage("cannot-remove-self", null)));
                pendingRemovals.remove(playerId);
                return;
            }

            // Remove the member from the claim
            claim.removeMember(target.getUniqueId());
            player.sendMessage(plugin.getMessage("member-removed", Map.of("player", targetName)));
            target.sendMessage(plugin.getMessage("removed-from-claim", Map.of("owner", player.getName())));

            // Unregister this listener
            pendingRemovals.remove(playerId);
        }
    }
}