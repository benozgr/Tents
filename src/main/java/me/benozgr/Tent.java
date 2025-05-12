package me.benozgr;

import org.bukkit.configuration.ConfigurationSection;

public class Tent {
    private final String id;
    private final double price;
    private final int size;
    private final int maxMembers;
    private final int duration;

    // Constructor for direct values (used in CadirClaim.loadTents)
    public Tent(String id, double price, int size, int maxMembers, int duration) {
        this.id = id;
        this.price = price;
        this.size = size;
        this.maxMembers = maxMembers;
        this.duration = duration;
    }

    // Constructor for ConfigurationSection (if needed elsewhere)
    public Tent(String id, ConfigurationSection section) {
        this.id = id;
        this.price = section.getDouble("price");
        this.size = section.getInt("size");
        this.maxMembers = section.getInt("max_members");
        this.duration = section.getInt("duration");
    }

    // Getters
    public String getId() {
        return id;
    }

    public double getPrice() {
        return price;
    }

    public int getSize() {
        return size;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public int getDuration() {
        return duration;
    }

    // Added to fix the "cannot resolve method getType" error
    public String getType() {
        return id; // Returns the tent's ID as its type
    }

    // Existing getName method
    public String getName() {
        return id; // The "name" of the tent is its ID (e.g., "kucuk_cadir")
    }
}