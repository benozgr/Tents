package me.benozgr;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class TentPurchaseMenu implements Listener {

    private final CadirClaim plugin;
    private final Map<UUID, Tent> pendingPurchases = new HashMap<>();
    private final Map<UUID, BukkitRunnable> particleTasks = new HashMap<>();

    public TentPurchaseMenu(CadirClaim plugin) {
        this.plugin = plugin;
    }

    public void openMenu(Player player) {
        ConfigurationSection guiConfig = plugin.getConfig().getConfigurationSection("gui");
        if (guiConfig == null) return;

        String title = ChatColor.translateAlternateColorCodes('&', guiConfig.getString("title", "&aÇadır Satın Al"));
        Inventory menu = Bukkit.createInventory(null, 9, title);

        ConfigurationSection itemsConfig = guiConfig.getConfigurationSection("items");
        if (itemsConfig == null) return;

        for (String tentKey : itemsConfig.getKeys(false)) {
            ConfigurationSection itemConfig = itemsConfig.getConfigurationSection(tentKey);
            if (itemConfig == null) continue;

            int slot = itemConfig.getInt("slot");
            Material material = Material.matchMaterial(itemConfig.getString("material", "WHITE_WOOL"));
            String name = ChatColor.translateAlternateColorCodes('&', itemConfig.getString("name", "&eÇadır"));
            List<String> lore = new ArrayList<>();

            Tent tent = plugin.getTentFromType(tentKey);
            if (tent != null) {
                for (String line : itemConfig.getStringList("lore")) {
                    line = line.replace("%price%", String.valueOf(tent.getPrice()))
                            .replace("%size%", String.valueOf(tent.getSize()))
                            .replace("%max_members%", String.valueOf(tent.getMaxMembers()))
                            .replace("%duration%", String.valueOf(tent.getDuration()));
                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
            }

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(name);
            meta.setLore(lore);

            NamespacedKey key = new NamespacedKey(plugin, "tent_id");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, tentKey);

            item.setItemMeta(meta);
            menu.setItem(slot, item);
        }

        player.openInventory(menu);
    }

    private void openConfirmationMenu(Player player, Tent tent) {
        ConfigurationSection confirmConfig = plugin.getConfig().getConfigurationSection("confirm-menu");
        if (confirmConfig == null) {
            player.sendMessage(ChatColor.RED + "Confirmation menu configuration not found!");
            return;
        }

        String title = ChatColor.translateAlternateColorCodes('&', confirmConfig.getString("title", "&eSatın Almayı Onayla"));
        int size = confirmConfig.getInt("size", 9);
        Inventory menu = Bukkit.createInventory(null, size, title);

        ConfigurationSection itemsConfig = confirmConfig.getConfigurationSection("items");
        if (itemsConfig == null) {
            player.sendMessage(ChatColor.RED + "Confirmation menu items configuration not found!");
            return;
        }

        // Accept button
        ConfigurationSection acceptConfig = itemsConfig.getConfigurationSection("accept");
        if (acceptConfig != null) {
            int slot = acceptConfig.getInt("slot");
            Material material = Material.matchMaterial(acceptConfig.getString("material", "LIME_DYE"));
            String name = ChatColor.translateAlternateColorCodes('&', acceptConfig.getString("name", "&aSatın Almayı Onayla"));
            List<String> lore = new ArrayList<>();
            for (String line : acceptConfig.getStringList("lore")) {
                line = line.replace("%price%", String.valueOf(tent.getPrice()));
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "action"), PersistentDataType.STRING, "accept_purchase");
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "tent_id"), PersistentDataType.STRING, tent.getName());
            item.setItemMeta(meta);
            menu.setItem(slot, item);
        }

        // Deny button
        ConfigurationSection denyConfig = itemsConfig.getConfigurationSection("deny");
        if (denyConfig != null) {
            int slot = denyConfig.getInt("slot");
            Material material = Material.matchMaterial(denyConfig.getString("material", "RED_DYE"));
            String name = ChatColor.translateAlternateColorCodes('&', denyConfig.getString("name", "&cSatın Almayı İptal Et"));
            List<String> lore = new ArrayList<>();
            for (String line : denyConfig.getStringList("lore")) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "action"), PersistentDataType.STRING, "deny_purchase");
            item.setItemMeta(meta);
            menu.setItem(slot, item);
        }

        player.openInventory(menu);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        String purchaseMenuTitle = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("gui.title", "&aÇadır Satın Al"));
        String confirmMenuTitle = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("confirm-menu.title", "&eSatın Almayı Onayla"));

        // Handle clicks in the purchase menu
        if (event.getView().getTitle().equals(purchaseMenuTitle)) {
            event.setCancelled(true);

            NamespacedKey key = new NamespacedKey(plugin, "tent_id");
            ItemMeta meta = clickedItem.getItemMeta();
            String tentId = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);

            if (tentId == null) {
                plugin.getLogger().warning("Tent ID is null for clicked item: " + clickedItem.getType());
                player.sendMessage(plugin.getMessage("invalid-tent-selection", null));
                return;
            }

            plugin.getLogger().info("Tent ID retrieved: " + tentId);

            Tent tent = plugin.getTentFromType(tentId);
            if (tent == null) {
                plugin.getLogger().warning("Tent type not recognized: " + tentId);
                player.sendMessage(plugin.getMessage("invalid-tent-selection", null));
                return;
            }

            pendingPurchases.put(player.getUniqueId(), tent);
            startParticleTask(player, tent);
            player.closeInventory();
            openConfirmationMenu(player, tent);
        }
        // Handle clicks in the confirmation menu
        else if (event.getView().getTitle().equals(confirmMenuTitle)) {
            event.setCancelled(true);

            ItemMeta meta = clickedItem.getItemMeta();
            NamespacedKey actionKey = new NamespacedKey(plugin, "action");
            String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);

            if (action == null) return;

            if (action.equals("accept_purchase")) {
                NamespacedKey tentKey = new NamespacedKey(plugin, "tent_id");
                String tentId = meta.getPersistentDataContainer().get(tentKey, PersistentDataType.STRING);
                Tent tent = plugin.getTentFromType(tentId);

                if (tent == null) {
                    player.sendMessage(plugin.getMessage("invalid-tent-selection", null));
                    return;
                }

                if (plugin.getEconomy().has(player, tent.getPrice())) {
                    plugin.getEconomy().withdrawPlayer(player, tent.getPrice());

                    // Use the configured material for the tent block
                    String tentName = tent.getName();
                    String materialPath = "gui.items." + tentName + ".material";
                    String materialString = plugin.getConfig().getString(materialPath, "WHITE_WOOL");
                    plugin.getLogger().info("Purchasing tent: " + tentName + ", material path: " + materialPath + ", material: " + materialString);

                    Material tentMaterial = Material.matchMaterial(materialString);
                    if (tentMaterial == null) {
                        tentMaterial = Material.WHITE_WOOL; // Fallback if material is invalid
                        plugin.getLogger().warning("Invalid material '" + materialString + "' for tent " + tentName + " in config. Using WHITE_WOOL as fallback.");
                    }

                    // Check claim limits and distance using ClaimLimits
                    ClaimLimits claimLimits = plugin.getClaimLimits();
                    if (!claimLimits.checkClaimLimit(player.getUniqueId(), player.getLocation())) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMessage("limit-reached", Map.of("limit", String.valueOf(claimLimits.getMaxClaimsPerWorld())))));
                        pendingPurchases.remove(player.getUniqueId());
                        stopParticleTask(player);
                        player.closeInventory();
                        return;
                    }

                    if (!claimLimits.checkMinimumDistance(player.getLocation())) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMessage("too-close", Map.of("distance", String.valueOf(claimLimits.getMinDistanceBetweenClaims())))));
                        pendingPurchases.remove(player.getUniqueId());
                        stopParticleTask(player);
                        player.closeInventory();
                        return;
                    }

                    // Check if the block below the player is solid
                    Location blockLocation = player.getLocation().subtract(0, 1, 0);
                    if (!blockLocation.getBlock().getType().isSolid()) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cÇadır burada yerleştirilemez, zemin sağlam değil!"));
                        pendingPurchases.remove(player.getUniqueId());
                        stopParticleTask(player);
                        player.closeInventory();
                        return;
                    }

                    // Create a new claim
                    Claim claim = new Claim(player.getUniqueId(), player.getLocation(), tent);
                    plugin.getClaimManager().addClaim(claim);

                    // Place the tent block (below the player)
                    blockLocation.getBlock().setType(tentMaterial);

                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("price", String.valueOf(tent.getPrice()));
                    player.sendMessage(plugin.getMessage("purchase-success", placeholders));
                } else {
                    player.sendMessage(plugin.getMessage("not-enough-money", null));
                }

                pendingPurchases.remove(player.getUniqueId());
                stopParticleTask(player);
                player.closeInventory();
            } else if (action.equals("deny_purchase")) {
                player.sendMessage(plugin.getMessage("purchase-cancelled", null));
                pendingPurchases.remove(player.getUniqueId());
                stopParticleTask(player);
                player.closeInventory();
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        pendingPurchases.remove(playerId);
        stopParticleTask(player);
    }

    private void startParticleTask(Player player, Tent tent) {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                showClaimBoundaries(player, player.getLocation(), tent.getSize());
            }
        };
        task.runTaskTimer(plugin, 0, 20);
        particleTasks.put(player.getUniqueId(), task);
    }

    private void stopParticleTask(Player player) {
        BukkitRunnable task = particleTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMessage("boundaries-hidden", null)));
        }
    }

    private void showClaimBoundaries(Player player, Location center, int size) {
        Particle particleType = plugin.getParticleType();
        Color particleColor = plugin.getParticleColor();
        Particle.DustOptions dustOptions = particleColor != null ? new Particle.DustOptions(particleColor, 1.0f) : null;

        int halfSize = size / 2;
        // Show particles only on the vertical sides (left, right, front, back)
        for (int y = -3; y <= 3; y++) {
            // Left face (X = -halfSize)
            for (int z = -halfSize; z <= halfSize; z++) {
                spawnParticleSafely(player, particleType, center.clone().add(-halfSize, y, z), dustOptions);
            }
            // Right face (X = +halfSize)
            for (int z = -halfSize; z <= halfSize; z++) {
                spawnParticleSafely(player, particleType, center.clone().add(halfSize, y, z), dustOptions);
            }
            // Front face (Z = -halfSize)
            for (int x = -halfSize; x <= halfSize; x++) {
                spawnParticleSafely(player, particleType, center.clone().add(x, y, -halfSize), dustOptions);
            }
            // Back face (Z = +halfSize)
            for (int x = -halfSize; x <= halfSize; x++) {
                spawnParticleSafely(player, particleType, center.clone().add(x, y, halfSize), dustOptions);
            }
        }
    }

    private void spawnParticleSafely(Player player, Particle particle, Location location, Particle.DustOptions dustOptions) {
        try {
            // Only REDSTONE and DUST_COLOR_TRANSITION (1.17+) support DustOptions for color and size
            if (particle == Particle.DUST || particle == Particle.DUST_COLOR_TRANSITION) {
                if (dustOptions != null) {
                    player.spawnParticle(particle, location, 1, dustOptions);
                } else {
                    // Fallback to a default color if dustOptions is null
                    player.spawnParticle(particle, location, 1, new Particle.DustOptions(Color.WHITE, 1.0f));
                }
            } else {
                // For particles that don't support DustOptions (e.g., FLAME, SMOKE_NORMAL), spawn without extra data
                player.spawnParticle(particle, location, 1);
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Failed to spawn particle " + particle.name() + " at " + location.toString() + ": " + e.getMessage());
            // Fallback to a default particle (FLAME) if the configured one fails
            try {
                player.spawnParticle(Particle.FLAME, location, 1);
            } catch (Exception ex) {
                plugin.getLogger().severe("Fallback particle FLAME also failed: " + ex.getMessage());
            }
        }
    }

    // Public methods for CadirClaim to access
    public boolean hasPendingPurchase(UUID playerId) {
        return pendingPurchases.containsKey(playerId);
    }

    public void cancelPendingPurchase(UUID playerId, Player player) {
        pendingPurchases.remove(playerId);
        stopParticleTask(player);
    }
}