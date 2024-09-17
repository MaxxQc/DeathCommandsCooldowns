package qc.maxx.deathcommandscooldowns;

import java.util.*;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class DeathCommandsCooldowns extends JavaPlugin implements Listener {
    private final Set<CooldownData> COOLDOWNS_SET = new HashSet<>();
    // uuid, deathWorldName
    private final Map<UUID, String> CD_TO_ADD_MAP = new HashMap<>();
    private final Map<String, Integer> CD_PERMISSIONS_MAP = new HashMap<>();
    // cmd, worldName
    private final Map<String, String> BLACKLIST_COMMANDS_MAP = new HashMap<>();

    private static final boolean DEBUG = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (DEBUG) {
            this.getLogger().warning("==== DEBUG ENABLED");
        }

        for (String data : new HashSet<>(getConfig().getStringList("blacklist-commands"))) {
            if (!data.contains("%")) {
                this.BLACKLIST_COMMANDS_MAP.put(data.toLowerCase(), null);
                continue;
            }

            int indexOf = data.indexOf("%");
            String worldName = data.substring(0, indexOf);
            String cmd = data.substring(indexOf + 1).toLowerCase();

            if (Bukkit.getWorld(worldName) == null) {
                this.getLogger().warning("World " + worldName + " not found.. command " + cmd + " will not be used");
            } else {
                this.BLACKLIST_COMMANDS_MAP.put(cmd, worldName);
            }
        }

        if (DEBUG) {
            this.getLogger().info("==== BLACKLIST COMMANDS");
            this.BLACKLIST_COMMANDS_MAP.forEach((key, value) -> this.getLogger().info("/" + key + " - in world " + value));
        }

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

        new BukkitRunnable() {
            @Override
            public void run() {
                COOLDOWNS_SET.removeIf(cd -> System.currentTimeMillis() > cd.getEndTime());

                if (DEBUG) {
                    getLogger().info("=== CLEAR TASK - " + COOLDOWNS_SET.size());
                }
            }
        }.runTaskTimerAsynchronously(this, 0L, 20L);
    }

    @Override
    public void onDisable() {
        if (DEBUG) {
            this.getLogger().info("==== PLUGIN DISABLED");
        }

        this.COOLDOWNS_SET.clear();
        this.BLACKLIST_COMMANDS_MAP.clear();
    }

    @EventHandler
    private void onJoin(PlayerJoinEvent event) {
        if (DEBUG) {
            this.getLogger().info("==== PLAYER JOINED " + event.getPlayer().getName());
        }

        applyCooldown(event.getPlayer());
    }

    @EventHandler
    private void onCommand(PlayerCommandPreprocessEvent event) {
        if (DEBUG) {
            this.getLogger().info("PlayerCommandPreprocessEvent COMMAND: " + event.getMessage());
        }

        if (!this.BLACKLIST_COMMANDS_MAP.containsKey(event.getMessage().toLowerCase().substring(1)))
            return;

        if (DEBUG) {
            this.getLogger().info("Command is blacklisted");
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String worldName = this.BLACKLIST_COMMANDS_MAP.get(event.getMessage().toLowerCase().substring(1));
        double remainingTime = getRemainingTimeCooldown(playerId, worldName);

        if (DEBUG) {
            this.getLogger().info("Remaining time: " + remainingTime);
        }

        if (remainingTime > 0) {
            if (DEBUG) {
                this.getLogger().info("Player " + event.getPlayer().getName() + " is still on cooldown");
            }

            player.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.cooldown").replace("{time}", String.format("%.1f", remainingTime))));
            event.setCancelled(true);
        }
    }

    @EventHandler
    private void onDeath(PlayerDeathEvent event) {
        UUID playerId = event.getEntity().getUniqueId();

        if (DEBUG) {
            this.getLogger().info("==== PLAYER DIED " + event.getEntity().getName() + " IN WORLD " + event.getEntity().getWorld().getName());
        }

        if (getConfig().getBoolean("events.on-death") || (getConfig().getBoolean("events.on-pvp-death") && event.getEntity().getKiller() != null)) {
            this.CD_TO_ADD_MAP.put(playerId, event.getEntity().getWorld().getName());

            if (DEBUG) {
                this.getLogger().info("==== CD TO ADD - " + event.getEntity().getName() + " IN WORLD " + event.getEntity().getWorld().getName());
            }
        }
    }

    @EventHandler
    private void onRespawn(PlayerRespawnEvent event) {
        if (DEBUG) {
            this.getLogger().info("==== PLAYER RESPAWNED " + event.getPlayer().getName());
        }

        applyCooldown(event.getPlayer());
    }

    private void addCooldown(Player player, String world) {
        int cooldown = this.getConfig().getInt("cooldowns.default", 0);

        for (Map.Entry<String, Integer> entry : this.CD_PERMISSIONS_MAP.entrySet()) {
            if (player.hasPermission(entry.getKey())) {
                cooldown = Math.min(cooldown, entry.getValue());
            }
        }

        if (DEBUG) {
            this.getLogger().info("==== cooldown for " + player.getName() + " is " + cooldown + " in world " + world);
        }

        CooldownData cooldownData = this.COOLDOWNS_SET.stream().filter(cd -> cd.getUuid().equals(player.getUniqueId()) && cd.getDeathWorld().equals(world)).findFirst().orElse(null);

        if (DEBUG) {
            this.getLogger().info("==== cooldown data: " + (cooldownData == null ? "does not exist" : "ALREADY EXISTS"));
        }

        if (cooldownData != null) {
            if (DEBUG) {
                this.getLogger().info("==== cooldown data updated: " + (System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(cooldown)));
            }

            cooldownData.setEndTime(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(cooldown));
            return;
        }

        this.COOLDOWNS_SET.add(new CooldownData(player.getUniqueId(), world, System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(cooldown)));

        if (DEBUG) {
            this.getLogger().info("==== cooldown data added: " + (System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(cooldown)));
        }
    }

    private double getRemainingTimeCooldown(UUID playerId, String worldName) {
        CooldownData cooldownData;

        if (DEBUG) {
            this.getLogger().info("== PlayerID " + playerId + " has cooldown for " + worldName + "?");
            this.getLogger().info("ALL COOLDOWN DATAS ->");
            this.COOLDOWNS_SET.forEach(cd -> this.getLogger().info("   " + cd.toString()));
        }

        if (worldName == null) {
            cooldownData = this.COOLDOWNS_SET.stream().filter(cd -> cd.getUuid().equals(playerId)).max(Comparator.comparingLong(CooldownData::getEndTime)).orElse(null);
        } else {
            cooldownData = this.COOLDOWNS_SET.stream().filter(cd -> cd.getUuid().equals(playerId) && cd.getDeathWorld().equals(worldName)).findFirst().orElse(null);
        }

        if (cooldownData == null) {
            if (DEBUG) {
                this.getLogger().info("==== cooldown data was null");
            }

            return 0.0;
        }

        if (DEBUG) {
            this.getLogger().info(cooldownData.toString());
        }

        return cooldownData.getRemainingTime();
    }

    private void applyCooldown(Player player) {
        UUID playerId = player.getUniqueId();

        if (this.CD_TO_ADD_MAP.containsKey(playerId)) {
            if (DEBUG) {
                this.getLogger().info("==== applying cooldown for " + player.getName());
            }

            addCooldown(player, this.CD_TO_ADD_MAP.get(playerId));
            this.CD_TO_ADD_MAP.remove(playerId);
        }
    }
}
