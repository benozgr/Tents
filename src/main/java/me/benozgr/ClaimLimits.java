package me.benozgr;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;

public class ClaimLimits {

    private final ClaimManager claimManager;
    private final int maxClaimsPerWorld;
    private final int minDistance;
    private final CadirClaim plugin;

    public ClaimLimits(ClaimManager claimManager, CadirClaim plugin) {
        this.claimManager = claimManager;
        this.plugin = plugin;
        // Ensure maxClaimsPerWorld is at least 1, default to 3 if not specified
        this.maxClaimsPerWorld = Math.max(1, plugin.getConfig().getInt("claim-limits.max-claims-per-world", 3));
        // Ensure minDistance is at least 0, default to 100 if not specified
        this.minDistance = Math.max(0, plugin.getConfig().getInt("claim-limits.min-distance-between-claims", 100));

        // Log warnings if the configured values were adjusted
        if (plugin.getConfig().getInt("claim-limits.max-claims-per-world", 3) <= 0) {
            plugin.getLogger().warning("claim-limits.max-claims-per-world pozitif olmalı. varsayılan değer kullanılıyor: " + this.maxClaimsPerWorld);
        }
        if (plugin.getConfig().getInt("claim-limits.min-distance-between-claims", 100) < 0) {
            plugin.getLogger().warning("claim-limits.min-distance-between-claims pozitif olmalı. varsayılan değer kullanılıyor: " + this.minDistance);
        }
    }

    public boolean canCreateClaim(UUID playerId, Location location) {
        // Check per-world limit and minimum distance
        return checkClaimLimit(playerId, location) && checkMinimumDistance(location);
    }

    public boolean checkClaimLimit(UUID playerId, Location location) {
        World world = location.getWorld();
        if (world == null) return false;

        long playerClaimsInWorld = getCurrentClaims(playerId, world);
        return playerClaimsInWorld < maxClaimsPerWorld;
    }

    public long getCurrentClaims(UUID playerId, World world) {
        return claimManager.getClaims().values().stream()
                .flatMap(List::stream)
                .filter(claim -> claim.getOwner().equals(playerId))
                .filter(claim -> claim.getLocation().getWorld() != null && claim.getLocation().getWorld().equals(world))
                .count();
    }

    public boolean checkMinimumDistance(Location location) {
        World world = location.getWorld();
        if (world == null) return false;

        for (Claim claim : claimManager.getClaims().values().stream().flatMap(List::stream).toList()) {
            if (claim.getLocation().getWorld() == null) continue;
            if (claim.getLocation().getWorld().equals(world) &&
                    claim.getLocation().distance(location) < minDistance) {
                return false;
            }
        }

        return true;
    }

    public int getMaxClaimsPerWorld() {
        return maxClaimsPerWorld;
    }

    public int getMinDistanceBetweenClaims() {
        return minDistance;
    }
}