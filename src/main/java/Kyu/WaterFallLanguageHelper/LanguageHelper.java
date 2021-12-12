package Kyu.WaterFallLanguageHelper;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.util.*;

public final class LanguageHelper {

    private static String defaultLang;
    private static InputStream defaultLangResource;

    private static String prefix;

    private static Configuration pLangConf;
    private static File pLangFile;

    private static Map<String, Map<String, String>> messages = new HashMap<>();
    private static Map<String, Map<String, List<String>>> lores = new HashMap<>();

    private static Map<ProxiedPlayer, String> playerLangs = new HashMap<>();

    private static Plugin plugin;

    public static void setup(Plugin plugin, String defaultLang, InputStream langResource, String prefix) {
        LanguageHelper.plugin = plugin;
        LanguageHelper.defaultLang = defaultLang;
        LanguageHelper.defaultLangResource = langResource;
        LanguageHelper.prefix = prefix;

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


    public static List<String> getLore(ProxiedPlayer p, String loreKey) {
        String pLang;
        if (!playerLangs.containsKey(p)) {
            pLang = defaultLang;
            setupPlayer(p);
        } else {
            pLang = playerLangs.get(p);
        }
        if (!lores.containsKey(pLang)) {
            return lores.get(defaultLang).getOrDefault(loreKey, new ArrayList<>(Arrays.asList(color("&cLore &4 " + loreKey + " &c not found!"))));
        } else {
            return lores.get(pLang).getOrDefault(loreKey, new ArrayList<>(Arrays.asList(color("&cLore &4 " + loreKey + " &c not found!"))));
        }
    }

    public static List<String> getLore(String loreKey) {
        return lores.get(defaultLang).getOrDefault(loreKey, new ArrayList<>(Arrays.asList(color("&cLore &4 " + loreKey + " &c not found!"))));
    }

    public static String getMess(ProxiedPlayer p, String messageKey, boolean... usePrefix) {
        String pLang;
        if (!playerLangs.containsKey(p)) {
            pLang = defaultLang;
            setupPlayer(p);
        } else {
            pLang = playerLangs.get(p);
        }
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

    public static String getMess(String messageKey, boolean... usePrefix) {
        String message = messages.get(defaultLang).getOrDefault(messageKey, color("&cMessage &4" + messageKey + "&c not found!"));
        if (usePrefix.length > 0 && usePrefix[0]) {
            message = prefix + message;
        }
        return message;
    }

    public static void setupPlayer(ProxiedPlayer p) {
        if (pLangConf.get(p.getUniqueId().toString()) == null) {
            p.sendMessage(new TextComponent(getMess("NoLangSet", true).replace("%default", defaultLang)));
            pLangConf.set(p.getUniqueId().toString(), defaultLang);
            saveConfig(pLangConf, pLangFile);
            playerLangs.put(p, defaultLang);
        } else {
            String lang = pLangConf.getString(p.getUniqueId().toString());
            playerLangs.put(p, lang);
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


    public static void changeLang(ProxiedPlayer p, String newLang) {
        playerLangs.remove(p);
        playerLangs.put(p, newLang);
        pLangConf.set(p.getUniqueId().toString(), newLang);
        saveConfig(pLangConf, pLangFile);
    }

    public static void remPlayer(ProxiedPlayer p) {
        playerLangs.remove(p);
    }

    public static String getDefaultLang() {
        return defaultLang;
    }
}