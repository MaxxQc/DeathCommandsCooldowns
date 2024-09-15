package qc.maxx.deathcommandscooldowns;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class DeathCommandsCooldowns extends JavaPlugin implements Listener {
    private final Map<UUID, Long> COOLDOWNS_MAP = new HashMap<>();
    private final Set<UUID> CD_TO_ADD_SET = new HashSet<>();
    private final Map<String, Integer> CD_PERMISSIONS_MAP = new HashMap<>();
    private final Set<String> BLACKLIST_COMMANDS_SET = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.BLACKLIST_COMMANDS_SET.addAll(getConfig().getStringList("blacklist-commands").stream().map(String::toLowerCase).collect(Collectors.toSet()));

        for (String key : getConfig().getConfigurationSection("cooldowns").getKeys(true)) {
            if (!key.equals("default")) {
                this.CD_PERMISSIONS_MAP.put(key.replace('_', '.'), getConfig().getInt("cooldowns." + key));
            }
        }

        if (getConfig().getBoolean("events.on-death") || getConfig().getBoolean("events.on-pvp-death")) {
            getServer().getPluginManager().registerEvents(this, this);
        } else {
            getLogger().info("No events selected - shutting down..");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        this.COOLDOWNS_MAP.clear();
    }

    @EventHandler
    private void onJoin(PlayerJoinEvent event) {
        applyCooldown(event.getPlayer());
    }

    @EventHandler
    private void onCommand(PlayerCommandPreprocessEvent event) {
        if (!this.BLACKLIST_COMMANDS_SET.contains(event.getMessage().toLowerCase().substring(1)))
            return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (hasCooldown(playerId)) {
            double remainingTime = (this.COOLDOWNS_MAP.get(playerId) - System.currentTimeMillis()) / 1000.0D;
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.cooldown").replace("{time}", String.format("%.1f", remainingTime))));
            event.setCancelled(true);
        }
    }

    @EventHandler
    private void onDeath(PlayerDeathEvent event) {
        UUID playerId = event.getEntity().getUniqueId();

        if (getConfig().getBoolean("events.on-death")) {
            this.CD_TO_ADD_SET.add(playerId);
        } else if (getConfig().getBoolean("events.on-pvp-death") && event.getEntity().getKiller() != null) {
            this.CD_TO_ADD_SET.add(playerId);
        }
    }

    @EventHandler
    private void onRespawn(PlayerRespawnEvent event) {
        applyCooldown(event.getPlayer());
    }

    private void addCooldown(Player player) {
        int cooldown = this.getConfig().getInt("cooldowns.default", 0);

        for (Map.Entry<String, Integer> entry : this.CD_PERMISSIONS_MAP.entrySet()) {
            if (player.hasPermission(entry.getKey())) {
                cooldown = Math.min(cooldown, entry.getValue());
            }
        }

        this.COOLDOWNS_MAP.put(player.getUniqueId(), System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(cooldown));
    }

    private boolean hasCooldown(UUID playerId) {
        long endTime = this.COOLDOWNS_MAP.getOrDefault(playerId, 0L);

        if (endTime == 0L) {
            return false;
        }

        if (System.currentTimeMillis() > endTime) {
            this.COOLDOWNS_MAP.remove(playerId);
            return false;
        }

        return true;
    }

    private void applyCooldown(Player player) {
        UUID playerId = player.getUniqueId();

        if (this.CD_TO_ADD_SET.contains(playerId)) {
            addCooldown(player);
            this.CD_TO_ADD_SET.remove(playerId);
        }
    }
}
