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
import java.io.Reader;
import java.nio.file.Files;
import java.util.*;

public final class LanguageHelper {

    private String defaultLang;
    private Map<String, Reader> langRessources;

    private String prefix;

    private Configuration pLangConf;
    private File pLangFile;

    private DB database;
    private boolean useDB;

    private Map<String, Map<String, String>> messages = new HashMap<>();
    private Map<String, Map<String, List<String>>> lores = new HashMap<>();

    private Map<UUID, String> playerLangs = new HashMap<>();

    private Plugin plugin;

    private static LanguageHelper instance;

    public LanguageHelper(Plugin plugin, String defaultLang, Reader langResource, String resourceName, String prefix, DB... database) {
        LanguageHelper.instance = this;
        this.plugin = plugin;
        this.defaultLang = defaultLang;
        this.langRessources = new HashMap<>() {{ put(resourceName, langResource); }};
        this.prefix = prefix;
        if (database.length > 0) {
            this.useDB = true;
            this.database = database[0];
            this.database.init();
        }

        setup();
    }

    public LanguageHelper(Plugin plugin, String defaultLang, Map<String, Reader> langResources, String prefix, DB... database) {
        LanguageHelper.instance = this;
        this.plugin = plugin;
        this.defaultLang = defaultLang;
        this.langRessources = langResources;
        this.prefix = prefix;
        if (database.length > 0) {
            this.useDB = true;
            this.database = database[0];
            this.database.init();
        }

        setup();
    }


    private void setup() {
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

        if (isUseDB() && this.database.isStoreMessagesInDB()) {
            loadMessagesDB();
        } else {
            loadMessagesLocal();
        }
        new MessageJoinListener(plugin);
    }

    public void setDatabase(DB database) {
        this.database = database;
    }

    private void loadMessagesDB() {
        updateLangsDB();
        messages = this.database.getMessages();
        lores = this.database.getLores();
    }

    private void loadMessagesLocal() {
        updateLangsLocal();

        File folder = new File(plugin.getDataFolder() + "/locales");
        for (File file : folder.listFiles()) {
            Map<String, String> langMessages = new HashMap<>();
            Map<String, List<String>> langLores = new HashMap<>();
            String name = file.getName().split(".yml")[0];
            Configuration conf;
            try {
                conf = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
            } catch(IOException e) {
                e.printStackTrace();
                return;
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

    private void updateLangsDB() {
        for (String langRessourceName : langRessources.keySet()) {
            Configuration refConf = ConfigurationProvider.getProvider(YamlConfiguration.class).load(langRessources.get(langRessourceName));
            final String langName = langRessourceName;
            for (String topKey : refConf.getKeys()) {
                for (String mess : refConf.getSection(topKey).getKeys()) {
                    if (!database.hasKey(langName, topKey, mess)) {
                        if (topKey.toLowerCase().contains("lores")) {
                            database.setLore(langName, topKey, mess, refConf.getStringList(topKey + "." + mess));
                        } else {
                            database.setMessage(langName, topKey, mess, refConf.getString(topKey + "." + mess));
                        }
                    }
                }
            }
        }
    }

    private void updateLangsLocal() {
        for (String langResourceName : langRessources.keySet()) {
            Configuration refConf = ConfigurationProvider.getProvider(YamlConfiguration.class).load(langRessources.get(langResourceName));
            final String langName = langResourceName;

            File file = new File(plugin.getDataFolder(), "locales/" + langName);
            if (!file.exists()) {
                try {
                    Files.copy(plugin.getResourceAsStream(langName), file.toPath());
                    return;
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            }

            Configuration langConfig;
            try {
                langConfig = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            for (String topKey : refConf.getKeys()) {
                for (String mess : refConf.getSection(topKey).getKeys()) {
                    if (langConfig.get(topKey + "." + mess) == null) {
                        if (topKey.toLowerCase().contains("lores")) {
                            langConfig.set(topKey + "." + mess, refConf.getStringList(topKey + "." + mess));
                        } else {
                            langConfig.set(topKey + "." + mess, refConf.getString(topKey + "." + mess));
                        }
                    }
                }
            }
            saveConfig(langConfig, file);
        }
    }

    public List<String> getLore(String pLang, String loreKey) {
        if (!lores.containsKey(pLang)) {
            return lores.get(defaultLang).getOrDefault(loreKey, new ArrayList<>(Arrays.asList(color("&cLore &4 " + loreKey + " &c not found!"))));
        } else {
            return lores.get(pLang).getOrDefault(loreKey, new ArrayList<>(Arrays.asList(color("&cLore &4 " + loreKey + " &c not found!"))));
        }
    }

    public List<String> getLore(CommandSender p, String loreKey) {
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

    public List<String> getLore(String loreKey) {
        return lores.get(defaultLang).getOrDefault(loreKey, new ArrayList<>(Arrays.asList(color("&cLore &4 " + loreKey + " &c not found!"))));
    }

    public String getMess(String pLang, String messageKey, boolean... usePrefix) {
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

    public String getMess(CommandSender p, String messageKey, boolean... usePrefix) {
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

    public String getMess(String messageKey, boolean... usePrefix) {
        String message = messages.get(defaultLang).getOrDefault(messageKey, color("&cMessage &4" + messageKey + "&c not found!"));
        if (usePrefix.length > 0 && usePrefix[0]) {
            message = prefix + message;
        }
        return message;
    }

    public void setupPlayer(ProxiedPlayer p) {
        if (!isUseDB()) {
            if (pLangConf.get(p.getUniqueId().toString()) == null) {
                String gameLanguage = p.getLocale().getLanguage().split("_")[0];
                String defaultLang = this.defaultLang;
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
            this.database.setupPlayer(p);
        }
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    void saveConfig(Configuration config, File toSave) {
        try {
            ConfigurationProvider.getProvider(YamlConfiguration.class).save(config, toSave);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void changeLang(UUID p, String newLang) {
        playerLangs.remove(p);
        playerLangs.put(p, newLang);
        pLangConf.set(p.toString(), newLang);
        saveConfig(pLangConf, pLangFile);

        if (isUseDB()) {
            this.database.updateUser(p.toString(), newLang);
        }
    }

    public void remPlayer(ProxiedPlayer p) {
        playerLangs.remove(p.getUniqueId());
    }

    public String getDefaultLang() {
        return defaultLang;
    }

    public String getLanguage(ProxiedPlayer p) {
        return playerLangs.getOrDefault(p, defaultLang);
    }

    public Set<String> getLanguages() {
        return messages.keySet();
    }

    public Map<UUID, String> getPlayerLangs() {
        return playerLangs;
    }

    public Map<String, Map<String, String>> getMessages() {
        return messages;
    }

    public Configuration getpLangConf() {
        return pLangConf;
    }

    public File getpLangFile() {
        return pLangFile;
    }


    public boolean isUseDB() {
        return useDB;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    static LanguageHelper getInstance() {
        return instance;
    }
}