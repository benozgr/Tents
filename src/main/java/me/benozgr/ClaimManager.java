package me.benozgr;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ClaimManager {

    private final JavaPlugin plugin; // Add plugin instance
    private final CadirClaim cadirClaim;
    private final Map<UUID, Claim> claims = new HashMap<>();
    private final File claimsFile;

    public ClaimManager(JavaPlugin plugin, CadirClaim cadirClaim) {
        this.plugin = plugin; // Initialize plugin instance
        this.cadirClaim = cadirClaim;
        this.claimsFile = new File(plugin.getDataFolder(), "claims.yml");
        loadClaims();
    }

    public void addClaim(Claim claim) {
        claims.put(claim.getOwner(), claim);
        saveClaims();
    }

    public void removeClaim(UUID owner) {
        claims.remove(owner);
        saveClaims();
    }

    public Claim getClaim(UUID owner) {
        return claims.get(owner);
    }

    public Map<UUID, Claim> getClaims() {
        return claims;
    }

    public void loadClaims() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(claimsFile);

        for (String key : config.getKeys(false)) {
            UUID owner = UUID.fromString(key);
            String tentType = config.getString(key + ".tentType");
            Location location = (Location) config.get(key + ".location");
            Tent tent = cadirClaim.getTentFromType(tentType);

            if (tent == null) {
                plugin.getLogger().warning("Geçersiz çadır tipi: " + tentType); // Use plugin.getLogger()
                continue;
            }

            Claim claim = new Claim(owner, tentType, location, tent);

            // Load members
            for (String member : config.getStringList(key + ".members")) {
                claim.addMember(UUID.fromString(member));
            }

            // Load admins
            for (String admin : config.getStringList(key + ".admins")) {
                claim.addAdmin(UUID.fromString(admin));
            }

            claims.put(owner, claim);

            // Debug message
            plugin.getLogger().info("Claim yüklendi: " + owner + " - " + tentType + " - " + location); // Use plugin.getLogger()
        }

        plugin.getLogger().info(claims.size() + " adet claim yüklendi."); // Use plugin.getLogger()
    }

    public void saveClaims() {
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<UUID, Claim> entry : claims.entrySet()) {
            String key = entry.getKey().toString();
            Claim claim = entry.getValue();

            config.set(key + ".tentType", claim.getTentType());
            config.set(key + ".location", claim.getLocation());
            config.set(key + ".members", claim.getMembers().stream().map(UUID::toString).toList());
            config.set(key + ".admins", claim.getAdmins().stream().map(UUID::toString).toList());
        }

        try {
            config.save(claimsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("claims.yml kaydedilemedi: " + e.getMessage()); // Use plugin.getLogger()
        }
    }

    public boolean canCreateClaim(Location location, int size) {
        for (Claim claim : claims.values()) {
            if (isOverlapping(location, size, claim)) {
                return false;
            }
        }
        return true;
    }

    public Claim getClaimAtLocation(Location location) {
        for (Claim claim : claims.values()) {
            if (isInClaim(location, claim)) {
                return claim;
            }
        }
        return null;
    }

    private boolean isInClaim(Location location, Claim claim) {
        Location center = claim.getLocation();
        int size = claim.getTent().getSize();

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

    private boolean isOverlapping(Location location, int size, Claim claim) {
        int newMinX = location.getBlockX() - size / 2;
        int newMaxX = location.getBlockX() + size / 2;
        int newMinZ = location.getBlockZ() - size / 2;
        int newMaxZ = location.getBlockZ() + size / 2;

        int claimMinX = claim.getLocation().getBlockX() - claim.getTent().getSize() / 2;
        int claimMaxX = claim.getLocation().getBlockX() + claim.getTent().getSize() / 2;
        int claimMinZ = claim.getLocation().getBlockZ() - claim.getTent().getSize() / 2;
        int claimMaxZ = claim.getLocation().getBlockZ() + claim.getTent().getSize() / 2;

        return newMinX <= claimMaxX && newMaxX >= claimMinX &&
                newMinZ <= claimMaxZ && newMaxZ >= claimMinZ;
    }

    public CadirClaim getPlugin() {
        return cadirClaim;
    }
}