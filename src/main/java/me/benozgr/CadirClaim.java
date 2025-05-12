package me.benozgr;

import net.milkbowl.vault.economy.Economy;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class CadirClaim extends JavaPlugin implements Listener {

    private final Map<String, Tent> tents = new HashMap<>();
    private Economy economy;
    private ClaimManager claimManager;
    private ClaimLimits claimLimits;
    private TentPurchaseMenu tentPurchaseMenu;
    private ClaimManagementMenu claimManagementMenu;
    private MemberEditMenu memberEditMenu;
    private final Map<UUID, PendingInvite> pendingInvites = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        getConfig().addDefault("claim-limits.max-claims-per-world", 3);
        getConfig().addDefault("claim-limits.min-distance-between-claims", 100);
        getConfig().addDefault("messages.limit-reached", "&cBu dünyada zaten maksimum çadır sayısına ulaştın! (Limit: %limit%)");
        getConfig().addDefault("messages.too-close", "&cBu konum başka bir claim'e çok yakın! (Minimum mesafe: %distance%)");
        getConfig().addDefault("messages.purchase-success", "&aÇadır başarıyla satın alındı ve yerleştirildi!");
        getConfig().addDefault("messages.not-enough-money", "&cYeterli paran yok!");
        getConfig().addDefault("economy.use-custom-provider", false);
        getConfig().addDefault("economy.starting-balance", 500.0);
        getConfig().addDefault("economy.currency-name-singular", "Tent Coin");
        getConfig().addDefault("economy.currency-name-plural", "Tent Coins");
        getConfig().options().copyDefaults(true);
        saveConfig();

        setupEconomy();

        loadTents();

        claimManager = new ClaimManager(this, this);
        claimLimits = new ClaimLimits(claimManager, this);

        claimManager.loadClaims();

        tentPurchaseMenu = new TentPurchaseMenu(this);
        claimManagementMenu = new ClaimManagementMenu(this);
        memberEditMenu = new MemberEditMenu(this);

        getServer().getPluginManager().registerEvents(tentPurchaseMenu, this);
        getServer().getPluginManager().registerEvents(claimManagementMenu, this);
        getServer().getPluginManager().registerEvents(memberEditMenu, this);
        getServer().getPluginManager().registerEvents(new ClaimInteractListener(claimManager), this);
        getServer().getPluginManager().registerEvents(new ClaimPreview(this), this);
        getServer().getPluginManager().registerEvents(new ClaimProtectionListener(claimManager), this);
        getServer().getPluginManager().registerEvents(new TentInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new MemberAddListener(this), this);
        getServer().getPluginManager().registerEvents(new BoundaryListener(this), this);
        getServer().getPluginManager().registerEvents(new TentBlockProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(this, this);

        getCommand("çadır").setExecutor(new CadirCommand(this));
        getCommand("çadıryardım").setExecutor(new CadirHelpCommand(this));
        getCommand("eco").setExecutor(new EcoCommand(this));

        new ExpirationTask(claimManager, this).runTaskTimer(this, 0L, 20L * 60L * 60L);

        getLogger().info("CadirClaim etkin.");
    }

    @Override
    public void onDisable() {
        if (claimManager != null) {
            claimManager.saveClaims();
        }
        getLogger().info("CadirClaim devredışı.");
    }

    private void setupEconomy() {
        Economy existingEconomy = getServer().getServicesManager().getRegistration(Economy.class) != null
                ? getServer().getServicesManager().getRegistration(Economy.class).getProvider()
                : null;

        boolean useCustomProvider = getConfig().getBoolean("economy.use-custom-provider", false);

        if (existingEconomy != null && !useCustomProvider) {
            getLogger().warning("Başka bir ekonomi sağlayıcısı bulundu: " + existingEconomy.getName() + ". CadirClaim kendi ekonomisini kullanmayacak.");
            getLogger().info("CadirClaim mevcut sağlayıcıyı kullanıyor: " + existingEconomy.getName());
            economy = existingEconomy;
        } else {
            economy = new CustomEconomyProvider(this, getConfig());
            getServer().getServicesManager().register(Economy.class, economy, this, ServicePriority.Lowest);
            getLogger().info("CadirClaim kendi ekonomi sağlayıcısını kullanıyor: " + economy.getName());
        }
    }

    private void loadTents() {
        FileConfiguration config = getConfig();
        ConfigurationSection tentsSection = config.getConfigurationSection("tents");

        if (tentsSection == null) {
            getLogger().severe("Tents section not found in config.yml!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        tents.clear();
        for (String tentId : tentsSection.getKeys(false)) {
            ConfigurationSection tentSection = tentsSection.getConfigurationSection(tentId);
            if (tentSection == null) {
                getLogger().warning("Invalid configuration for tent: " + tentId);
                continue;
            }

            if (!tentSection.contains("price") || !tentSection.contains("size") ||
                    !tentSection.contains("max_members") || !tentSection.contains("duration")) {
                getLogger().warning("Missing required fields for tent: " + tentId);
                continue;
            }

            double price = tentSection.getDouble("price");
            int size = tentSection.getInt("size");
            int maxMembers = tentSection.getInt("max_members");
            int duration = tentSection.getInt("duration");

            if (price <= 0 || size <= 0 || maxMembers <= 0 || duration <= 0) {
                getLogger().warning("Invalid values for tent: " + tentId + " (values must be positive)");
                continue;
            }

            tents.put(tentId, new Tent(tentId, price, size, maxMembers, duration));
            getLogger().info("Loaded tent: " + tentId);
        }

        if (tents.isEmpty()) {
            getLogger().severe("No valid tents loaded from config.yml!");
            getServer().getPluginManager().disablePlugin(this);
        } else {
            getLogger().info("Successfully loaded " + tents.size() + " tents.");
        }
    }

    public Map<String, Tent> getTents() {
        return tents;
    }

    public Tent getTentFromType(String tentType) {
        return tents.get(tentType);
    }

    public Tent getTentFromItem(ItemStack item) {
        for (Tent tent : tents.values()) {
            if (item.getType().name().toLowerCase().contains(tent.getName().toLowerCase())) {
                return tent;
            }
        }
        return null;
    }

    public String getMessage(String key, Map<String, String> placeholders) {
        String message = getConfig().getString("messages." + key, "&cMesaj bulunamadı: " + key);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public TentPurchaseMenu getTentPurchaseMenu() {
        return tentPurchaseMenu;
    }

    public Economy getEconomy() {
        return economy;
    }

    public ClaimManager getClaimManager() {
        return claimManager;
    }

    public ClaimLimits getClaimLimits() {
        return claimLimits;
    }

    public Particle getParticleType() {
        String particleName = getConfig().getString("particle.type", "DUST");
        try {
            return Particle.valueOf(particleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            getLogger().warning("Sınır efekti geçersiz, varsayılan 'DUST' efekti kullanılıyor.");
            return Particle.DUST;
        }
    }

    public Color getParticleColor() {
        ConfigurationSection colorSection = getConfig().getConfigurationSection("particle.color");
        if (colorSection == null) {
            return Color.RED;
        }
        int red = colorSection.getInt("red", 255);
        int green = colorSection.getInt("green", 0);
        int blue = colorSection.getInt("blue", 0);
        return Color.fromRGB(red, green, blue);
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String command = event.getMessage().toLowerCase();

        if (command.equals("/çadırlarım")) {
            event.setCancelled(true);
            List<Claim> playerClaims = claimManager.getClaims(playerId);
            if (playerClaims.isEmpty()) {
                player.sendMessage(getMessage("no-claim", null));
                return;
            }
            claimManagementMenu.openMyClaimsMenu(player, playerClaims);
            return;
        }

        if (command.startsWith("/çadır kira")) {
            event.setCancelled(true);
            List<Claim> playerClaims = claimManager.getClaims(playerId);
            if (playerClaims.isEmpty()) {
                player.sendMessage(getMessage("no-claim", null));
                return;
            }
            claimManagementMenu.openMyClaimsMenu(player, playerClaims);
            return;
        }

        if (command.startsWith("/çadır ekle")) {
            event.setCancelled(true);
            String[] args = command.split(" ");
            if (args.length != 3) {
                player.sendMessage(ChatColor.RED + "Kullanım: /çadır ekle <oyuncu>");
                return;
            }

            Claim claim = claimManager.getClaimAtLocation(player.getLocation());
            if (claim == null || (!claim.getOwner().equals(playerId) && !claim.hasPermission(playerId, "add_members"))) {
                player.sendMessage(ChatColor.RED + "Bu claim'de üye ekleme yetkiniz yok veya bir claim içinde değilsiniz!");
                return;
            }

            String targetName = args[2];
            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Oyuncu bulunamadı: " + targetName);
                return;
            }

            if (player.getUniqueId().equals(target.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Kendinizi claim'e ekleyemezsiniz!");
                return;
            }

            if (claim.getMembers().contains(target.getUniqueId())) {
                player.sendMessage(ChatColor.RED + targetName + " zaten claim'de bir üye!");
                return;
            }

            pendingInvites.remove(player.getUniqueId());
            addPendingInvite(player, target, claim);

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", player.getName());
            String message = getMessage("add-member-request", placeholders);
            TextComponent component = new TextComponent(message);
            component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/çadır accept " + player.getName()));
            target.spigot().sendMessage(component);

            player.sendMessage(ChatColor.translateAlternateColorCodes('&', getMessage("add-member-request-sent", Map.of("player", target.getName()))));

            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (pendingInvites.containsKey(player.getUniqueId())) {
                    PendingInvite invite = pendingInvites.get(player.getUniqueId());
                    if (invite.getInvitedPlayer().equals(target.getUniqueId())) {
                        pendingInvites.remove(player.getUniqueId());
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', getMessage("add-member-timeout", null)));
                        target.sendMessage(ChatColor.translateAlternateColorCodes('&', getMessage("add-member-timeout-self", Map.of("player", player.getName()))));
                    }
                }
            }, 600L);
            return;
        }

        if (command.startsWith("/çadır at")) {
            event.setCancelled(true);
            String[] args = command.split(" ");
            if (args.length != 3) {
                player.sendMessage(ChatColor.RED + "Kullanım: /çadır at <oyuncu>");
                return;
            }

            Claim claim = claimManager.getClaimAtLocation(player.getLocation());
            if (claim == null || (!claim.getOwner().equals(playerId) && !claim.hasPermission(playerId, "add_members"))) {
                player.sendMessage(ChatColor.RED + "Bu claim'de üye çıkarma yetkiniz yok veya bir claim içinde değilsiniz!");
                return;
            }

            String targetName = args[2];
            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Oyuncu bulunamadı: " + targetName);
                return;
            }

            if (player.getUniqueId().equals(target.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Kendinizi claim'den çıkaramazsınız!");
                return;
            }

            if (!claim.getMembers().contains(target.getUniqueId())) {
                player.sendMessage(ChatColor.RED + targetName + " bu claim'de bir üye değil!");
                return;
            }

            claim.removeMember(target.getUniqueId());
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", target.getName());
            player.sendMessage(getMessage("member-removed", placeholders));
            placeholders.put("player", player.getName());
            target.sendMessage(getMessage("member-removed-self", placeholders));
            return;
        }

        if (command.startsWith("/çadır accept")) {
            event.setCancelled(true);
            String[] args = command.split(" ");
            if (args.length != 3) {
                player.sendMessage(ChatColor.RED + "Kullanım: /çadır accept <davetçi>");
                return;
            }

            String inviterName = args[2];
            Player inviter = Bukkit.getPlayer(inviterName);
            if (inviter == null) {
                player.sendMessage(ChatColor.RED + "Davetçi oyuncu bulunamadı: " + inviterName);
                return;
            }

            UUID inviterId = inviter.getUniqueId();
            PendingInvite invite = pendingInvites.get(inviterId);
            if (invite == null) {
                player.sendMessage(ChatColor.RED + "Bu oyuncudan aktif bir davet bulunmuyor!");
                return;
            }

            if (!invite.getInvitedPlayer().equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Bu davet size ait değil!");
                return;
            }

            Claim claim = invite.getClaim();
            claim.addMember(player.getUniqueId());

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", player.getName());
            inviter.sendMessage(getMessage("member-added", placeholders));
            placeholders.put("player", inviter.getName());
            player.sendMessage(getMessage("member-added-self", placeholders));

            pendingInvites.remove(inviterId);
            return;
        }

        if (tentPurchaseMenu.hasPendingPurchase(playerId)) {
            tentPurchaseMenu.cancelPendingPurchase(playerId, player);
            player.sendMessage(getMessage("purchase-cancelled", null));
        }

        if (memberEditMenu.hasPendingPermissionEdit(playerId) || memberEditMenu.hasPendingRemoveMember(playerId)) {
            memberEditMenu.cancelPendingPermissionEdit(playerId);
            memberEditMenu.cancelPendingRemoveMember(playerId);
            player.sendMessage(getMessage("permission-edit-cancelled", null));
        }
    }

    public List<String> getHologramLines() {
        return getConfig().getStringList("hologram.lines");
    }

    public void addPendingInvite(Player inviter, Player invited, Claim claim) {
        pendingInvites.put(inviter.getUniqueId(), new PendingInvite(inviter.getUniqueId(), invited.getUniqueId(), claim));
    }

    private static class PendingInvite {
        private final UUID inviter;
        private final UUID invitedPlayer;
        private final Claim claim;

        public PendingInvite(UUID inviter, UUID invitedPlayer, Claim claim) {
            this.inviter = inviter;
            this.invitedPlayer = invitedPlayer;
            this.claim = claim;
        }

        public UUID getInviter() {
            return inviter;
        }

        public UUID getInvitedPlayer() {
            return invitedPlayer;
        }

        public Claim getClaim() {
            return claim;
        }
    }
}