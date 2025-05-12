package me.benozgr;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ExpirationTask extends BukkitRunnable {

    private final ClaimManager claimManager;
    private final CadirClaim plugin;

    public ExpirationTask(ClaimManager claimManager, CadirClaim plugin) {
        this.claimManager = claimManager;
        this.plugin = plugin;
    }

    @Override
    public void run() {
        Date now = new Date();
        for (UUID owner : claimManager.getClaims().keySet()) {
            List<Claim> claims = claimManager.getClaims(owner);
            for (Claim claim : claims) {
                if (claim.getExpirationDate() != null && claim.getExpirationDate().before(now)) {
                    int gracePeriod = plugin.getConfig().getInt("rent-settings.grace-period", 3);
                    Calendar graceDeadline = Calendar.getInstance();
                    graceDeadline.setTime(claim.getExpirationDate());
                    graceDeadline.add(Calendar.DAY_OF_YEAR, gracePeriod);

                    if (now.after(graceDeadline.getTime()) && claim.getRentAttempts() < plugin.getConfig().getInt("rent-settings.max-rent-attempts", 3)) {
                        attemptRentPayment(claim);
                    } else if (claim.getRentAttempts() >= plugin.getConfig().getInt("rent-settings.max-rent-attempts", 3)) {
                        disbandClaim(claim);
                    }
                }
            }
        }
    }

    private void attemptRentPayment(Claim claim) {
        double rentPrice = claim.getRentPrice();
        int rentDuration = claim.getRentDuration();
        var owner = Bukkit.getOfflinePlayer(claim.getOwner());

        if (plugin.getEconomy().has(owner, rentPrice)) {
            plugin.getEconomy().withdrawPlayer(owner, rentPrice);
            Calendar newExpiration = Calendar.getInstance();
            newExpiration.setTime(new Date());
            newExpiration.add(Calendar.DAY_OF_YEAR, rentDuration);
            claim.setExpirationDate(newExpiration.getTime());
            claim.resetRentAttempts();
            claim.setLastRentAttempt(new Date());

            if (owner.isOnline()) {
                Player player = owner.getPlayer();
                Map<String, String> placeholders = Map.of(
                        "duration", String.valueOf(rentDuration),
                        "price", String.valueOf(rentPrice)
                );
                player.sendMessage(plugin.getMessage("rent-paid", placeholders));
            }
        } else {
            claim.incrementRentAttempts();
            claim.setLastRentAttempt(new Date());

            if (owner.isOnline()) {
                Player player = owner.getPlayer();
                Map<String, String> placeholders = Map.of(
                        "attempts", String.valueOf(plugin.getConfig().getInt("rent-settings.max-rent-attempts", 3) - claim.getRentAttempts()),
                        "price", String.valueOf(rentPrice)
                );
                player.sendMessage(plugin.getMessage("rent-failed", placeholders));
            }
        }
    }

    private void disbandClaim(Claim claim) {
        claimManager.removeClaim(claim.getOwner(), claim.getLocation());
        Player player = plugin.getServer().getPlayer(claim.getOwner());
        if (player != null) {
            Map<String, String> placeholders = Map.of("location", claim.getLocation().toString());
            player.sendMessage(plugin.getMessage("claim-disbanded-rent", placeholders));
        }
        // Remove the tent block
        claim.getLocation().getBlock().setType(Material.AIR);
        // Remove the hologram if it exists
        if (claim.getHologram() != null) {
            claim.getHologram().delete();
        }
    }
}