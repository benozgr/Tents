package me.benozgr;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClaimInteractListener implements Listener {

    private final ClaimManager claimManager;
    private final Map<UUID, Long> lastMessageSent = new HashMap<>(); // Track last message time to avoid spam

    public ClaimInteractListener(ClaimManager claimManager) {
        this.claimManager = claimManager;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();
        sendClaimMessage(player, location);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();
        sendClaimMessage(player, location);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getClickedBlock() == null) return;

        Location location = event.getClickedBlock().getLocation();
        sendClaimMessage(player, location);
    }

    private void sendClaimMessage(Player player, Location location) {
        Claim claim = claimManager.getClaimAtLocation(location);
        if (claim == null) return;

        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastSent = lastMessageSent.get(playerId);

        // Only send the message if 3 seconds have passed since the last message to avoid spam
        if (lastSent != null && (currentTime - lastSent) < 3000) {
            return;
        }

        String ownerName = Bukkit.getOfflinePlayer(claim.getOwner()).getName();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("owner", ownerName != null ? ownerName : "Bilinmiyor");

        String enterMessage = claimManager.getPlugin().getConfig().getString("action-bar-messages.enter-claim", "&aClaim sahibi: &e%owner%");
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            enterMessage = enterMessage.replace("%" + entry.getKey() + "%", entry.getValue());
        }

        int duration = claimManager.getPlugin().getConfig().getInt("action-bar-messages.duration", 3) * 20; // Convert seconds to ticks
        player.sendActionBar(ChatColor.translateAlternateColorCodes('&', enterMessage));
        Bukkit.getScheduler().runTaskLater(claimManager.getPlugin(), () -> player.sendActionBar(""), duration);

        lastMessageSent.put(playerId, currentTime);
    }
}