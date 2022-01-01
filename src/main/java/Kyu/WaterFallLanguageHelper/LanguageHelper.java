package Kyu.WaterFallLanguageHelper;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public final class LanguageHelper {

    private static String defaultLang;
    private static InputStream defaultLangResource;

    private static String prefix;

    private static Configuration pLangConf;
    private static File pLangFile;

    private static DB database;
    private static boolean useDB;

    private static Map<String, Map<String, String>> messages = new HashMap<>();
    private static Map<String, Map<String, List<String>>> lores = new HashMap<>();

    private static Map<UUID, String> playerLangs = new HashMap<>();

    private static Plugin plugin;

    public static void setup(Plugin plugin, String defaultLang, InputStream langResource, String prefix, boolean useDB) {
        LanguageHelper.plugin = plugin;
        LanguageHelper.defaultLang = defaultLang;
        LanguageHelper.defaultLangResource = langResource;
        LanguageHelper.prefix = prefix;
        LanguageHelper.useDB = useDB;

        pLangFile = new File(plugin.getDataFolder(), "playerLangs.yml");
        if (!pLangFile.exists()) {
            try {
                pLangFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
        try {
            pLangConf = ConfigurationProvider.getProvider(YamlConfiguration.class).load(pLangFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        File folder = new File(plugin.getDataFolder() + "/locales");
        if (!folder.exists()) {
            folder.mkdir();
        }

        loadMessages();
        new MessageJoinListener(plugin);
    }

    public static void setDatabase(DB database) {
        LanguageHelper.database = database;
    }

    private static void loadMessages() {
        updateDefaultLangFile();

        File folder = new File(plugin.getDataFolder() + "/locales");
        for (File file : folder.listFiles()) {
            Map<String, String> langMessages = new HashMap<>();
            Map<String, List<String>> langLores = new HashMap<>();
            String name = file.getName().split(".yml")[0];
            Configuration conf = null;
            try {
                conf = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
            for (String key : conf.getKeys()) {
                for (String messageKey : conf.getSection(key).getKeys()) {
                    if (key.toLowerCase().contains("lores")) {
                        List<String> lore = new ArrayList<>();
                        for (String line : conf.getStringList(key + "." + messageKey)) {
                            lore.add(color(line));
                        }
                        langLores.put(messageKey, lore);
                    } else {
                        String message = color(conf.getString(key + "." + messageKey));
                        langMessages.put(messageKey, message);
                        plugin.getLogger().info("Putting Message " + messageKey + " from " + name + " into map!");
                    }
                }
            }
            lores.put(name, langLores);
            messages.put(name, langMessages);
        }

    }

    private static void updateDefaultLangFile() {
        File file = new File(plugin.getDataFolder(), "locales/" + defaultLang + ".yml");
        if (!file.exists()) {
            try {
                Files.copy(plugin.getResourceAsStream(defaultLang + ".yml"), file.toPath());
                return;
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
        Configuration refConf = ConfigurationProvider.getProvider(YamlConfiguration.class).load(defaultLangResource);
        Configuration defaultConf = null;
        try {
            defaultConf = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (String topKey : refConf.getKeys()) {
            for (String mess : refConf.getSection(topKey).getKeys()) {
                if (defaultConf.get(topKey + "." + mess) == null) {
                    if (topKey.toLowerCase().contains("lores")) {
                        defaultConf.set(topKey + "." + mess, refConf.getStringList(topKey + "." + mess));
                    } else {
                        defaultConf.set(topKey + "." + mess, refConf.getString(topKey + "." + mess));
                    }
                }
            }
        }
        saveConfig(defaultConf, file);
    }

    public static List<String> getLore(String pLang, String loreKey) {
        if (!lores.containsKey(pLang)) {
            return lores.get(defaultLang).getOrDefault(loreKey, new ArrayList<>(Arrays.asList(color("&cLore &4 " + loreKey + " &c not found!"))));
        } else {
            return lores.get(pLang).getOrDefault(loreKey, new ArrayList<>(Arrays.asList(color("&cLore &4 " + loreKey + " &c not found!"))));
        }
    }

    public static List<String> getLore(CommandSender p, String loreKey) {
        String pLang;
        if (p instanceof ProxiedPlayer) {
            ProxiedPlayer player = (ProxiedPlayer) p;
            if (!playerLangs.containsKey(player.getUniqueId())) {
                pLang = defaultLang;
                setupPlayer((ProxiedPlayer) p);
            } else {
                pLang = playerLangs.get(player.getUniqueId());
            }
        } else {
            pLang = defaultLang;
        }
        return getLore(pLang, loreKey);
    }

    public static List<String> getLore(String loreKey) {
        return lores.get(defaultLang).getOrDefault(loreKey, new ArrayList<>(Arrays.asList(color("&cLore &4 " + loreKey + " &c not found!"))));
    }

    public static String getMess(String pLang, String messageKey, boolean... usePrefix) {
        String message;
        if (!messages.containsKey(pLang)) {
            message = messages.get(defaultLang).getOrDefault(messageKey, color("&cMessage &4" + messageKey + "&c not found!"));
        } else {
            message = messages.get(pLang).getOrDefault(messageKey, color("&cMessage &4" + messageKey + "&c not found!"));
        }
        if (usePrefix.length > 0 && usePrefix[0]) {
            message = prefix + message;
        }
        return message;
    }

    public static String getMess(CommandSender p, String messageKey, boolean... usePrefix) {
        String pLang;
        if (p instanceof ProxiedPlayer) {
            ProxiedPlayer player = (ProxiedPlayer) p;
            if (!playerLangs.containsKey(player.getUniqueId())) {
                pLang = defaultLang;
                setupPlayer((ProxiedPlayer) p);
            } else {
                pLang = playerLangs.get(player.getUniqueId());
            }
        } else {
            pLang = defaultLang;
        }
        return getMess(pLang, messageKey, usePrefix);
    }

    public static String getMess(String messageKey, boolean... usePrefix) {
        String message = messages.get(defaultLang).getOrDefault(messageKey, color("&cMessage &4" + messageKey + "&c not found!"));
        if (usePrefix.length > 0 && usePrefix[0]) {
            message = prefix + message;
        }
        return message;
    }

    public static void setupPlayer(ProxiedPlayer p) {
        if (!isUseDB()) {
            if (pLangConf.get(p.getUniqueId().toString()) == null) {
                String gameLanguage = p.getLocale().getLanguage().split("_")[0];
                String defaultLang = LanguageHelper.defaultLang;
                if (messages.get(gameLanguage) != null) {
                    defaultLang = gameLanguage;
                }

                pLangConf.set(p.getUniqueId().toString(), defaultLang);
                saveConfig(pLangConf, pLangFile);
                playerLangs.put(p.getUniqueId(), defaultLang);
                p.sendMessage(new TextComponent(getMess(p, "NoLangSet", true).replace("%default", defaultLang)));
            } else {
                String lang = pLangConf.getString(p.getUniqueId().toString());
                playerLangs.put(p.getUniqueId(), lang);
            }
        } else {
            Connection conn = database.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement("SELECT lang FROM langusers WHERE uuid = ?;")) {
                stmt.setString(1, p.getUniqueId().toString());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    String lang = rs.getString("lang");
                    playerLangs.put(p.getUniqueId(), lang);
                } else {
                    String gameLanguage = p.getLocale().getLanguage().split("_")[0];
                    String defaultLang = LanguageHelper.defaultLang;
                    if (messages.get(gameLanguage) != null) {
                        defaultLang = gameLanguage;
                    }

                    PreparedStatement statemt = conn.prepareStatement("INSERT INTO langusers(uuid, lang) VALUES(?, ?);");
                    statemt.setString(1, p.getUniqueId().toString());
                    statemt.setString(2, defaultLang);
                    statemt.execute();
                    statemt.close();

                    pLangConf.set(p.getUniqueId().toString(), defaultLang);
                    saveConfig(pLangConf, pLangFile);
                    playerLangs.put(p.getUniqueId(), defaultLang);
                    p.sendMessage(new TextComponent(getMess(p, "NoLangSet", true).replace("%default", defaultLang)));
                }
                conn.close();
                stmt.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private static void saveConfig(Configuration config, File toSave) {
        try {
            ConfigurationProvider.getProvider(YamlConfiguration.class).save(config, toSave);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void changeLang(UUID p, String newLang) {
        playerLangs.remove(p);
        playerLangs.put(p, newLang);
        pLangConf.set(p.toString(), newLang);
        saveConfig(pLangConf, pLangFile);

        if (isUseDB()) {
            Connection conn = database.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement("UPDATE langusers SET lang = ? WHERE uuid = ?;")) {
                stmt.setString(1, newLang);
                stmt.setString(2, p.toString());
                stmt.executeUpdate();
                stmt.close();
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static void remPlayer(ProxiedPlayer p) {
        playerLangs.remove(p.getUniqueId());
    }

    public static String getDefaultLang() {
        return defaultLang;
    }

    public static String getLanguage(ProxiedPlayer p) {
        return playerLangs.getOrDefault(p, defaultLang);
    }

    public static boolean isUseDB() {
        return useDB;
    }
}