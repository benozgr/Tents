package me.benozgr;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.SimpleDateFormat;
import java.util.*;

public class TentInteractListener implements Listener {

    private final CadirClaim plugin;
    private final Map<UUID, Tent> pendingNames = new HashMap<>();
    private final Map<UUID, Location> pendingLocations = new HashMap<>();

    public TentInteractListener(CadirClaim plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        UUID playerId = player.getUniqueId();

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;
        Location clickedLocation = clickedBlock.getLocation();

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Claim existingClaim = plugin.getClaimManager().getClaimByTentBlock(clickedLocation);
            if (existingClaim == null) {
                Location aboveLocation = clickedLocation.clone().add(0, 1, 0);
                existingClaim = plugin.getClaimManager().getClaimByTentBlock(aboveLocation);
            }

            if (existingClaim != null) {
                if (existingClaim.getOwner().equals(playerId)) {
                    Date now = new Date();
                    if (existingClaim.getExpirationDate().before(now)) {
                        int gracePeriod = plugin.getConfig().getInt("rent-settings.grace-period", 3);
                        Calendar graceDeadline = Calendar.getInstance();
                        graceDeadline.setTime(existingClaim.getExpirationDate());
                        graceDeadline.add(Calendar.DAY_OF_YEAR, gracePeriod);

                        if (now.after(graceDeadline.getTime())) {
                            Map<String, String> placeholders = Map.of(
                                    "price", String.valueOf(existingClaim.getRentPrice())
                            );
                            player.sendMessage(plugin.getMessage("rent-due", placeholders));
                            // Trigger manual payment
                            player.performCommand("payrent " + existingClaim.getId().toString());
                        } else {
                            player.sendMessage(plugin.getMessage("rent-not-due", null));
                        }
                    } else {
                        new ClaimManagementMenu(plugin).openMenu(player, existingClaim);
                    }
                } else {
                    player.sendMessage(plugin.getMessage("not-claim-owner", null));
                }
                event.setCancelled(true);
                return;
            }

            if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return;

            ItemMeta meta = item.getItemMeta();
            if (meta == null) return;

            NamespacedKey key = new NamespacedKey(plugin, "tent_id");
            String tentId = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (tentId == null) return;

            Tent tent = plugin.getTentFromType(tentId);
            if (tent == null) return;

            // Check overlap first (handled by ClaimManager)
            if (!plugin.getClaimManager().canCreateClaim(clickedLocation, tent.getSize())) {
                plugin.getLogger().info("Overlap check failed for " + player.getName() + " at " + clickedLocation);
                player.sendMessage(plugin.getMessage("claim-overlap", null));
                return;
            }

            ClaimLimits claimLimits = plugin.getClaimLimits();
            long currentClaims = claimLimits.getCurrentClaims(playerId, clickedLocation.getWorld());
            int maxClaims = claimLimits.getMaxClaimsPerWorld();
            plugin.getLogger().info("Limit check for " + player.getName() + ": Current claims = " + currentClaims + ", Max claims = " + maxClaims);
            if (!claimLimits.canCreateClaim(playerId, clickedLocation)) {
                plugin.getLogger().info("Limit check failed for " + player.getName() + " at " + clickedLocation);
                player.sendMessage(plugin.getMessage("limit-reached", Map.of("limit", String.valueOf(maxClaims))));
                return;
            }

            Material tentMaterial = Material.matchMaterial(plugin.getConfig().getString("gui.items." + tentId + ".material", "WHITE_WOOL"));
            Block targetBlock = clickedBlock.getRelative(event.getBlockFace());
            targetBlock.setType(tentMaterial);
            Location tentLocation = targetBlock.getLocation();
            Claim claim = new Claim(playerId, tentLocation, tent);
            plugin.getClaimManager().addClaim(claim);

            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }

