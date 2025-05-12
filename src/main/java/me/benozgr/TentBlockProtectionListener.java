package me.benozgr;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Enderman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Iterator;

public class TentBlockProtectionListener implements Listener {

    private final CadirClaim plugin;

    public TentBlockProtectionListener(CadirClaim plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Location blockLoc = event.getBlock().getLocation();
        if (plugin.getClaimManager().getClaimByTentBlock(blockLoc) != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Bu çadır bloğunu kıramazsın!");
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Iterator<Block> iterator = event.blockList().iterator(); iterator.hasNext();) {
            Block block = iterator.next();
            Location blockLoc = block.getLocation();
            if (plugin.getClaimManager().getClaimByTentBlock(blockLoc) != null) {
                iterator.remove(); // Remove the block from the explosion list
            }
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Iterator<Block> iterator = event.blockList().iterator(); iterator.hasNext();) {
            Block block = iterator.next();
            Location blockLoc = block.getLocation();
            if (plugin.getClaimManager().getClaimByTentBlock(blockLoc) != null) {
                iterator.remove(); // Remove the block from the explosion list
            }
        }
    }

    @EventHandler
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            Location blockLoc = block.getLocation();
            if (plugin.getClaimManager().getClaimByTentBlock(blockLoc) != null) {
                event.setCancelled(true);
                return; // Cancel the entire piston event if a tent block is involved
            }
        }
    }

    @EventHandler
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        if (event.isSticky()) { // Only check for sticky pistons pulling blocks
            Block block = event.getBlock().getRelative(event.getDirection().getOppositeFace());
            Location blockLoc = block.getLocation();
            if (plugin.getClaimManager().getClaimByTentBlock(blockLoc) != null) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Enderman) {
            Location blockLoc = event.getBlock().getLocation();
            if (plugin.getClaimManager().getClaimByTentBlock(blockLoc) != null) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockFade(BlockFadeEvent event) {
        Location blockLoc = event.getBlock().getLocation();
        if (plugin.getClaimManager().getClaimByTentBlock(blockLoc) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        Location blockLoc = event.getBlock().getLocation();
        if (plugin.getClaimManager().getClaimByTentBlock(blockLoc) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockSpread(BlockSpreadEvent event) {
        Location blockLoc = event.getBlock().getLocation();
        if (plugin.getClaimManager().getClaimByTentBlock(blockLoc) != null) {
            event.setCancelled(true);
        }
    }
}