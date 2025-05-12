package me.benozgr;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class CadirHelpCommand implements CommandExecutor {

    private final CadirClaim plugin;

    public CadirHelpCommand(CadirClaim plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessage("only-players", null));
            return true;
        }

        Player player = (Player) sender;
        List<String> helpLines = plugin.getConfig().getStringList("help-commands");
        if (helpLines.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Yardım komutları konfigürasyonda tanımlı değil!");
            return true;
        }

        for (String line : helpLines) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
        }

        return true;
    }
}