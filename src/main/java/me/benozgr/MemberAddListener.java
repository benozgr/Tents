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

public class MemberAddListener implements Listener {

    private final CadirClaim plugin;
    private final Map<UUID, Claim> pendingInvites = new HashMap<>();

    public MemberAddListener(CadirClaim plugin) {
        this.plugin = plugin;
    }

    public void addPendingInvite(UUID owner, Claim claim) {
        pendingInvites.put(owner, claim);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (pendingInvites.containsKey(playerId)) {
            event.setCancelled(true);

            String targetName = event.getMessage();
            Player target = Bukkit.getPlayer(targetName);

            if (target == null) {
                player.sendMessage(plugin.getMessage("player-not-found", Map.of("player", targetName)));
                return;
            }

            Claim claim = pendingInvites.get(playerId);

            // Check if the target is already a member
            if (claim.getMembers().contains(target.getUniqueId())) {
                player.sendMessage(plugin.getMessage("already-a-member", Map.of("player", targetName)));
                return;
            }

            // Send invite to the target player
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", player.getName());
            target.sendMessage(plugin.getMessage("claim-invite", placeholders));

            // Add a listener for the target's response
            Bukkit.getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void onPlayerResponse(AsyncPlayerChatEvent responseEvent) {
                    if (responseEvent.getPlayer().equals(target)) {
                        responseEvent.setCancelled(true);

                        if (responseEvent.getMessage().equalsIgnoreCase("evet")) {
                            claim.addMember(target.getUniqueId());
                            player.sendMessage(plugin.getMessage("member-added", Map.of("player", targetName)));
                            target.sendMessage(plugin.getMessage("joined-claim", Map.of("owner", player.getName())));
                        } else {
                            player.sendMessage(plugin.getMessage("invite-declined", Map.of("player", targetName)));
                        }

                        // Unregister this listener
                        responseEvent.getHandlers().unregister(this);
                    }
                }
            }, plugin);

            pendingInvites.remove(playerId);
        }
    }
}