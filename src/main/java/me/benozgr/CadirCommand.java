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
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CadirCommand implements CommandExecutor, Listener {

    private final CadirClaim plugin;

    public CadirCommand(CadirClaim plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessage("only-players", null));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            openMainMenu(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "satın-al":
                plugin.getTentPurchaseMenu().openMenu(player);
                break;
            case "çadırlarım":
                openMyClaimsMenu(player);
                break;
            default:
                player.sendMessage(plugin.getMessage("invalid-subcommand", null));
                break;
        }

        return true;
    }

    private void openMainMenu(Player player) {
        String title = plugin.getConfig().getString("gui-titles.main-menu", "&aÇadır Menüsü");
        int size = plugin.getConfig().getInt("gui-titles.main-menu-size", 9);
        Inventory menu = Bukkit.createInventory(null, size, ChatColor.translateAlternateColorCodes('&', title));

        ConfigurationSection purchaseItemConfig = plugin.getConfig().getConfigurationSection("main-menu-items.purchase-tent");
        if (purchaseItemConfig != null) {
            ItemStack purchaseItem = createMenuItem(purchaseItemConfig);
            int slot = purchaseItemConfig.getInt("slot", 2);
            menu.setItem(slot, purchaseItem);
        }

        ConfigurationSection myClaimsItemConfig = plugin.getConfig().getConfigurationSection("main-menu-items.my-claims");
        if (myClaimsItemConfig != null) {
            ItemStack myClaimsItem = createMenuItem(myClaimsItemConfig);
            int slot = myClaimsItemConfig.getInt("slot", 6);
            menu.setItem(slot, myClaimsItem);
        }

        player.openInventory(menu);
    }

    private void openMyClaimsMenu(Player player) {
        String title = plugin.getConfig().getString("gui-titles.my-claims-menu", "&aÇadırlarım");
        int size = plugin.getConfig().getInt("gui-titles.my-claims-menu-size", 27);
        Inventory menu = Bukkit.createInventory(null, size, ChatColor.translateAlternateColorCodes('&', title));

        Map<UUID, Claim> claims = plugin.getClaimManager().getClaims();
        for (Claim claim : claims.values()) {
            if (claim.getOwner().equals(player.getUniqueId())) {
                ItemStack claimItem = new ItemStack(Material.MAP);
                ItemMeta meta = claimItem.getItemMeta();

                String tentType = claim.getTentType();
                String displayName = plugin.getConfig().getString("gui.items." + tentType + ".name", "&e" + tentType);
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));

                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Koordinatlar: " + ChatColor.WHITE +
                        claim.getLocation().getBlockX() + ", " +
                        claim.getLocation().getBlockY() + ", " +
                        claim.getLocation().getBlockZ());
                lore.add(ChatColor.GRAY + "Süre: " + ChatColor.WHITE +
                        (claim.getExpirationDate() - System.currentTimeMillis()) / (1000 * 60 * 60 * 24) + " gün kaldı");
                meta.setLore(lore);

                claimItem.setItemMeta(meta);
                menu.addItem(claimItem);
            }
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
        Inventory clickedInventory = event.getClickedInventory();
        ItemStack clickedItem = event.getCurrentItem();

        // Get the InventoryView
        InventoryView view = event.getView();

        // Check if the clicked inventory is the main menu
        String mainMenuTitle = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("gui-titles.main-menu", "&aÇadır Menüsü"));
        if (clickedInventory != null && mainMenuTitle.equals(view.getTitle())) {
            event.setCancelled(true); // Prevent taking items

            if (clickedItem == null || !clickedItem.hasItemMeta()) return;

            // Check which item was clicked
            String purchaseItemName = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("main-menu-items.purchase-tent.name", "&eÇadır Satın Al"));
            String myClaimsItemName = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("main-menu-items.my-claims.name", "&eÇadırlarım"));

            String clickedItemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
            if (clickedItemName.equals(ChatColor.stripColor(purchaseItemName))) {
                plugin.getTentPurchaseMenu().openMenu(player);
            } else if (clickedItemName.equals(ChatColor.stripColor(myClaimsItemName))) {
                openMyClaimsMenu(player);
            }
        }
    }
}