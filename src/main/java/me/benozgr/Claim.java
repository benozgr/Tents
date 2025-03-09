package me.benozgr;

import org.bukkit.Location;

import java.util.*;

public class Claim {
    private final UUID owner;
    private final Set<UUID> members;
    private final Map<UUID, Set<String>> permissions;
    private final Set<UUID> admins; // New: Store admin UUIDs
    private final long expirationDate;
    private final String tentType;
    private final Location location;
    private final Tent tent;

    public Claim(UUID owner, String tentType, Location location, Tent tent) {
        this.owner = owner;
        this.tentType = tentType;
        this.location = location;
        this.tent = tent;
        this.members = new HashSet<>();
        this.permissions = new HashMap<>();
        this.admins = new HashSet<>(); // Initialize admins set
        this.expirationDate = System.currentTimeMillis() + getDurationMillis(tent);
    }

    public UUID getOwner() {
        return owner;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public Map<UUID, Set<String>> getPermissions() {
        return permissions;
    }

    public Set<UUID> getAdmins() {
        return admins;
    }

    public long getExpirationDate() {
        return expirationDate;
    }

    public String getTentType() {
        return tentType;
    }

    public Location getLocation() {
        return location;
    }

    public Tent getTent() {
        return tent;
    }

    public void addMember(UUID member) {
        members.add(member);
        permissions.put(member, new HashSet<>());
    }

    public void removeMember(UUID member) {
        members.remove(member);
        permissions.remove(member);
    }

    public void setPermission(UUID member, String permission, boolean value) {
        if (!members.contains(member)) {
            return; // Player is not a member, do nothing
        }

        if (value) {
            permissions.get(member).add(permission);
        } else {
            permissions.get(member).remove(permission);
        }
    }

    public boolean hasPermission(UUID member, String permission) {
        return permissions.containsKey(member) && permissions.get(member).contains(permission);
    }

    public void addAdmin(UUID admin) {
        admins.add(admin);
    }

    public void removeAdmin(UUID admin) {
        admins.remove(admin);
    }

    public boolean isAdmin(UUID player) {
        return admins.contains(player);
    }

    private long getDurationMillis(Tent tent) {
        return tent.getDuration() * 24L * 60 * 60 * 1000;
    }
}