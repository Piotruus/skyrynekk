package pl.skypvp.market;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class MarketPlugin extends JavaPlugin {

    private MarketManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "search-pl.txt").exists()) {
            saveResource("search-pl.txt", false);
        }

        manager = new MarketManager(this);
        manager.load();
        manager.loadSearch();

        // ekonomia rejestruje sie czasem chwile po starcie, wiec podpinamy ja w pierwszym ticku
        getServer().getScheduler().runTask(this, () -> {
            RegisteredServiceProvider<Economy> rsp =
                    getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp == null) {
                getLogger().severe("Nie znaleziono ekonomii! Zainstaluj Vault oraz plugin ekonomii (np. EssentialsX).");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            manager.setEconomy(rsp.getProvider());
        });

        getServer().getPluginManager().registerEvents(new MarketListener(manager), this);
        MarketCommand cmd = new MarketCommand(manager);
        getCommand("ah").setExecutor(cmd);
        getCommand("ah").setTabCompleter(cmd);

        getServer().getScheduler().runTaskTimer(this, manager::tick, 20L, 20L);
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.save();
    }
}
