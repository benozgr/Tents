package me.benozgr;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;

public class ClaimEnterListener implements Listener {

    private final ClaimManager claimManager;

    public ClaimEnterListener(ClaimManager claimManager) {
        this.claimManager = claimManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        Location from = event.getFrom();

        if (to == null) return;

        Claim enterClaim = claimManager.getClaimAtLocation(to);
        Claim exitClaim = claimManager.getClaimAtLocation(from);

        // Player entered a claim
        if (enterClaim != null) {
            String ownerName = Bukkit.getOfflinePlayer(enterClaim.getOwner()).getName();
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("owner", ownerName != null ? ownerName : "Bilinmiyor");

            String enterMessage = claimManager.getPlugin().getConfig().getString("action-bar-messages.enter-claim", "&aClaim sahibi: &e%owner%");
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                enterMessage = enterMessage.replace("%" + entry.getKey() + "%", entry.getValue());
            }

            int duration = claimManager.getPlugin().getConfig().getInt("action-bar-messages.duration", 3) * 20; // Convert seconds to ticks
            player.sendActionBar(ChatColor.translateAlternateColorCodes('&', enterMessage));
            Bukkit.getScheduler().runTaskLater(claimManager.getPlugin(), () -> player.sendActionBar(""), duration);
        }

        // Player exited a claim
        if (exitClaim != null && (enterClaim == null || !enterClaim.getOwner().equals(exitClaim.getOwner()))) {
            String ownerName = Bukkit.getOfflinePlayer(exitClaim.getOwner()).getName();
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("owner", ownerName != null ? ownerName : "Bilinmiyor");

            String exitMessage = claimManager.getPlugin().getConfig().getString("action-bar-messages.exit-claim", "&e%owner% &aclaim'inden çıkıldı");
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                exitMessage = exitMessage.replace("%" + entry.getKey() + "%", entry.getValue());
            }

            int duration = claimManager.getPlugin().getConfig().getInt("action-bar-messages.duration", 3) * 20; // Convert seconds to ticks
            player.sendActionBar(ChatColor.translateAlternateColorCodes('&', exitMessage));
            Bukkit.getScheduler().runTaskLater(claimManager.getPlugin(), () -> player.sendActionBar(""), duration);
        }
    }
}