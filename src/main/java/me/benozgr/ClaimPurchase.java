package me.benozgr;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class ClaimPurchase implements Listener {

    private final Economy economy;
    private final CadirClaim plugin;

    public ClaimPurchase(Economy economy, CadirClaim plugin) {
        this.economy = economy;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().toLowerCase();

        // Check if the player is confirming the purchase
        if (message.equals("onayla")) {
            ItemStack item = player.getInventory().getItemInMainHand();
            Tent tent = plugin.getTentFromItem(item);

            if (tent == null) return;

            double price = tent.getPrice();

            if (economy.has(player, price)) {
                economy.withdrawPlayer(player, price);

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("price", String.valueOf(price));
                player.sendMessage(plugin.getMessage("claim-purchased", placeholders));

                // Save the claim to the database or config
            } else {
                player.sendMessage(plugin.getMessage("not-enough-money", null));
            }
        }
    }
}
