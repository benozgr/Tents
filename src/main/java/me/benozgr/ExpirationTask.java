package me.benozgr;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

public class ExpirationTask implements Runnable {

    private final ClaimManager claimManager;
    private final CadirClaim plugin;

    public ExpirationTask(ClaimManager claimManager, CadirClaim plugin) {
        this.claimManager = claimManager;
        this.plugin = plugin;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        for (UUID owner : new HashSet<>(claimManager.getClaims().keySet())) {
            Claim claim = claimManager.getClaim(owner);
            if (claim.getExpirationDate() <= now) {
                claimManager.removeClaim(owner);

                // Send expiration message to the owner
                Player player = Bukkit.getPlayer(owner);
                if (player != null) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("location", claim.getLocation().getBlockX() + ", " + claim.getLocation().getBlockY() + ", " + claim.getLocation().getBlockZ());
                    player.sendMessage(plugin.getMessage("claim-expired", placeholders));
                }
            }
        }
    }
}

