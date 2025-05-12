package me.benozgr;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class EcoCommand implements CommandExecutor {

    private final CadirClaim plugin;
    private final Economy economy;

    public EcoCommand(CadirClaim plugin) {
        this.plugin = plugin;
        this.economy = plugin.getEconomy();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (economy == null) {
            sender.sendMessage(ChatColor.RED + "Ekonomi sistemi bulunamadı. Lütfen bir ekonomi sağlayıcısını kurun!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("give")) {
            if (!sender.hasPermission("cadirclaim.eco.give")) {
                sender.sendMessage(ChatColor.RED + "Bu komutu kullanma yetkin yok!");
                return true;
            }
            if (args.length != 3) {
                sender.sendMessage(ChatColor.RED + "Kullanım: /eco give <oyuncu> <miktar>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Oyuncu bulunamadı: " + args[1]);
                return true;
            }
            double amount;
            try {
                amount = Double.parseDouble(args[2]);
                if (amount <= 0) {
                    sender.sendMessage(ChatColor.RED + "Miktar pozitif bir sayı olmalı!");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Geçersiz miktar: " + args[2]);
                return true;
            }
            EconomyResponse response = economy.depositPlayer(target.getName(), amount);
            if (response.transactionSuccess()) {
                sender.sendMessage(ChatColor.GREEN + target.getName() + " oyuncusuna " + economy.format(amount) + " verildi.");
                if (!sender.equals(target)) {
                    target.sendMessage(ChatColor.GREEN + "Bakiyene " + economy.format(amount) + " eklendi!");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Para verme işlemi başarısız: " + response.errorMessage);
            }
            return true;
        }

        if (subCommand.equals("take")) {
            if (!sender.hasPermission("cadirclaim.eco.take")) {
                sender.sendMessage(ChatColor.RED + "Bu komutu kullanma yetkin yok!");
                return true;
            }
            if (args.length != 3) {
                sender.sendMessage(ChatColor.RED + "Kullanım: /eco take <oyuncu> <miktar>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Oyuncu bulunamadı: " + args[1]);
                return true;
            }
            double amount;
            try {
                amount = Double.parseDouble(args[2]);
                if (amount <= 0) {
                    sender.sendMessage(ChatColor.RED + "Miktar pozitif bir sayı olmalı!");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Geçersiz miktar: " + args[2]);
                return true;
            }
            EconomyResponse response = economy.withdrawPlayer(target.getName(), amount);
            if (response.transactionSuccess()) {
                sender.sendMessage(ChatColor.GREEN + target.getName() + " oyuncusundan " + economy.format(amount) + " alındı.");
                if (!sender.equals(target)) {
                    target.sendMessage(ChatColor.RED + "Bakiyenden " + economy.format(amount) + " alındı!");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Para alma işlemi başarısız: " + response.errorMessage);
            }
            return true;
        }

        if (subCommand.equals("set")) {
            if (!sender.hasPermission("cadirclaim.eco.set")) {
                sender.sendMessage(ChatColor.RED + "Bu komutu kullanma yetkin yok!");
                return true;
            }
            if (args.length != 3) {
                sender.sendMessage(ChatColor.RED + "Kullanım: /eco set <oyuncu> <miktar>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Oyuncu bulunamadı: " + args[1]);
                return true;
            }
            double amount;
            try {
                amount = Double.parseDouble(args[2]);
                if (amount < 0) {
                    sender.sendMessage(ChatColor.RED + "Miktar negatif olamaz!");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Geçersiz miktar: " + args[2]);
                return true;
            }
            double currentBalance = economy.getBalance(target.getName());
            if (currentBalance > amount) {
                EconomyResponse withdrawResponse = economy.withdrawPlayer(target.getName(), currentBalance - amount);
                if (!withdrawResponse.transactionSuccess()) {
                    sender.sendMessage(ChatColor.RED + "Bakiye ayarlama başarısız: " + withdrawResponse.errorMessage);
                    return true;
                }
            } else if (currentBalance < amount) {
                EconomyResponse depositResponse = economy.depositPlayer(target.getName(), amount - currentBalance);
                if (!depositResponse.transactionSuccess()) {
                    sender.sendMessage(ChatColor.RED + "Bakiye ayarlama başarısız: " + depositResponse.errorMessage);
                    return true;
                }
            }
            sender.sendMessage(ChatColor.GREEN + target.getName() + " oyuncusunun bakiyesi " + economy.format(amount) + " olarak ayarlandı.");
            if (!sender.equals(target)) {
                target.sendMessage(ChatColor.GREEN + "Bakiyen " + economy.format(amount) + " olarak ayarlandı!");
            }
            return true;
        }

        if (subCommand.equals("balance") || subCommand.equals("bal")) {
            if (args.length != 1 && args.length != 2) {
                sender.sendMessage(ChatColor.RED + "Kullanım: /eco balance [oyuncu]");
                return true;
            }
            Player target = (args.length == 1 && sender instanceof Player) ? (Player) sender : Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Oyuncu bulunamadı: " + (args.length == 2 ? args[1] : sender.getName()));
                return true;
            }
            double balance = economy.getBalance(target.getName());
            sender.sendMessage(ChatColor.GREEN + target.getName() + " oyuncusunun bakiyesi: " + economy.format(balance));
            return true;
        }

        if (subCommand.equals("reset")) {
            if (!sender.hasPermission("cadirclaim.eco.reset")) {
                sender.sendMessage(ChatColor.RED + "Bu komutu kullanma yetkin yok!");
                return true;
            }
            if (args.length != 2) {
                sender.sendMessage(ChatColor.RED + "Kullanım: /eco reset <oyuncu>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Oyuncu bulunamadı: " + args[1]);
                return true;
            }
            double startingBalance = plugin.getConfig().getDouble("economy.starting-balance", 0.0);
            EconomyResponse response = economy.withdrawPlayer(target.getName(), economy.getBalance(target.getName()) - startingBalance);
            if (response.transactionSuccess() || economy.getBalance(target.getName()) == 0) {
                sender.sendMessage(ChatColor.GREEN + target.getName() + " oyuncusunun bakiyesi sıfırlandı ve başlangıç bakiyesine (" + economy.format(startingBalance) + ") ayarlandı.");
                if (!sender.equals(target)) {
                    target.sendMessage(ChatColor.GREEN + "Bakiyen sıfırlandı ve " + economy.format(startingBalance) + " olarak ayarlandı!");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Bakiye sıfırlama başarısız: " + response.errorMessage);
            }
            return true;
        }

        if (subCommand.equals("baltop")) {
            if (!sender.hasPermission("cadirclaim.eco.baltop")) {
                sender.sendMessage(ChatColor.RED + "Bu komutu kullanma yetkin yok!");
                return true;
            }
            Map<String, Double> balances = new HashMap<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                balances.put(p.getName(), economy.getBalance(p.getName()));
            }
            Map<String, Double> sortedBalances = balances.entrySet().stream()
                    .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                    .limit(10)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
            sender.sendMessage(ChatColor.GOLD + "En Yüksek Bakiyeler (Top 10):");
            int rank = 1;
            for (Map.Entry<String, Double> entry : sortedBalances.entrySet()) {
                sender.sendMessage(ChatColor.YELLOW + "#" + rank + " " + entry.getKey() + ": " + economy.format(entry.getValue()));
                rank++;
            }
            return true;
        }

        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Ekonomi Komutları:");
        sender.sendMessage(ChatColor.YELLOW + "/eco balance [oyuncu] - Oyuncunun bakiyesini kontrol et");
        sender.sendMessage(ChatColor.YELLOW + "/eco baltop - En yüksek bakiyelere sahip oyuncuları göster");
        if (sender.hasPermission("cadirclaim.eco.give")) {
            sender.sendMessage(ChatColor.YELLOW + "/eco give <oyuncu> <miktar> - Oyuncuya para ver");
        }
        if (sender.hasPermission("cadirclaim.eco.take")) {
            sender.sendMessage(ChatColor.YELLOW + "/eco take <oyuncu> <miktar> - Oyuncudan para al");
        }
        if (sender.hasPermission("cadirclaim.eco.set")) {
            sender.sendMessage(ChatColor.YELLOW + "/eco set <oyuncu> <miktar> - Oyuncunun bakiyesini ayarla");
        }
        if (sender.hasPermission("cadirclaim.eco.reset")) {
            sender.sendMessage(ChatColor.YELLOW + "/eco reset <oyuncu> - Oyuncunun bakiyesini sıfırla");
        }
    }
}