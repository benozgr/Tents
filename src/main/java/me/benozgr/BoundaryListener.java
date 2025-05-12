package me.benozgr;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BoundaryListener implements Listener {

    private final CadirClaim plugin;
    private final Map<UUID, Claim> pendingBoundaries = new HashMap<>();

    public BoundaryListener(CadirClaim plugin) {
        this.plugin = plugin;
    }

    public void addPendingBoundary(UUID owner, Claim claim) {
        pendingBoundaries.put(owner, claim);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();

        if (!pendingBoundaries.containsKey(playerId)) return;

        event.setCancelled(true);

        Claim claim = pendingBoundaries.get(playerId);
        showClaimBoundaries(player, claim.getLocation(), claim.getTent().getSize());

        pendingBoundaries.remove(playerId);
    }

    public void showClaimBoundaries(Player player, Location center, int size) {
        // Read particle type and color from config
        ConfigurationSection particleConfig = plugin.getConfig().getConfigurationSection("particle");
        if (particleConfig == null) return;

        // Get the particle type from the config
        String particleTypeString = particleConfig.getString("type", "DUST").toUpperCase();
        Particle particleType;
        try {
            particleType = Particle.valueOf(particleTypeString);
        } catch (IllegalArgumentException e) {
            player.sendMessage("Invalid particle type specified in config. Defaulting to DUST.");
            particleType = Particle.DUST; // Fallback to DUST
        }

        // Get color from config
        int red = particleConfig.getInt("color.red", 255);
        int green = particleConfig.getInt("color.green", 0);
        int blue = particleConfig.getInt("color.blue", 0);
        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.fromRGB(red, green, blue), 1);

        for (int x = -size / 2; x <= size / 2; x++) {
            for (int z = -size / 2; z <= size / 2; z++) {
                if (x == -size / 2 || x == size / 2 || z == -size / 2 || z == size / 2) {
                    Location boundaryLocation = center.clone().add(x, 0, z);
                    player.spawnParticle(particleType, boundaryLocation, 1, dustOptions);
                }
            }
        }
    }
}