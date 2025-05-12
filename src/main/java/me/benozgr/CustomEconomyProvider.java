package me.benozgr;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

public class CustomEconomyProvider implements Economy {

    private final Plugin plugin;
    private final Logger logger;
    private final Map<UUID, Double> balances = new HashMap<>();
    private final File economyFile;
    private final YamlConfiguration economyConfig;
    private final double startingBalance;
    private final String currencyNameSingular;
    private final String currencyNamePlural;
    private final double maxMoney;
    private final double minMoney;
    private final boolean economyLogEnabled;
    private final File logFile;

    public CustomEconomyProvider(Plugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.economyFile = new File(plugin.getDataFolder(), "economy.yml");
        this.economyConfig = YamlConfiguration.loadConfiguration(economyFile);
        this.startingBalance = config.getDouble("economy.starting-balance", 500.0);
        this.currencyNameSingular = config.getString("economy.currency-name-singular", "Tent Coin");
        this.currencyNamePlural = config.getString("economy.currency-name-plural", "Tent Coins");
        this.maxMoney = config.getDouble("economy.max-money", 10000000000000.0);
        this.minMoney = config.getDouble("economy.min-money", -10000.0);
        this.economyLogEnabled = config.getBoolean("economy.economy-log-enabled", false);
        this.logFile = new File(plugin.getDataFolder(), "economy.log");
        loadBalances();
    }

    private void loadBalances() {
        if (!economyFile.exists()) {
            economyConfig.set("starting-balance", startingBalance);
            saveBalances();
        }
        for (String key : economyConfig.getKeys(false)) {
            if (key.equals("starting-balance")) continue;
            try {
                UUID uuid = UUID.fromString(key);
                double balance = economyConfig.getDouble(key, startingBalance);
                if (balance > maxMoney) balance = maxMoney;
                if (balance < minMoney) balance = minMoney;
                balances.put(uuid, balance);
            } catch (IllegalArgumentException e) {
                logger.warning("Geçersiz UUID bulundu: " + key);
            }
        }
        logger.info("Ekonomi verileri yüklendi: " + balances.size() + " oyuncu.");
    }

    private void saveBalances() {
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
            double balance = entry.getValue();
            if (balance > maxMoney) balance = maxMoney;
            if (balance < minMoney) balance = minMoney;
            economyConfig.set(entry.getKey().toString(), balance);
        }
        try {
            economyConfig.save(economyFile);
        } catch (Exception e) {
            logger.severe("Ekonomi verileri kaydedilirken hata: " + e.getMessage());
        }
    }

    private void logTransaction(String action, String playerName, double amount, String result) {
        if (economyLogEnabled) {
            try (PrintWriter out = new PrintWriter(new FileWriter(logFile, true))) {
                String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                out.println(timestamp + " | " + action + " | " + playerName + " | " + format(amount) + " | " + result);
            } catch (IOException e) {
                logger.warning("Log dosyasına yazılırken hata: " + e.getMessage());
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return "CadirClaimEconomy";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        return String.format("%.2f %s", amount, amount == 1 ? currencyNameSingular : currencyNamePlural);
    }

    @Override
    public String currencyNamePlural() {
        return currencyNamePlural;
    }

    @Override
    public String currencyNameSingular() {
        return currencyNameSingular;
    }

    @Override
    public boolean hasAccount(String playerName) {
        return true;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return true;
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public double getBalance(String playerName) {
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerName);
        return getBalance(player);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return balances.computeIfAbsent(player.getUniqueId(), k -> {
            double balance = startingBalance;
            if (balance > maxMoney) balance = maxMoney;
            if (balance < minMoney) balance = minMoney;
            return balance;
        });
    }

    @Override
    public double getBalance(String playerName, String worldName) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String worldName) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double amount) {
        return getBalance(playerName) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerName);
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        double balance = getBalance(player);
        if (amount < 0) {
            return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Negatif miktar çekilemez!");
        }
        if (balance < amount) {
            return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Yetersiz bakiye!");
        }
        double newBalance = balance - amount;
        if (newBalance < minMoney) {
            return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Bakiye minimum limite (" + this.format(minMoney) + ") ulaştı!");
        }
        balances.put(player.getUniqueId(), newBalance);
        saveBalances();
        logTransaction("WITHDRAW", player.getName(), amount, "SUCCESS");
        return new EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerName);
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        double balance = getBalance(player);
        if (amount < 0) {
            return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Negatif miktar yatırımı yapılamaz!");
        }
        double newBalance = balance + amount;
        if (newBalance > maxMoney) {
            return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Bakiye maksimum limite (" + this.format(maxMoney) + ") ulaştı!");
        }
        balances.put(player.getUniqueId(), newBalance);
        saveBalances();
        logTransaction("DEPOSIT", player.getName(), amount, "SUCCESS");
        return new EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banka desteklenmiyor!");
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banka desteklenmiyor!");
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banka desteklenmiyor!");
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banka desteklenmiyor!");
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banka desteklenmiyor!");
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banka desteklenmiyor!");
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banka desteklenmiyor!");
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banka desteklenmiyor!");
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banka desteklenmiyor!");
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banka desteklenmiyor!");
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banka desteklenmiyor!");
    }

    @Override
    public List<String> getBanks() {
        return Collections.emptyList();
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }
}