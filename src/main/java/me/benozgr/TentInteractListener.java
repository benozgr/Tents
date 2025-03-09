package me.benozgr;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TentInteractListener implements Listener {

    private final CadirClaim plugin;
    private final Map<UUID, Location> pendingClaims = new HashMap<>();

    public TentInteractListener(CadirClaim plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // Check if the item is a tent
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        // Get the tent type from the item's display name
        String tentName = ChatColor.stripColor(meta.getDisplayName());
        Tent tent = plugin.getTentFromType(tentName);
        if (tent == null) return;

        Location clickedLocation = event.getClickedBlock() != null ? event.getClickedBlock().getLocation() : player.getLocation();

        // Left-click: Show boundaries
        if (event.getAction().toString().contains("LEFT_CLICK")) {
            showClaimBoundaries(player, clickedLocation, tent.getSize());
            pendingClaims.put(player.getUniqueId(), clickedLocation);
        }

        // Right-click: Confirm claim
        if (event.getAction().toString().contains("RIGHT_CLICK")) {
            if (!pendingClaims.containsKey(player.getUniqueId())) {
                player.sendMessage(plugin.getMessage("no-boundary-selected", null));
                return;
            }

            Location claimLocation = pendingClaims.get(player.getUniqueId());
            if (plugin.getClaimManager().canCreateClaim(claimLocation, tent.getSize())) {
                player.sendMessage(plugin.getMessage("claim-confirmed", null));

                // Create the claim
                Claim claim = new Claim(player.getUniqueId(), tent.getName(), claimLocation, tent);
                plugin.getClaimManager().addClaim(claim);

                // Remove the tent item from the player's inventory
                item.setAmount(item.getAmount() - 1);
                pendingClaims.remove(player.getUniqueId());
            } else {
                player.sendMessage(plugin.getMessage("claim-overlap", null));
            }
        }
    }

    private void showClaimBoundaries(Player player, Location center, int size) {
        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.RED, 1);
        for (int x = -size / 2; x <= size / 2; x++) {
            for (int z = -size / 2; z <= size / 2; z++) {
                if (x == -size / 2 || x == size / 2 || z == -size / 2 || z == size / 2) {
                    Location boundaryLocation = center.clone().add(x, 0, z);
                    player.spawnParticle(Particle.DUST, boundaryLocation, 1, dustOptions);
                }
            }
        }
    }
}