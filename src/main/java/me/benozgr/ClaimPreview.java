package me.benozgr;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class ClaimPreview implements Listener {

    private final CadirClaim plugin;

    public ClaimPreview(CadirClaim plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // Check if the player is holding a tent item
        if (item == null || !item.getType().name().contains("WOOL")) return;

        // Check if the player is right-clicking a block
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        // Get tent data from config
        Tent tent = plugin.getTentFromItem(item);
        if (tent == null) return;

        // Show claim preview
        showClaimPreview(player, clickedBlock.getLocation(), tent);
    }

    private void showClaimPreview(Player player, Location location, Tent tent) {
        int size = tent.getSize();

        // Highlight the claim area with wool
        for (int x = -size / 2; x <= size / 2; x++) {
            for (int z = -size / 2; z <= size / 2; z++) {
                Location blockLocation = location.clone().add(x, 0, z);
                player.sendBlockChange(blockLocation, Material.WHITE_WOOL.createBlockData());
            }
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("size", String.valueOf(size));
        player.sendMessage(plugin.getMessage("claim-preview", placeholders));
    }
}
