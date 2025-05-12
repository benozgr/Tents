package me.benozgr;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.Date;
import java.util.Calendar;

public class ClaimManagementMenu implements Listener {

    private final CadirClaim plugin;
    private final Map<UUID, BukkitRunnable> particleTasks = new HashMap<>();
    private static final Map<UUID, String> CLICK_LOCKS = new HashMap<>();
    private final Map<UUID, Long> lastPromptTime = new HashMap<>();

    public ClaimManagementMenu(CadirClaim plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openMyClaimsMenu(Player player, List<Claim> claims) {
        String title = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("gui-titles.my-claims-menu", "&aÇadırlarım"));
        int size = plugin.getConfig().getInt("gui-titles.my-claims-menu-size", 27);
        Inventory menu = Bukkit.createInventory(null, size, title);

        Date now = new Date();
        for (int i = 0; i < claims.size() && i < size; i++) {
            Claim claim = claims.get(i);
            ItemStack item = createClaimItem(claim);
            menu.setItem(i, item);

            // Add rent payment button for expired claims past grace period
            if (claim.getExpirationDate().before(now)) {
                int gracePeriod = plugin.getConfig().getInt("rent-settings.grace-period", 3);
                Calendar graceDeadline = Calendar.getInstance();
                graceDeadline.setTime(claim.getExpirationDate());
                graceDeadline.add(Calendar.DAY_OF_YEAR, gracePeriod);
                if (now.after(graceDeadline.getTime())) {
                    ItemStack rentItem = createRentItem(claim);
                    menu.setItem(i + 1 < size ? i + 1 : i, rentItem); // Place next to claim item if possible
                }
            }
        }

        // Add back button if configured
        ConfigurationSection backConfig = plugin.getConfig().getConfigurationSection("rent-menu.items.back");
        if (backConfig != null) {
            ItemStack backItem = createMenuItem(backConfig);
            menu.setItem(backConfig.getInt("slot", 26), backItem); // Default to slot 26
        }

        player.openInventory(menu);
    }

