package Kyu.WaterFallLanguageHelper;

import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

public class MessageJoinListener implements Listener {

    public MessageJoinListener(Plugin plugin) {
        plugin.getProxy().getPluginManager().registerListener(plugin, this);
    }

    @EventHandler
    public void onJoin(PostLoginEvent e) {
        LanguageHelper.setupPlayer(e.getPlayer());
    }

    @EventHandler
    public void onLeave(PlayerDisconnectEvent e) {
        LanguageHelper.remPlayer(e.getPlayer());
    }

}
