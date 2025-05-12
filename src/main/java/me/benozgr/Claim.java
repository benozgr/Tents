package me.benozgr;

import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;

import java.util.*;

public class Claim {
    private final UUID id;
    private final UUID owner;
    private final Location location;
    private final Tent tent;
    private final Map<UUID, Set<String>> memberPermissions;
    private Date expirationDate;
    private String name;
    private Hologram hologram;
    private int rentAttempts; // Track failed rent attempts
    private Date lastRentAttempt; // Track the last rent attempt date

    public Claim(UUID owner, Location location, Tent tent) {
        this.id = UUID.randomUUID();
        this.owner = owner;
        this.location = location;
        this.tent = tent;
        this.memberPermissions = new HashMap<>();
        this.memberPermissions.put(owner, new HashSet<>(Arrays.asList("block_place", "block_break", "mob_kill", "animal_kill", "open_chests", "add_members")));
        this.expirationDate = calculateExpirationDate(tent.getDuration());
        this.name = "İsimsiz Claim";
        this.rentAttempts = 0;
        this.lastRentAttempt = null;
    }

    private Date calculateExpirationDate(int durationInDays) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, durationInDays);
        return calendar.getTime();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwner() {
        return owner;
    }

    public Location getLocation() {
        return location;
    }

    public Tent getTent() {
        return tent;
    }

    public String getTentType() {
        return tent.getType();
    }

    public Date getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(Date expirationDate) {
        this.expirationDate = expirationDate;
    }

    public void addMember(UUID memberId) {
        memberPermissions.computeIfAbsent(memberId, k -> new HashSet<>());
    }

    public void removeMember(UUID memberId) {
        memberPermissions.remove(memberId);
    }

    public Set<UUID> getMembers() {
        return memberPermissions.keySet();
    }

    public boolean hasPermission(UUID memberId, String permission) {
        return memberPermissions.getOrDefault(memberId, Collections.emptySet()).contains(permission);
    }

    public void setPermission(UUID memberId, String permission, boolean value) {
        Set<String> perms = memberPermissions.computeIfAbsent(memberId, k -> new HashSet<>());
        if (value) {
            perms.add(permission);
        } else {
            perms.remove(permission);
        }
    }

    public Map<UUID, Set<String>> getMemberPermissions() {
        return memberPermissions;
    }

    public Set<String> getPermissions(UUID memberId) {
        return memberPermissions.getOrDefault(memberId, Collections.emptySet());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Hologram getHologram() {
        return hologram;
    }

    public void setHologram(Hologram hologram) {
        this.hologram = hologram;
    }

    public int getRentAttempts() {
        return rentAttempts;
    }

    public void incrementRentAttempts() {
        this.rentAttempts++;
    }

    public void resetRentAttempts() {
        this.rentAttempts = 0;
    }

    public Date getLastRentAttempt() {
        return lastRentAttempt;
    }

    public void setLastRentAttempt(Date lastRentAttempt) {
        this.lastRentAttempt = lastRentAttempt;
    }

    // Get the rent price (original purchase price of the tent)
    public double getRentPrice() {
        return tent.getPrice();
    }

    // Get the duration to extend (original duration of the tent)
    public int getRentDuration() {
        return tent.getDuration();
    }

    @Override
    public String toString() {
        return "Claim{id=" + id.toString() +
                ", owner=" + owner.toString() +
                ", location=" + (location != null ? location.getWorld().getName() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() : "null") +
                ", tent=" + (tent != null ? tent.getName() : "null") +
                ", name=" + name +
                ", expirationDate=" + (expirationDate != null ? expirationDate.toString() : "null") +
                ", memberPermissions=" + memberPermissions +
                ", rentAttempts=" + rentAttempts +
                ", lastRentAttempt=" + (lastRentAttempt != null ? lastRentAttempt.toString() : "null") +
                "}";
    }
}