            pendingNames.put(playerId, tent);
            pendingLocations.put(playerId, tentLocation);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aBu claim'e ne isim vermek istersin? Sohbete yaz (boşluk yok, maksimum 16 karakter)."));
            event.setCancelled(true);
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (item != null && item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                NamespacedKey key = new NamespacedKey(plugin, "tent_id");
                String tentId = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                if (tentId != null) {
                    Tent tent = plugin.getTentFromType(tentId);
                    if (tent != null) {
                        showClaimBoundaries(player, clickedLocation, tent.getSize());
                        player.sendMessage(plugin.getMessage("claim-preview", Map.of("size", String.valueOf(tent.getSize()))));
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (pendingNames.containsKey(playerId)) {
            event.setCancelled(true);
            String claimName = event.getMessage().trim();
            Tent tent = pendingNames.get(playerId);
            Location tentLocation = pendingLocations.get(playerId);

            // Validate the name: no spaces, max 16 characters
            if (claimName.contains(" ") || claimName.length() > 16) {
                player.sendMessage(ChatColor.RED + "Claim ismi boşluk içeremez ve 16 karakterden uzun olamaz!");

                // Schedule rollback on the main thread
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        // Remove claim
                        Claim claim = plugin.getClaimManager().getClaims(playerId).stream()
                                .filter(c -> c.getLocation().equals(tentLocation))
                                .findFirst().orElse(null);
                        if (claim != null) {
                            plugin.getClaimManager().removeClaim(claim);
                        }
                        // Remove the tent block
                        tentLocation.getBlock().setType(Material.AIR);
                        // Return the fully configured tent item
                        ItemStack tentItem = createTentItem(tent.getType());
                        player.getInventory().addItem(tentItem);
                    }
                }.runTask(plugin);

                pendingNames.remove(playerId);
                pendingLocations.remove(playerId);
                return;
            }

            Claim claim = plugin.getClaimManager().getClaims(playerId).stream()
                    .filter(c -> c.getTent().equals(tent) && c.getLocation().equals(tentLocation))
                    .findFirst().orElse(null);

            if (claim == null) {
                player.sendMessage(ChatColor.RED + "Claim bulunamadı, lütfen tekrar dene.");
                pendingNames.remove(playerId);
                pendingLocations.remove(playerId);
                return;
            }

            claim.setName(claimName);
            player.sendMessage(ChatColor.GREEN + "Claim '" + claimName + "' olarak isimlendirildi!");

            Location hologramLoc = claim.getLocation().clone().add(0.5, 3.0, 0.5);
            String hologramId = "claim_" + playerId + "_" + System.currentTimeMillis();
            List<String> lines = new ArrayList<>();
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");
            for (String line : plugin.getHologramLines()) {
                line = line.replace("%claim-name%", claimName)
                        .replace("%owner%", player.getName())
                        .replace("%expiration-date%", sdf.format(claim.getExpirationDate()));
                lines.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            Hologram hologram = DHAPI.createHologram(hologramId, hologramLoc, lines);
            claim.setHologram(hologram);

            pendingNames.remove(playerId);
            pendingLocations.remove(playerId);
        }
    }

    private ItemStack createTentItem(String tentId) {
        Material material = Material.matchMaterial(plugin.getConfig().getString("gui.items." + tentId + ".material", "WHITE_WOOL"));
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            NamespacedKey key = new NamespacedKey(plugin, "tent_id");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, tentId);

            String displayName = plugin.getConfig().getString("gui.items." + tentId + ".display-name");
            if (displayName != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
            }

            List<String> lore = plugin.getConfig().getStringList("gui.items." + tentId + ".lore");
            if (!lore.isEmpty()) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : lore) {
                    coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(coloredLore);
            }

            item.setItemMeta(meta);
        }
        return item;
    }

    private void showClaimBoundaries(Player player, Location center, int size) {
        Particle particleType = plugin.getParticleType();
        Color particleColor = plugin.getParticleColor();
        Particle.DustOptions dustOptions = particleColor != null ? new Particle.DustOptions(particleColor, 1.0f) : null;

        int halfSize = size / 2;
        for (int y = -3; y <= 3; y++) {
            for (int z = -halfSize; z <= halfSize; z++) {
                spawnParticleSafely(player, particleType, center.clone().add(-halfSize, y, z), dustOptions);
                spawnParticleSafely(player, particleType, center.clone().add(halfSize, y, z), dustOptions);
            }
            for (int x = -halfSize; x <= halfSize; x++) {
                spawnParticleSafely(player, particleType, center.clone().add(x, y, -halfSize), dustOptions);
                spawnParticleSafely(player, particleType, center.clone().add(x, y, halfSize), dustOptions);
            }
        }
    }

    private void spawnParticleSafely(Player player, Particle particle, Location location, Particle.DustOptions dustOptions) {
        try {
            if (particle == Particle.DUST) {
                if (dustOptions != null) {
                    player.spawnParticle(particle, location, 1, dustOptions);
                } else {
                    player.spawnParticle(particle, location, 1, new Particle.DustOptions(Color.WHITE, 1.0f));
                }
            } else {
                player.spawnParticle(particle, location, 1);
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Failed to spawn particle " + particle.name() + " at " + location.toString() + ": " + e.getMessage());
            try {
                player.spawnParticle(Particle.DUST, location, 1);
            } catch (Exception ex) {
                plugin.getLogger().severe("Fallback particle DUST also failed: " + ex.getMessage());
            }
        }
    }
}