    private ItemStack createClaimItem(Claim claim) {
        ConfigurationSection tentConfig = plugin.getConfig().getConfigurationSection("gui.items." + claim.getTentType());
        Material material = Material.matchMaterial(tentConfig != null ? tentConfig.getString("material", "WHITE_WOOL") : "WHITE_WOOL");
        String name = ChatColor.translateAlternateColorCodes('&', tentConfig != null ? tentConfig.getString("name", "&e" + claim.getTentType()) : "&e" + claim.getTentType());

        List<String> lore = new ArrayList<>();
        List<String> defaultLore = plugin.getConfig().getStringList("my-claims-items.lore.default");
        Location loc = claim.getLocation();
        long daysLeft = calculateDaysLeft(claim.getExpirationDate());

        for (String line : defaultLore) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line)
                    .replace("%x%", String.valueOf(loc.getBlockX()))
                    .replace("%y%", String.valueOf(loc.getBlockY()))
                    .replace("%z%", String.valueOf(loc.getBlockZ()))
                    .replace("%days%", String.valueOf(daysLeft)));
        }

        ItemStack item = new ItemStack(material != null ? material : Material.WHITE_WOOL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "claim_id"), PersistentDataType.STRING, claim.getId().toString());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createRentItem(Claim claim) {
        ConfigurationSection rentConfig = plugin.getConfig().getConfigurationSection("rent-menu.items.pay-rent");
        Material material = Material.matchMaterial(rentConfig != null ? rentConfig.getString("material", "GOLD_INGOT") : "GOLD_INGOT");
        String name = ChatColor.translateAlternateColorCodes('&', rentConfig != null ? rentConfig.getString("name", "&eÇadır Kira Ödeme") : "&eÇadır Kira Ödeme");

        List<String> lore = rentConfig != null ? rentConfig.getStringList("lore") : new ArrayList<>();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("price", String.valueOf(claim.getRentPrice()));
        placeholders.put("duration", String.valueOf(claim.getRentDuration()));
        lore.replaceAll(line -> ChatColor.translateAlternateColorCodes('&', plugin.getMessage("rent-menu.items.pay-rent.lore." + lore.indexOf(line), placeholders)));

        ItemStack item = new ItemStack(material != null ? material : Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "claim_id"), PersistentDataType.STRING, claim.getId().toString());
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "rent_action"), PersistentDataType.BYTE, (byte) 1); // Mark as rent action
            item.setItemMeta(meta);
        }
        return item;
    }

    private long calculateDaysLeft(Date expirationDate) {
        long currentTime = System.currentTimeMillis();
        long expirationTime = expirationDate.getTime();
        long diffInMillis = expirationTime - currentTime;
        if (diffInMillis < 0) {
            return 0; // Expired
        }
        return TimeUnit.MILLISECONDS.toDays(diffInMillis);
    }

    public void openMenu(Player player, Claim claim) {
        ConfigurationSection tentConfig = plugin.getConfig().getConfigurationSection("gui.items." + claim.getTentType());
        String tentDisplayName = tentConfig != null ? ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', tentConfig.getString("name", claim.getTentType()))) : claim.getTentType();
        String title = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("gui-titles.claim-management-menu", "&aClaim Yönetimi").replace("%type%", tentDisplayName));
        Inventory menu = Bukkit.createInventory(null, plugin.getConfig().getInt("gui-titles.claim-management-menu-size", 9), title);

        ConfigurationSection itemsConfig = plugin.getConfig().getConfigurationSection("claim-management-items");

        ItemStack editMembers = createMenuItem(itemsConfig.getConfigurationSection("edit-members"));
        ItemStack showLimits = createMenuItem(itemsConfig.getConfigurationSection("show-limits"));
        ItemStack disbandClaim = null;
        if (claim.getOwner().equals(player.getUniqueId())) {
            disbandClaim = createMenuItem(itemsConfig.getConfigurationSection("disband-claim"));
        }

        menu.setItem(itemsConfig.getConfigurationSection("edit-members").getInt("slot"), editMembers);
        menu.setItem(itemsConfig.getConfigurationSection("show-limits").getInt("slot"), showLimits);
        if (disbandClaim != null) {
            menu.setItem(itemsConfig.getConfigurationSection("disband-claim").getInt("slot"), disbandClaim);
        }

        player.openInventory(menu);
    }

    private ItemStack createMenuItem(ConfigurationSection itemConfig) {
        Material material = Material.matchMaterial(itemConfig.getString("material", "STONE"));
        String name = ChatColor.translateAlternateColorCodes('&', itemConfig.getString("name", "&eItem"));
        List<String> lore = itemConfig.getStringList("lore");
        lore.replaceAll(line -> ChatColor.translateAlternateColorCodes('&', line));

        ItemStack item = new ItemStack(material != null ? material : Material.STONE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();
        Inventory clickedInventory = event.getClickedInventory();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedInventory == null || clickedItem == null || !clickedItem.hasItemMeta()) return;

        String title = event.getView().getTitle();
        String myClaimsTitle = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("gui-titles.my-claims-menu", "&aÇadırlarım"));
        String claimManagementTitle = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("gui-titles.claim-management-menu", "&aClaim Yönetimi"));

        if (title.equals(myClaimsTitle)) {
            event.setCancelled(true);
            String claimId = clickedItem.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "claim_id"), PersistentDataType.STRING);
            if (claimId == null) return;

            Claim claim = plugin.getClaimManager().getClaims().values().stream()
                    .flatMap(List::stream)
                    .filter(c -> c.getId().toString().equals(claimId))
                    .findFirst()
                    .orElse(null);

            if (claim == null) return;

            // Handle rent payment action
            NamespacedKey rentKey = new NamespacedKey(plugin, "rent_action");
            if (clickedItem.getItemMeta().getPersistentDataContainer().has(rentKey, PersistentDataType.BYTE) &&
                    clickedItem.getItemMeta().getPersistentDataContainer().get(rentKey, PersistentDataType.BYTE) == 1) {
                attemptRentPayment(player, claim);
                return;
            }

            openMenu(player, claim);
            return;
        }

        if (!title.contains(claimManagementTitle)) return;

        event.setCancelled(true);

        Claim claim = plugin.getClaimManager().getClaimAtLocation(player.getLocation());
        if (claim == null) {
            for (Claim c : plugin.getClaimManager().getClaims(player.getUniqueId())) {
                ConfigurationSection tentConfig = plugin.getConfig().getConfigurationSection("gui.items." + c.getTentType());
                String tentDisplayName = tentConfig != null ? ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', tentConfig.getString("name", c.getTentType()))) : c.getTentType();
                if (title.contains(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("gui.items." + c.getTentType() + ".name", c.getTentType()))))) {
                    claim = c;
                    break;
                }
            }
            if (claim == null) return;
        }

        if (!player.getWorld().equals(claim.getLocation().getWorld())) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMessage("wrong-world", Map.of(
                    "claim-world", claim.getLocation().getWorld().getName(),
                    "player-world", player.getWorld().getName()
            ))));
            player.closeInventory();
            return;
        }

        ConfigurationSection itemsConfig = plugin.getConfig().getConfigurationSection("claim-management-items");
        String editMembersName = ChatColor.translateAlternateColorCodes('&', itemsConfig.getString("edit-members.name"));
        String showLimitsName = ChatColor.translateAlternateColorCodes('&', itemsConfig.getString("show-limits.name"));
        String disbandClaimName = ChatColor.translateAlternateColorCodes('&', itemsConfig.getString("disband-claim.name"));

        String clickedName = clickedItem.getItemMeta().getDisplayName();

        int currentTick = Bukkit.getServer().getCurrentTick();
        String lockKey = currentTick + ":" + event.getSlot();
        synchronized (CLICK_LOCKS) {
            String lastLock = CLICK_LOCKS.get(playerId);
            if (lastLock != null && lastLock.equals(lockKey)) return;
            CLICK_LOCKS.put(playerId, lockKey);
        }

        long currentTime = System.currentTimeMillis();
        long lastPrompt = lastPromptTime.getOrDefault(playerId, 0L);
        if (currentTime - lastPrompt < 100) return;

        if (clickedName.equals(editMembersName)) {
            if (claim.getOwner().equals(playerId) || claim.hasPermission(playerId, "add_members")) {
                new MemberEditMenu(plugin).openMenu(player, claim);
            } else {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMessage("no-permission", null)));
            }
        } else if (clickedName.equals(showLimitsName)) {
            if (event.getClick() != ClickType.LEFT) return;

            BukkitRunnable existingTask = particleTasks.remove(playerId);
            if (existingTask != null) existingTask.cancel();
            startParticleTask(player, claim);
            player.closeInventory();
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMessage("boundaries-shown", null)));
        } else if (clickedName.equals(disbandClaimName)) {
            if (!claim.getOwner().equals(playerId)) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMessage("not-claim-owner", null)));
                player.closeInventory();
                return;
            }

            plugin.getClaimManager().removeClaim(claim);
            claim.getLocation().getBlock().setType(Material.AIR);
            Tent tent = claim.getTent();
            ItemStack tentItem = createTentItem(tent);
            player.getInventory().addItem(tentItem).forEach((index, remainingItem) -> {
                if (remainingItem != null) {
                    player.getWorld().dropItemNaturally(player.getLocation(), remainingItem);
                }
            });

            player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMessage("claim-disbanded", null)));
            player.closeInventory();
        }
    }

    private void startParticleTask(Player player, Claim claim) {
        ConfigurationSection limitsConfig = plugin.getConfig().getConfigurationSection("claim-management-items.show-limits");
        int duration = limitsConfig.getInt("particle-duration", 600);
        int interval = limitsConfig.getInt("particle-interval", 20);

        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= duration) {
                    cancel();
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMessage("boundaries-hidden", null)));
                    particleTasks.remove(player.getUniqueId());
                    return;
                }
                showClaimBoundaries(player, claim.getLocation(), claim.getTent().getSize());
                ticks += interval;
            }
        };
        task.runTaskTimer(plugin, 0, interval);
        particleTasks.put(player.getUniqueId(), task);
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

    private ItemStack createTentItem(Tent tent) {
        ConfigurationSection tentConfig = plugin.getConfig().getConfigurationSection("gui.items." + tent.getType());
        Material material = Material.matchMaterial(tentConfig != null ? tentConfig.getString("material", "WHITE_WOOL") : "WHITE_WOOL");
        String name = ChatColor.translateAlternateColorCodes('&', tentConfig != null ? tentConfig.getString("name", "&e" + tent.getType()) : "&e" + tent.getType());
        List<String> lore = tentConfig != null ? tentConfig.getStringList("lore") : new ArrayList<>();
        lore.replaceAll(line -> ChatColor.translateAlternateColorCodes('&', line)
                .replace("%price%", String.valueOf(tent.getPrice()))
                .replace("%size%", String.valueOf(tent.getSize()))
                .replace("%max_members%", String.valueOf(tent.getMaxMembers()))
                .replace("%duration%", String.valueOf(tent.getDuration())));

        ItemStack item = new ItemStack(material != null ? material : Material.WHITE_WOOL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "tent_id"), PersistentDataType.STRING, tent.getType());
            item.setItemMeta(meta);
        }
        return item;
    }

    private void attemptRentPayment(Player player, Claim claim) {
        if (!claim.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getMessage("not-claim-owner", null));
            return;
        }

        Date now = new Date();
        if (!claim.getExpirationDate().before(now)) {
            player.sendMessage(plugin.getMessage("rent-not-due", null));
            return;
        }

        int gracePeriod = plugin.getConfig().getInt("rent-settings.grace-period", 3);
        Calendar graceDeadline = Calendar.getInstance();
        graceDeadline.setTime(claim.getExpirationDate());
        graceDeadline.add(Calendar.DAY_OF_YEAR, gracePeriod);
        if (!now.after(graceDeadline.getTime())) {
            player.sendMessage(plugin.getMessage("rent-not-due", null));
            return;
        }

        double rentPrice = claim.getRentPrice();
        int rentDuration = claim.getRentDuration();

        if (plugin.getEconomy().has(player, rentPrice)) {
            plugin.getEconomy().withdrawPlayer(player, rentPrice);
            Calendar newExpiration = Calendar.getInstance();
            newExpiration.setTime(now);
            newExpiration.add(Calendar.DAY_OF_YEAR, rentDuration);
            claim.setExpirationDate(newExpiration.getTime());
            claim.resetRentAttempts();
            claim.setLastRentAttempt(now);
            Map<String, String> placeholders = Map.of(
                    "duration", String.valueOf(rentDuration),
                    "price", String.valueOf(rentPrice)
            );
            player.sendMessage(plugin.getMessage("rent-paid", placeholders));
            updateHologram(player, claim);
            player.closeInventory();
        } else {
            claim.incrementRentAttempts();
            claim.setLastRentAttempt(now);
            Map<String, String> placeholders = Map.of(
                    "attempts", String.valueOf(plugin.getConfig().getInt("rent-settings.max-rent-attempts", 3) - claim.getRentAttempts()),
                    "price", String.valueOf(rentPrice)
            );
            player.sendMessage(plugin.getMessage("rent-failed", placeholders));
            if (claim.getRentAttempts() >= plugin.getConfig().getInt("rent-settings.max-rent-attempts", 3)) {
                disbandClaim(player, claim);
            }
        }
    }

    private void disbandClaim(Player player, Claim claim) {
        plugin.getClaimManager().removeClaim(claim.getOwner(), claim.getLocation());
        Map<String, String> placeholders = Map.of("location", claim.getLocation().toString());
        player.sendMessage(plugin.getMessage("claim-disbanded-rent", placeholders));
        claim.getLocation().getBlock().setType(Material.AIR);
        if (claim.getHologram() != null) {
            claim.getHologram().delete();
        }
        player.closeInventory();
    }

    private void updateHologram(Player player, Claim claim) {
        if (claim.getHologram() == null) return;
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");
        List<String> lines = new ArrayList<>();
        for (String line : plugin.getHologramLines()) {
            line = line.replace("%claim-name%", claim.getName())
                    .replace("%owner%", player.getName())
                    .replace("%expiration-date%", sdf.format(claim.getExpirationDate()));
            lines.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        // Use DHAPI to update hologram lines
        DHAPI.setHologramLines(claim.getHologram(), lines);
    }
}