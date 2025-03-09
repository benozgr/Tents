package me.benozgr;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MemberCommands implements CommandExecutor {

    private final ClaimManager claimManager;

    public MemberCommands(ClaimManager claimManager) {
        this.claimManager = claimManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(claimManager.getPlugin().getMessage("only-players", null));
            return true;
        }

        Player player = (Player) sender;
        if (args.length == 0) {
            player.sendMessage(claimManager.getPlugin().getMessage("command-usage", null));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "üye":
                if (args.length < 3) {
                    player.sendMessage(claimManager.getPlugin().getMessage("command-usage", null));
                    return true;
                }

                String subCommand = args[1];
                String targetName = args[2];
                Player target = Bukkit.getPlayer(targetName);

                if (target == null) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("player", targetName);
                    player.sendMessage(claimManager.getPlugin().getMessage("player-not-found", placeholders));
                    return true;
                }

                Claim claim = claimManager.getClaim(player.getUniqueId());
                if (claim == null) {
                    player.sendMessage(claimManager.getPlugin().getMessage("no-claim", null));
                    return true;
                }

                // Check if the player is the owner or an admin
                if (!claim.getOwner().equals(player.getUniqueId()) && !claim.isAdmin(player.getUniqueId())) {
                    player.sendMessage(claimManager.getPlugin().getMessage("no-permission", null));
                    return true;
                }

                switch (subCommand.toLowerCase()) {
                    case "ekle":
                        claim.addMember(target.getUniqueId());
                        Map<String, String> addPlaceholders = new HashMap<>();
                        addPlaceholders.put("player", target.getName());
                        player.sendMessage(claimManager.getPlugin().getMessage("member-added", addPlaceholders));
                        break;
                    case "çıkar":
                        claim.removeMember(target.getUniqueId());
                        Map<String, String> removePlaceholders = new HashMap<>();
                        removePlaceholders.put("player", target.getName());
                        player.sendMessage(claimManager.getPlugin().getMessage("member-removed", removePlaceholders));
                        break;
                    default:
                        player.sendMessage(claimManager.getPlugin().getMessage("invalid-subcommand", null));
                        break;
                }
                break;

            case "izin":
                if (args.length < 4) {
                    player.sendMessage(claimManager.getPlugin().getMessage("command-usage", null));
                    return true;
                }

                String action = args[1];
                String permissionTargetName = args[2];
                String permission = args[3];
                Player permissionTarget = Bukkit.getPlayer(permissionTargetName);

                if (permissionTarget == null) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("player", permissionTargetName);
                    player.sendMessage(claimManager.getPlugin().getMessage("player-not-found", placeholders));
                    return true;
                }

                Claim permissionClaim = claimManager.getClaim(player.getUniqueId());
                if (permissionClaim == null) {
                    player.sendMessage(claimManager.getPlugin().getMessage("no-claim", null));
                    return true;
                }

                // Check if the player is the owner or an admin
                if (!permissionClaim.getOwner().equals(player.getUniqueId()) && !permissionClaim.isAdmin(player.getUniqueId())) {
                    player.sendMessage(claimManager.getPlugin().getMessage("no-permission", null));
                    return true;
                }

                // Check if the target player is a member of the claim
                if (!permissionClaim.getMembers().contains(permissionTarget.getUniqueId())) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("player", permissionTarget.getName());
                    player.sendMessage(claimManager.getPlugin().getMessage("not-a-member", placeholders));
                    return true;
                }

                switch (action.toLowerCase()) {
                    case "ver":
                        permissionClaim.setPermission(permissionTarget.getUniqueId(), permission, true);
                        Map<String, String> grantPlaceholders = new HashMap<>();
                        grantPlaceholders.put("player", permissionTarget.getName());
                        grantPlaceholders.put("permission", permission);
                        player.sendMessage(claimManager.getPlugin().getMessage("permission-granted", grantPlaceholders));
                        break;
                    case "al":
                        permissionClaim.setPermission(permissionTarget.getUniqueId(), permission, false);
                        Map<String, String> revokePlaceholders = new HashMap<>();
                        revokePlaceholders.put("player", permissionTarget.getName());
                        revokePlaceholders.put("permission", permission);
                        player.sendMessage(claimManager.getPlugin().getMessage("permission-revoked", revokePlaceholders));
                        break;
                    default:
                        player.sendMessage(claimManager.getPlugin().getMessage("invalid-subcommand", null));
                        break;
                }
                break;

            case "yetkili":
                if (args.length < 3) {
                    player.sendMessage(claimManager.getPlugin().getMessage("command-usage", null));
                    return true;
                }

                String adminAction = args[1];
                String adminTargetName = args[2];
                Player adminTarget = Bukkit.getPlayer(adminTargetName);

                if (adminTarget == null) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("player", adminTargetName);
                    player.sendMessage(claimManager.getPlugin().getMessage("player-not-found", placeholders));
                    return true;
                }

                Claim adminClaim = claimManager.getClaim(player.getUniqueId());
                if (adminClaim == null) {
                    player.sendMessage(claimManager.getPlugin().getMessage("no-claim", null));
                    return true;
                }

                // Only the owner can manage admins
                if (!adminClaim.getOwner().equals(player.getUniqueId())) {
                    player.sendMessage(claimManager.getPlugin().getMessage("no-permission", null));
                    return true;
                }

                switch (adminAction.toLowerCase()) {
                    case "ekle":
                        adminClaim.addAdmin(adminTarget.getUniqueId());
                        Map<String, String> addAdminPlaceholders = new HashMap<>();
                        addAdminPlaceholders.put("player", adminTarget.getName());
                        player.sendMessage(claimManager.getPlugin().getMessage("admin-added", addAdminPlaceholders));
                        break;
                    case "çıkar":
                        adminClaim.removeAdmin(adminTarget.getUniqueId());
                        Map<String, String> removeAdminPlaceholders = new HashMap<>();
                        removeAdminPlaceholders.put("player", adminTarget.getName());
                        player.sendMessage(claimManager.getPlugin().getMessage("admin-removed", removeAdminPlaceholders));
                        break;
                    default:
                        player.sendMessage(claimManager.getPlugin().getMessage("invalid-subcommand", null));
                        break;
                }
                break;

            default:
                player.sendMessage(claimManager.getPlugin().getMessage("invalid-subcommand", null));
                break;
        }

        return true;
    }

    private void openMemberManagementMenu(Player player) {
        String title = claimManager.getPlugin().getConfig().getString("gui-titles.member-management-menu", "&aÜye Yönetimi");
        int size = claimManager.getPlugin().getConfig().getInt("gui-titles.member-management-menu-size", 9);
        Inventory menu = Bukkit.createInventory(null, size, ChatColor.translateAlternateColorCodes('&', title));

        // Yeni Üye Ekle
        ConfigurationSection addMemberConfig = claimManager.getPlugin().getConfig().getConfigurationSection("member-management-items.add-member");
        if (addMemberConfig != null) {
            ItemStack addMemberItem = createMenuItem(addMemberConfig);
            int slot = addMemberConfig.getInt("slot", 0);
            menu.setItem(slot, addMemberItem);
        }

        // Üyeleri Düzenle
        ConfigurationSection editMembersConfig = claimManager.getPlugin().getConfig().getConfigurationSection("member-management-items.edit-members");
        if (editMembersConfig != null) {
            ItemStack editMembersItem = createMenuItem(editMembersConfig);
            int slot = editMembersConfig.getInt("slot", 2);
            menu.setItem(slot, editMembersItem);
        }

        // Üyeyi Çıkar
        ConfigurationSection removeMemberConfig = claimManager.getPlugin().getConfig().getConfigurationSection("member-management-items.remove-member");
        if (removeMemberConfig != null) {
            ItemStack removeMemberItem = createMenuItem(removeMemberConfig);
            int slot = removeMemberConfig.getInt("slot", 4);
            menu.setItem(slot, removeMemberItem);
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
}