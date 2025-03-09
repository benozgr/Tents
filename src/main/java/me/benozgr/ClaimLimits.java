package me.benozgr;

import org.bukkit.Location;

import java.util.*;

public class ClaimLimits {

    private final ClaimManager claimManager;

    public ClaimLimits(ClaimManager claimManager) {
        this.claimManager = claimManager;
    }

    public boolean canCreateClaim(Location location) {
        // Check max claims per world
        if (claimManager.getClaims().size() >= 3) {
            return false;
        }

        // Check distance between claims
        for (Claim claim : claimManager.getClaims().values()) {
            if (claim.getLocation().distance(location) < 100) {
                return false;
            }
        }

        return true;
    }
}