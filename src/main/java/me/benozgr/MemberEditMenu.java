package me.benozgr;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class MemberEditMenu implements Listener {

    private final CadirClaim plugin;
    private final Map<UUID, Claim> editingClaims = new HashMap<>();

    public MemberEditMenu(CadirClaim plugin) {
        this.plugin = plugin;
    }

    public void openMemberList(Player player, Claim claim) {
        editingClaims.put(player.getUniqueId(), claim);

        String title = ChatColor.translateAlternateColorCodes('&', "&aÜyeleri Düzenle");
        Inventory menu = Bukkit.createInventory(null, 27, title);

        for (UUID memberId : claim.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                meta.setOwningPlayer(member);
                meta.setDisplayName(ChatColor.YELLOW + member.getName());
                head.setItemMeta(meta);

                menu.addItem(head);
            }
        }

        player.openInventory(menu);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();

        if (!editingClaims.containsKey(playerId)) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        Claim claim = editingClaims.get(playerId);
        Player target = Bukkit.getPlayer(clickedItem.getItemMeta().getDisplayName());

        if (target != null) {
            openPermissionMenu(player, claim, target);
        }
    }

    private void openPermissionMenu(Player player, Claim claim, Player target) {
        String title = ChatColor.translateAlternateColorCodes('&', "&a" + target.getName() + " İzinleri");
        Inventory menu = Bukkit.createInventory(null, 9, title);

        // Add permission toggle items
        // Example: BLOCK_BREAK, BLOCK_PLACE, etc.
        // You can customize this based on your needs.

        player.openInventory(menu);
    }
}