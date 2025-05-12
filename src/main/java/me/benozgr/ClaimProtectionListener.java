package me.benozgr;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.Cancellable;

import java.util.List;

public class ClaimProtectionListener implements Listener {

    private final ClaimManager claimManager;

    public ClaimProtectionListener(ClaimManager claimManager) {
        this.claimManager = claimManager;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        handleClaimProtection(event.getPlayer(), event.getBlock().getLocation(), event, "BLOCK_BREAK");
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        handleClaimProtection(event.getPlayer(), event.getBlock().getLocation(), event, "BLOCK_PLACE");
    }

    private void handleClaimProtection(Player player, Location location, Cancellable event, String permission) {
        for (List<Claim> claimList : claimManager.getClaims().values()) {
            for (Claim claim : claimList) {
                if (isInClaim(location, claim) && !claim.getOwner().equals(player.getUniqueId())) {
                    // Allow players with add_members permission to bypass protection (equivalent to old admin role)
                    if (claim.hasPermission(player.getUniqueId(), "add_members")) {
                        return;
                    }

                    if (!claim.hasPermission(player.getUniqueId(), permission)) {
                        event.setCancelled(true);
                        player.sendMessage(claimManager.getPlugin().getMessage("cannot-build-here", null));
                        return;
                    }
                }
            }
        }
    }

    private boolean isInClaim(Location location, Claim claim) {
        int size = claim.getTent().getSize();
        Location center = claim.getLocation();
        int minX = center.getBlockX() - size / 2;
        int maxX = center.getBlockX() + size / 2;
        int minZ = center.getBlockZ() - size / 2;
        int maxZ = center.getBlockZ() + size / 2;

        // Check X and Z coordinates
        boolean inXZ = location.getBlockX() >= minX && location.getBlockX() <= maxX &&
                location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;

        // Check Y coordinates (entire height of the world)
        boolean inY = location.getBlockY() >= -64 && location.getBlockY() <= 320;

        return inXZ && inY;
    }
}