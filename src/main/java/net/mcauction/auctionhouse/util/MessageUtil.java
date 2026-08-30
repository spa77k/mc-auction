package net.mcauction.auctionhouse.util;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;

public class MessageUtil {

    private final FileConfiguration config;

    public MessageUtil(FileConfiguration config) {
        this.config = config;
    }

    public String get(String path) {
        String raw = config.getString("messages." + path, path);
        return colorize(raw);
    }

    public String get(String path, Map<String, String> placeholders) {
        String raw = config.getString("messages." + path, path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return colorize(raw);
    }

    public void send(CommandSender sender, String path) {
        sender.sendMessage(get("prefix") + get(path));
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        sender.sendMessage(get("prefix") + get(path, placeholders));
    }

    public String formatted(String path, Map<String, String> placeholders) {
        return get("prefix") + get(path, placeholders);
    }

    public String formatted(String path) {
        return get("prefix") + get(path);
    }

    public String colorize(String text) {
        if (text == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
