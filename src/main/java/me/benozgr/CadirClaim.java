package me.benozgr;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class CadirClaim extends JavaPlugin {

    private final Map<String, Tent> tents = new HashMap<>();
    private Economy economy;
    private ClaimManager claimManager;
    private ClaimLimits claimLimits;
    private TentPurchaseMenu tentPurchaseMenu; // Declare TentPurchaseMenu

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().severe("Vault bulunamadı. Plugin devredışı");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        loadTents();

        claimManager = new ClaimManager(this, this); // Pass 'this' (CadirClaim instance)
        claimLimits = new ClaimLimits(claimManager);

        // Load claims from claims.yml
        claimManager.loadClaims();

        // Initialize TentPurchaseMenu
        tentPurchaseMenu = new TentPurchaseMenu(this);
        getServer().getPluginManager().registerEvents(tentPurchaseMenu, this);

        // Register other listeners and commands
        getServer().getPluginManager().registerEvents(new ClaimEnterListener(claimManager), this);
        getServer().getPluginManager().registerEvents(new ClaimPreview(this), this);
        getServer().getPluginManager().registerEvents(new ClaimPurchase(economy, this), this);
        getServer().getPluginManager().registerEvents(new ClaimProtectionListener(claimManager), this);
        getServer().getPluginManager().registerEvents(new TentInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new MemberAddListener(this), this);
        getServer().getPluginManager().registerEvents(new MemberRemoveListener(this), this);
        getServer().getPluginManager().registerEvents(new BoundaryListener(this), this);

        getCommand("çadır").setExecutor(new CadirCommand(this));
        getCommand("çadırım").setExecutor(new MemberCommands(claimManager));

        getServer().getScheduler().runTaskTimer(this, new ExpirationTask(claimManager, this), 0, 20 * 60 * 60);

        getLogger().info("CadirClaim etkin.");
    }

    @Override
    public void onDisable() {

        claimManager.saveClaims();
        getLogger().info("CadirClaim devredışı.");
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    private void loadTents() {
        FileConfiguration config = getConfig();
        ConfigurationSection tentsSection = config.getConfigurationSection("tents");

        if (tentsSection == null) {
            getLogger().warning("Konfigürasyonda çadır tipleri bulunamadı.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        for (String key : tentsSection.getKeys(false)) {
            String name = key;
            double price = tentsSection.getDouble(key + ".price");
            int size = tentsSection.getInt(key + ".size");
            int maxMembers = tentsSection.getInt(key + ".max_members");
            int duration = tentsSection.getInt(key + ".duration");

            tents.put(name, new Tent(name, price, size, maxMembers, duration));
        }

        getLogger().info("Konfigürasyondan " + tents.size() + " adet çadır yüklendi.");
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
            return Color.RED; // Default color
        }
        int red = colorSection.getInt("red", 255);
        int green = colorSection.getInt("green", 0);
        int blue = colorSection.getInt("blue", 0);
        return Color.fromRGB(red, green, blue);
    }
}