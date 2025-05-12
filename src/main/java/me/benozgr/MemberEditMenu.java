package me.benozgr;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class MemberEditMenu implements Listener {

    private final CadirClaim plugin;
    private final Map<UUID, Claim> editingClaims = new HashMap<>();
    private final Map<UUID, Claim> pendingRemoveMember = new HashMap<>();
    private final Map<UUID, Integer> currentPage = new HashMap<>();
    private final Map<UUID, UUID> pendingPermissionEdit = new HashMap<>(); // Player -> Target member

    public MemberEditMenu(CadirClaim plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openMenu(Player player, Claim claim, int page) {
        editingClaims.put(player.getUniqueId(), claim);
        currentPage.put(player.getUniqueId(), page);

        ConfigurationSection tentConfig = plugin.getConfig().getConfigurationSection("gui.items." + claim.getTentType());
        String tentDisplayName = tentConfig != null ? ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', tentConfig.getString("name", claim.getTentType()))) : claim.getTentType();
        String titleTemplate = plugin.getConfig().getString("gui-titles.member-edit-menu", "%type% - Üye Düzenleme");
        String title = ChatColor.translateAlternateColorCodes('&', titleTemplate.replace("%type%", tentDisplayName));
        int size = Math.min(plugin.getConfig().getInt("gui-titles.member-edit-menu-size", 27), 54);
        size = (size / 9) * 9;
        Inventory menu = Bukkit.createInventory(null, size, title);

        ConfigurationSection menuConfig = plugin.getConfig().getConfigurationSection("claim-management-items.member-edit");

        // Create a list of members, excluding the owner
        List<UUID> members = new ArrayList<>(claim.getMembers());
        members.remove(claim.getOwner()); // Remove the owner from the displayed members

        int membersPerPage = size - 3;
        int startIndex = page * membersPerPage;
        int endIndex = Math.min(startIndex + membersPerPage, members.size());

        ConfigurationSection permsConfig = plugin.getConfig().getConfigurationSection("permissions");
        Map<String, String> permDisplayNames = new HashMap<>();
        for (String perm : permsConfig.getKeys(false)) {
            permDisplayNames.put(perm, ChatColor.translateAlternateColorCodes('&', permsConfig.getString(perm + ".display-name")));
        }

        for (int i = startIndex; i < endIndex; i++) {
            UUID memberUUID = members.get(i);
            Player member = Bukkit.getPlayer(memberUUID);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            String memberName = member != null ? member.getName() : Bukkit.getOfflinePlayer(memberUUID).getName();
            meta.setDisplayName(ChatColor.YELLOW + memberName);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "İzinler:");
            Set<String> perms = claim.getPermissions(memberUUID);
            for (String perm : permDisplayNames.keySet()) {
                lore.add(ChatColor.WHITE + permDisplayNames.get(perm) + ": " + (perms.contains(perm) ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗"));
            }
            lore.add(ChatColor.translateAlternateColorCodes('&', menuConfig.getString("member-head.lore-click", "&aTıkla: Yetkileri düzenle")));
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(memberUUID));
            meta.setLore(lore);
            head.setItemMeta(meta);
            menu.setItem(i - startIndex, head);
        }

        if (startIndex > 0) {
            ItemStack prevPage = createMenuItem(menuConfig.getConfigurationSection("prev-page"));
            menu.setItem(size - 3, prevPage);
        }
        if (endIndex < members.size()) {
            ItemStack nextPage = createMenuItem(menuConfig.getConfigurationSection("next-page"));
            menu.setItem(size - 2, nextPage);
        }
        ItemStack close = createMenuItem(menuConfig.getConfigurationSection("close"));
        menu.setItem(size - 1, close);

        player.openInventory(menu);
    }

    public void openMenu(Player player, Claim claim) {
        openMenu(player, claim, 0);
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
        UUID playerId = player.getUniqueId();

        Inventory clickedInventory = event.getClickedInventory();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedInventory == null || clickedItem == null || !clickedItem.hasItemMeta()) return;

        String title = event.getView().getTitle();

        // Main members menu
        if (editingClaims.containsKey(playerId)) {
            Claim claim = editingClaims.get(playerId);
            ConfigurationSection tentConfig = plugin.getConfig().getConfigurationSection("gui.items." + claim.getTentType());
            String tentDisplayName = tentConfig != null ? ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', tentConfig.getString("name", claim.getTentType()))) : claim.getTentType();
            String titleTemplate = plugin.getConfig().getString("gui-titles.member-edit-menu", "%type% - Üye Düzenleme");
            String expectedTitle = ChatColor.translateAlternateColorCodes('&', titleTemplate.replace("%type%", tentDisplayName));
            if (!title.equals(expectedTitle)) return;

            event.setCancelled(true);

            ConfigurationSection menuConfig = plugin.getConfig().getConfigurationSection("claim-management-items.member-edit");
            String closeName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', menuConfig.getString("close.name")));
            String nextPageName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', menuConfig.getString("next-page.name")));
            String prevPageName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', menuConfig.getString("prev-page.name")));
            String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

            if (itemName.equals(closeName)) {
                player.closeInventory();
                editingClaims.remove(playerId);
                currentPage.remove(playerId);
                new ClaimManagementMenu(plugin).openMenu(player, claim);
            } else if (itemName.equals(nextPageName)) {
                int nextPage = currentPage.get(playerId) + 1;
                player.closeInventory();
                openMenu(player, claim, nextPage);
            } else if (itemName.equals(prevPageName)) {
                int prevPage = Math.max(currentPage.get(playerId) - 1, 0);
                player.closeInventory();
                openMenu(player, claim, prevPage);
            } else {
                // Identify the member by matching the display name (without the ChatColor.YELLOW prefix)
                String clickedMemberName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
                UUID memberUUID = null;
                for (UUID uuid : claim.getMembers()) {
                    String memberName = Bukkit.getOfflinePlayer(uuid).getName();
                    if (memberName != null && memberName.equals(clickedMemberName)) {
                        memberUUID = uuid;
                        break;
                    }
                }

                if (memberUUID != null) {
                    if (claim.getOwner().equals(playerId) || claim.hasPermission(playerId, "add_members")) {
                        openMemberPermissionsMenu(player, claim, memberUUID);
                        editingClaims.remove(playerId);
                        currentPage.remove(playerId);
                    } else {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMessage("no-permission", null)));
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Üye bulunamadı!");
                }
            }
        }
        // Permissions menu
        else if (pendingPermissionEdit.containsKey(playerId)) {
            Claim claim = getClaimFromTitle(player, title);
            if (claim == null || !title.startsWith(ChatColor.translateAlternateColorCodes('&', "&eYetkiler - "))) return;

            event.setCancelled(true);

            UUID target = pendingPermissionEdit.get(playerId);
            if (!claim.getOwner().equals(playerId) && !claim.hasPermission(playerId, "add_members")) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMessage("no-permission", null)));
                return;
            }

            ConfigurationSection menuConfig = plugin.getConfig().getConfigurationSection("claim-management-items.member-permissions");
            String backName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', menuConfig.getString("back.name")));
            String removeMemberName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', menuConfig.getString("remove-member.name")));
            String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

            if (itemName.equals(backName)) {
                player.closeInventory();
                pendingPermissionEdit.remove(playerId);
                openMenu(player, claim);
            } else if (itemName.equals(removeMemberName)) {
                player.closeInventory();
                pendingPermissionEdit.remove(playerId);
                pendingRemoveMember.put(playerId, claim);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMessage("remove-member-prompt", Map.of("player", Bukkit.getOfflinePlayer(target).getName()))));
                registerRemoveMemberListener(player, claim, target);
            } else {
                if (claim.getOwner().equals(target)) {
                    player.sendMessage(ChatColor.RED + "Sahibin izinleri değiştirilemez!");
                    return;
                }
                ConfigurationSection permsConfig = plugin.getConfig().getConfigurationSection("permissions");
                List<String> permissions = new ArrayList<>(permsConfig.getKeys(false));
                int slot = event.getSlot();
                if (slot >= 0 && slot < permissions.size()) {
                    String permission = permissions.get(slot);
                    boolean currentValue = claim.hasPermission(target, permission);
                    claim.setPermission(target, permission, !currentValue);
                    openMemberPermissionsMenu(player, claim, target); // Refresh menu
                }
            }
        }
    }

    private void openMemberPermissionsMenu(Player player, Claim claim, UUID memberUUID) {
        String memberName = Bukkit.getOfflinePlayer(memberUUID).getName();
        String titleTemplate = plugin.getConfig().getString("gui-titles.member-permissions-menu", "&eYetkiler - %member%");
        String title = ChatColor.translateAlternateColorCodes('&', titleTemplate.replace("%member%", memberName));
        int size = Math.min(plugin.getConfig().getInt("gui-titles.member-permissions-menu-size", 9), 54);
        size = (size / 9) * 9;
        Inventory menu = Bukkit.createInventory(null, size, title);

        ConfigurationSection menuConfig = plugin.getConfig().getConfigurationSection("claim-management-items.member-permissions");
        ConfigurationSection permsConfig = plugin.getConfig().getConfigurationSection("permissions");

        List<String> permissions = new ArrayList<>(permsConfig.getKeys(false));
        for (int i = 0; i < permissions.size() && i < size - 2; i++) {
            String perm = permissions.get(i);
            ConfigurationSection permSection = permsConfig.getConfigurationSection(perm);
            ItemStack item = new ItemStack(Material.matchMaterial(permSection.getString("material", "STONE")));
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', permSection.getString("display-name", perm)));
            List<String> lore = new ArrayList<>();
            boolean hasPerm = claim.hasPermission(memberUUID, perm);
            lore.add(ChatColor.translateAlternateColorCodes('&', hasPerm ? "&a✓ Aktif" : "&c✗ Pasif"));
            if (claim.getOwner().equals(memberUUID)) {
                lore.add(ChatColor.translateAlternateColorCodes('&', "&7(Sahip - Değiştirilemez)"));
            } else {
                lore.add(ChatColor.translateAlternateColorCodes('&', "&7Tıkla ve durumu değiştir!"));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
            menu.setItem(i, item);
        }

        ItemStack removeMember = createMenuItem(menuConfig.getConfigurationSection("remove-member"));
        menu.setItem(size - 2, removeMember);

        ItemStack back = createMenuItem(menuConfig.getConfigurationSection("back"));
        menu.setItem(size - 1, back);

        player.openInventory(menu);
        pendingPermissionEdit.put(player.getUniqueId(), memberUUID);
    }

    private void registerRemoveMemberListener(Player player, Claim claim, UUID memberUUID) {
        UUID playerId = player.getUniqueId();
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onChat(AsyncPlayerChatEvent e) {
                if (!e.getPlayer().equals(player)) return;
                e.setCancelled(true);
                String message = e.getMessage().trim().toLowerCase();

                if (message.equals("evet") || message.equals("onayla")) {
                    claim.removeMember(memberUUID);
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("player", Bukkit.getOfflinePlayer(memberUUID).getName());
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMessage("member-removed", placeholders)));
                    Player target = Bukkit.getPlayer(memberUUID);
                    if (target != null) {
                        target.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMessage("removed-from-claim", Map.of("owner", player.getName()))));
                    }
                } else {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMessage("remove-member-cancelled", Map.of("player", Bukkit.getOfflinePlayer(memberUUID).getName()))));
                }

                pendingRemoveMember.remove(playerId);
                AsyncPlayerChatEvent.getHandlerList().unregister(this);
                openMenu(player, claim);
            }
        }, plugin);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingRemoveMember.containsKey(playerId)) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMessage("remove-member-timeout", null)));
                pendingRemoveMember.remove(playerId);
                openMenu(player, claim);
            }
        }, 600L);
    }

    private Claim getClaimFromTitle(Player player, String title) {
        for (Claim claim : plugin.getClaimManager().getClaims(player.getUniqueId())) {
            String memberName = title.replace(ChatColor.translateAlternateColorCodes('&', "&eYetkiler - "), "");
            UUID memberId = Bukkit.getOfflinePlayer(memberName).getUniqueId();
            if (claim.getMembers().contains(memberId)) return claim;
        }
        return null;
    }

    public boolean hasPendingRemoveMember(UUID playerId) {
        return pendingRemoveMember.containsKey(playerId);
    }

    public void cancelPendingRemoveMember(UUID playerId) {
        pendingRemoveMember.remove(playerId);
    }

    public boolean hasPendingPermissionEdit(UUID playerId) {
        return pendingPermissionEdit.containsKey(playerId);
    }

    public void cancelPendingPermissionEdit(UUID playerId) {
        pendingPermissionEdit.remove(playerId);
    }
}