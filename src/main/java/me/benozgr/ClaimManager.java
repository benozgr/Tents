package me.benozgr;

import eu.decentsoftware.holograms.api.DHAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class ClaimManager {

    private final JavaPlugin plugin;
    private final CadirClaim cadirClaim;
    private final Map<UUID, List<Claim>> claims = new HashMap<>();
    private final Map<Location, Claim> tentBlocks = new HashMap<>();
    private final File claimsFile;

    public ClaimManager(JavaPlugin plugin, CadirClaim cadirClaim) {
        this.plugin = plugin;
        this.cadirClaim = cadirClaim;
        this.claimsFile = new File(plugin.getDataFolder(), "claims.yml");
        loadClaims();
    }

    public void addClaim(Claim claim) {
        UUID owner = claim.getOwner();
        claims.computeIfAbsent(owner, k -> new ArrayList<>()).add(claim);
        tentBlocks.put(claim.getLocation(), claim);
        saveClaims();
    }

    public void removeClaim(Claim claim) {
        UUID owner = claim.getOwner();
        List<Claim> playerClaims = claims.get(owner);
        if (playerClaims != null) {
            playerClaims.remove(claim);
            tentBlocks.remove(claim.getLocation());
            if (claim.getHologram() != null) {
                DHAPI.removeHologram(claim.getHologram().getId());
            }
            if (playerClaims.isEmpty()) {
                claims.remove(owner);
            }
            saveClaims();
        }
    }

    public void removeClaim(UUID owner, Location location) {
        List<Claim> playerClaims = claims.get(owner);
        if (playerClaims != null) {
            Claim claimToRemove = null;
            for (Claim claim : playerClaims) {
                if (claim.getLocation().equals(location)) {
                    claimToRemove = claim;
                    break;
                }
            }
            if (claimToRemove != null) {
                removeClaim(claimToRemove);
                plugin.getLogger().info("Claim removed for owner " + owner + " at location " + location);
            }
        }
    }

    public List<Claim> getClaims(UUID owner) {
        return claims.getOrDefault(owner, Collections.emptyList());
    }

    public Map<UUID, List<Claim>> getClaims() {
        return claims;
    }

    public void loadClaims() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(claimsFile);
        claims.clear();
        tentBlocks.clear();

        for (String key : config.getKeys(false)) {
            UUID owner;
            try {
                owner = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Geçersiz UUID: " + key);
                continue;
            }
            ConfigurationSection claimsSection = config.getConfigurationSection(key + ".claims");
            if (claimsSection == null) continue;

            List<Claim> playerClaims = new ArrayList<>();
            for (String claimKey : claimsSection.getKeys(false)) {
                String tentType = claimsSection.getString(claimKey + ".tentType");
                Location location = (Location) claimsSection.get(claimKey + ".location");
                String name = claimsSection.getString(claimKey + ".name", "Unnamed Claim");
                Tent tent = cadirClaim.getTentFromType(tentType);

                if (tent == null) {
                    plugin.getLogger().warning("Geçersiz çadır tipi: " + tentType);
                    continue;
                }

                Claim claim = new Claim(owner, location, tent);
                claim.setName(name);

                String expirationStr = claimsSection.getString(claimKey + ".expirationDate");
                if (expirationStr != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    try {
                        claim.setExpirationDate(sdf.parse(expirationStr));
                    } catch (Exception e) {
                        plugin.getLogger().warning("Geçersiz expirationDate: " + expirationStr + " için " + claimKey);
                    }
                }

                ConfigurationSection permsSection = claimsSection.getConfigurationSection(claimKey + ".memberPermissions");
                if (permsSection != null) {
                    for (String memberKey : permsSection.getKeys(false)) {
                        UUID memberId = UUID.fromString(memberKey);
                        List<String> permissions = permsSection.getStringList(memberKey);
                        for (String perm : permissions) {
                            claim.setPermission(memberId, perm, true);
                        }
                    }
                }

                Location hologramLoc = claim.getLocation().clone().add(0.5, 3.0, 0.5);
                String hologramId = "claim_" + owner + "_" + claimKey;
                List<String> lines = new ArrayList<>();
                String ownerName = Bukkit.getOfflinePlayer(owner).getName();
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy"); // Updated format
                for (String line : cadirClaim.getHologramLines()) {
                    line = line.replace("%claim-name%", name)
                            .replace("%owner%", ownerName != null ? ownerName : "Unknown")
                            .replace("%expiration-date%", sdf.format(claim.getExpirationDate()));
                    lines.add(ChatColor.translateAlternateColorCodes('&', line));
                }

                eu.decentsoftware.holograms.api.holograms.Hologram existingHologram = DHAPI.getHologram(hologramId);
                if (existingHologram != null) {
                    if (!existingHologram.getLocation().equals(hologramLoc)) {
                        DHAPI.removeHologram(hologramId);
                        existingHologram = DHAPI.createHologram(hologramId, hologramLoc, lines);
                    } else {
                        DHAPI.setHologramLines(existingHologram, lines);
                    }
                } else {
                    existingHologram = DHAPI.createHologram(hologramId, hologramLoc, lines);
                }
                claim.setHologram(existingHologram);

                playerClaims.add(claim);
                tentBlocks.put(location, claim);
            }

            if (!playerClaims.isEmpty()) {
                claims.put(owner, playerClaims);
                plugin.getLogger().info("Claims yüklendi: " + owner + " - " + playerClaims.size() + " claims");
            }
        }

        plugin.getLogger().info(claims.size() + " oyuncunun claimleri yüklendi.");
    }

    public void saveClaims() {
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<UUID, List<Claim>> entry : claims.entrySet()) {
            String key = entry.getKey().toString();
            List<Claim> playerClaims = entry.getValue();

            for (int i = 0; i < playerClaims.size(); i++) {
                Claim claim = playerClaims.get(i);
                String claimKey = "claim_" + i;
                config.set(key + ".claims." + claimKey + ".tentType", claim.getTentType());
                config.set(key + ".claims." + claimKey + ".location", claim.getLocation());
                config.set(key + ".claims." + claimKey + ".name", claim.getName());
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                config.set(key + ".claims." + claimKey + ".expirationDate", sdf.format(claim.getExpirationDate()));
                Map<String, List<String>> serializedPermissions = new HashMap<>();
                for (Map.Entry<UUID, Set<String>> permEntry : claim.getMemberPermissions().entrySet()) {
                    serializedPermissions.put(permEntry.getKey().toString(), new ArrayList<>(permEntry.getValue()));
                }
                config.set(key + ".claims." + claimKey + ".memberPermissions", serializedPermissions);
            }
        }

        try {
            config.save(claimsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("claims.yml kaydedilemedi: " + e.getMessage());
        }
    }

    public boolean canCreateClaim(Location location, int size) {
        for (List<Claim> claimList : claims.values()) {
            for (Claim claim : claimList) {
                if (isOverlapping(location, size, claim)) {
                    return false;
                }
            }
        }
        return true;
    }

    public Claim getClaimAtLocation(Location location) {
        for (List<Claim> claimList : claims.values()) {
            for (Claim claim : claimList) {
                if (isInClaim(location, claim)) {
                    return claim;
                }
            }
        }
        return null;
    }

    public Claim getClaimByTentBlock(Location location) {
        return tentBlocks.get(location);
    }

    private boolean isInClaim(Location location, Claim claim) {
        Location center = claim.getLocation();
        int size = claim.getTent().getSize();

        int minX = center.getBlockX() - size / 2;
        int maxX = center.getBlockX() + size / 2;
        int minZ = center.getBlockZ() - size / 2;
        int maxZ = center.getBlockZ() + size / 2;

        boolean inXZ = location.getBlockX() >= minX && location.getBlockX() <= maxX &&
                location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;
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
        int claimMinZ = claim.getLocation().getBlockZ() - size / 2;
        int claimMaxZ = claim.getLocation().getBlockZ() + size / 2;

        return newMinX <= claimMaxX && newMaxX >= claimMinX &&
                newMinZ <= claimMaxZ && newMaxZ >= claimMinZ;
    }

    public CadirClaim getPlugin() {
        return cadirClaim;
    }
}