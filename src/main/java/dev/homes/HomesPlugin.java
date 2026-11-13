package dev.homes;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class HomesPlugin extends JavaPlugin {
    private HomeManager homes;
    private IconResolver icons;
    private HomeMenus menus;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        homes = new HomeManager(this);
        icons = new IconResolver();
        icons.loadOverrides(getConfig().getConfigurationSection("icon-overrides"));
        icons.setPackBeds(getConfig().getBoolean("use-resource-pack-beds", true));
        menus = new HomeMenus(this);
        loadIconAssets();
        getServer().getPluginManager().registerEvents(new HomeListener(this), this);
    }

    /** Exact icons from the real game files; cached in icon-cache.yml per Minecraft version. */
    private void loadIconAssets() {
        if (!getConfig().getBoolean("auto-resolve-icons", true)) return;
        String version = getServer().getMinecraftVersion();
        java.io.File cache = new java.io.File(getDataFolder(), "icon-cache.yml");
        if (cache.exists()) {
            org.bukkit.configuration.file.YamlConfiguration y =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(cache);
            if (version.equals(y.getString("version")) && y.getConfigurationSection("icons") != null) {
                java.util.Map<String, String> m = new java.util.HashMap<>();
                for (String k : y.getConfigurationSection("icons").getKeys(false)) m.put(k, y.getString("icons." + k));
                icons.setResolved(m);
                return;
            }
        }
        java.util.List<String> keys = icons.keys();
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                getDataFolder().mkdirs();
                java.util.Map<String, String> m = AssetIndexer.build(getDataFolder(), version, keys);
                if (m == null) {
                    getLogger().warning("Could not find Minecraft " + version + " to read icon textures; using built-in guesses.");
                    return;
                }
                icons.setResolved(m);
                org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
                y.set("version", version);
                for (java.util.Map.Entry<String, String> e : m.entrySet()) y.set("icons." + e.getKey(), e.getValue());
                y.save(cache);
                getLogger().info("Resolved " + m.size() + " item icons from Minecraft " + version + ".");
            } catch (Exception e) {
                getLogger().warning("Icon lookup failed (" + e + "); using built-in guesses. Set auto-resolve-icons: false to silence.");
            }
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("homes.admin")) {
                sender.sendMessage("You do not have permission.");
                return true;
            }
            reloadConfig();
            icons.loadOverrides(getConfig().getConfigurationSection("icon-overrides"));
            icons.setPackBeds(getConfig().getBoolean("use-resource-pack-beds", true));
            sender.sendMessage("Homes config reloaded.");
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        menus.openList(p);
        return true;
    }

    public HomeManager homes() { return homes; }
    public IconResolver icons() { return icons; }
    public HomeMenus menus() { return menus; }
}
