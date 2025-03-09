package me.benozgr;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
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

            // Store the tent ID in the item's metadata
            NamespacedKey key = new NamespacedKey(plugin, "tent_id");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, tentKey);

            item.setItemMeta(meta);
            menu.setItem(slot, item);
        }

        player.openInventory(menu);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("gui.title", "&aÇadır Satın Al")))) {
            return;
        }

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        // Get the tent ID from the item's metadata
        NamespacedKey key = new NamespacedKey(plugin, "tent_id");
        ItemMeta meta = clickedItem.getItemMeta();
        String tentId = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);

        if (tentId == null) {
            plugin.getLogger().warning("Geçersiz çadır tipi: " + tentId);
            player.sendMessage(plugin.getMessage("invalid-tent-selection", null));
            return;
        }

        // Get the tent from the config
        Tent tent = plugin.getTentFromType(tentId);
        if (tent == null) {
            player.sendMessage(plugin.getMessage("invalid-tent-selection", null));
            return;
        }

        // Add the tent to pending purchases
        pendingPurchases.put(player.getUniqueId(), tent);
        startParticleTask(player, tent);
        player.closeInventory();

        // Send confirmation message
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("price", String.valueOf(tent.getPrice()));
        player.sendMessage(plugin.getMessage("confirm-purchase", placeholders));
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().toLowerCase();

        if (pendingPurchases.containsKey(player.getUniqueId())) {
            if (message.equals("onayla")) {
                Tent tent = pendingPurchases.get(player.getUniqueId());
                pendingPurchases.remove(player.getUniqueId());

                stopParticleTask(player);

                if (plugin.getEconomy().has(player, tent.getPrice())) {
                    plugin.getEconomy().withdrawPlayer(player, tent.getPrice());

                    // Create the tent item
                    ItemStack tentItem = new ItemStack(Material.WHITE_WOOL); // Default material
                    ItemMeta meta = tentItem.getItemMeta();

                    // Set the display name from the config
                    String displayName = plugin.getConfig().getString("gui.items." + tent.getName() + ".name", "&e" + tent.getName());
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));

                    // Set the lore from the config
                    List<String> lore = new ArrayList<>();
                    for (String line : plugin.getConfig().getStringList("gui.items." + tent.getName() + ".lore")) {
                        line = line.replace("%price%", String.valueOf(tent.getPrice()))
                                .replace("%size%", String.valueOf(tent.getSize()))
                                .replace("%max_members%", String.valueOf(tent.getMaxMembers()))
                                .replace("%duration%", String.valueOf(tent.getDuration()));
                        lore.add(ChatColor.translateAlternateColorCodes('&', line));
                    }
                    meta.setLore(lore);

                    // Set the item meta
                    tentItem.setItemMeta(meta);

                    // Add the item to the player's inventory
                    player.getInventory().addItem(tentItem);

                    // Send confirmation message
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("price", String.valueOf(tent.getPrice()));
                    player.sendMessage(plugin.getMessage("claim-purchased", placeholders));
                } else {
                    player.sendMessage(plugin.getMessage("not-enough-money", null));
                }
            } else {
                player.sendMessage(plugin.getMessage("purchase-cancelled", null));
                pendingPurchases.remove(player.getUniqueId());
                stopParticleTask(player);
            }
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        pendingPurchases.remove(playerId);

        BukkitRunnable task = particleTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
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