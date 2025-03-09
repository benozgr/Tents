package me.benozgr;

public class Tent {
    private final String name;
    private final double price;
    private final int size;
    private final int maxMembers;
    private final int duration; // in days

    public Tent(String name, double price, int size, int maxMembers, int duration) {
        this.name = name;
        this.price = price;
        this.size = size;
        this.maxMembers = maxMembers;
        this.duration = duration;
    }

    public String getName() {
        return name;
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
}