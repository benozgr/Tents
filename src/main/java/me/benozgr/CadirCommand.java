package me.benozgr;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.ConfigurationSection;

import java.text.SimpleDateFormat;
import java.util.*;

public class CadirCommand implements CommandExecutor, Listener {

    private final CadirClaim plugin;

    public CadirCommand(CadirClaim plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMessage("only-players", null)));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            openMainMenu(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("satın-al")) {
            new TentPurchaseMenu(plugin).openMenu(player);
            return true;
        } else if (subCommand.equals("çadırlarım")) {
            openMyClaimsMenu(player);
            return true;
        }

        return false;
    }

    private void openMainMenu(Player player) {
        ConfigurationSection menuConfig = plugin.getConfig().getConfigurationSection("gui-titles");
        String title = ChatColor.translateAlternateColorCodes('&', menuConfig.getString("main-menu", "&aÇadır Menüsü"));
        Inventory menu = Bukkit.createInventory(null, menuConfig.getInt("main-menu-size", 9), title);

        ConfigurationSection itemsConfig = plugin.getConfig().getConfigurationSection("main-menu-items");

        ItemStack purchaseTent = createMenuItem(itemsConfig.getConfigurationSection("purchase-tent"));
        ItemStack myClaims = createMenuItem(itemsConfig.getConfigurationSection("my-claims"));

        menu.setItem(itemsConfig.getConfigurationSection("purchase-tent").getInt("slot"), purchaseTent);
        menu.setItem(itemsConfig.getConfigurationSection("my-claims").getInt("slot"), myClaims);

        player.openInventory(menu);
    }

    public void openMyClaimsMenu(Player player) {
        List<Claim> playerClaims = plugin.getClaimManager().getClaims(player.getUniqueId());
        if (playerClaims.isEmpty()) {
            player.sendMessage(plugin.getMessage("no-tent", null));
            return;
        }

        String title = plugin.getConfig().getString("gui-titles.my-claims-menu", "&aÇadırlarım");
        int size = plugin.getConfig().getInt("gui-titles.my-claims-menu-size", 27);
        Inventory menu = Bukkit.createInventory(null, size, ChatColor.translateAlternateColorCodes('&', title));

        for (Claim claim : playerClaims) {
            ItemStack claimItem = new ItemStack(Material.MAP);
            ItemMeta meta = claimItem.getItemMeta();

            String displayName = ChatColor.translateAlternateColorCodes('&', "&e" + claim.getName());
            meta.setDisplayName(displayName);

            List<String> lore = new ArrayList<>();
            ConfigurationSection loreConfig = plugin.getConfig().getConfigurationSection("my-claims-items.lore");
            if (loreConfig != null) {
                for (String line : loreConfig.getStringList("default")) {
                    line = line.replace("%x%", String.valueOf(claim.getLocation().getBlockX()))
                            .replace("%y%", String.valueOf(claim.getLocation().getBlockY()))
                            .replace("%z%", String.valueOf(claim.getLocation().getBlockZ()))
                            .replace("%days%", getRemainingDays(claim.getExpirationDate()));
                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
            }
            meta.setLore(lore);
            claimItem.setItemMeta(meta);
            menu.addItem(claimItem);
        }

        player.openInventory(menu);
    }

    private ItemStack createMenuItem(ConfigurationSection itemConfig) {
        Material material = Material.matchMaterial(itemConfig.getString("material", "STONE"));
        String name = ChatColor.translateAlternateColorCodes('&', itemConfig.getString("name", "&eItem"));
        List<String> lore = itemConfig.getStringList("lore");
        lore.replaceAll(line -> ChatColor.translateAlternateColorCodes('&', line));

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String mainMenuTitle = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("gui-titles.main-menu", "&aÇadır Menüsü"));
        String myClaimsTitle = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("gui-titles.my-claims-menu", "&aÇadırlarım"));

        if (!event.getView().getTitle().equals(mainMenuTitle) && !event.getView().getTitle().equals(myClaimsTitle)) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        ConfigurationSection itemsConfig = plugin.getConfig().getConfigurationSection("main-menu-items");
        String purchaseTentName = ChatColor.translateAlternateColorCodes('&', itemsConfig.getString("purchase-tent.name"));
        String myClaimsName = ChatColor.translateAlternateColorCodes('&', itemsConfig.getString("my-claims.name"));

        String clickedName = clickedItem.getItemMeta().getDisplayName();

        if (event.getView().getTitle().equals(mainMenuTitle)) {
            if (clickedName.equals(purchaseTentName)) {
                new TentPurchaseMenu(plugin).openMenu(player);
            } else if (clickedName.equals(myClaimsName)) {
                openMyClaimsMenu(player);
            }
        } else if (event.getView().getTitle().equals(myClaimsTitle)) {
            List<Claim> playerClaims = plugin.getClaimManager().getClaims(player.getUniqueId());
            int slot = event.getSlot();
            if (slot >= 0 && slot < playerClaims.size()) {
                Claim selectedClaim = playerClaims.get(slot);
                new ClaimManagementMenu(plugin).openMenu(player, selectedClaim);
            }
        }
    }

    private String getRemainingDays(Date expirationDate) {
        if (expirationDate == null) return "N/A";
        long diff = expirationDate.getTime() - System.currentTimeMillis();
        if (diff <= 0) return "Bitti";
        long days = diff / (1000 * 60 * 60 * 24);
        return String.valueOf(days);
    }
}