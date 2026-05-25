package com.tfcsmp.seasonlore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public final class FactionManager {
    private final JavaPlugin plugin;
    private final Map<UUID, FactionState> factions = new HashMap<>();
    private BukkitTask task;
    private VoidZoneLookup voidZoneLookup = location -> false;

    public FactionManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public interface VoidZoneLookup {
        boolean isInVoidZone(Location location);
    }

    public record FactionState(Faction faction, int level) {
    }

    public void setVoidZoneLookup(VoidZoneLookup voidZoneLookup) {
        this.voidZoneLookup = voidZoneLookup;
    }

    public void load() {
        factions.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("runtime.factions");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                ConfigurationSection playerSection = section.getConfigurationSection(key);
                if (playerSection == null) {
                    continue;
                }
                Faction faction = Faction.parse(playerSection.getString("faction", "none"));
                int level = Math.max(0, Math.min(3, playerSection.getInt("level", faction == Faction.NONE ? 0 : 1)));
                if (faction != Faction.NONE) {
                    factions.put(UUID.fromString(key), new FactionState(faction, level));
                }
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Неверная faction-запись в config.yml: " + key);
            }
        }
    }

    public void save() {
        plugin.getConfig().set("runtime.factions", null);
        for (Map.Entry<UUID, FactionState> entry : factions.entrySet()) {
            String path = "runtime.factions." + entry.getKey();
            plugin.getConfig().set(path + ".faction", entry.getValue().faction().id());
            plugin.getConfig().set(path + ".level", entry.getValue().level());
        }
        plugin.saveConfig();
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::applyEffects, 40L, 100L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public FactionState state(Player player) {
        return factions.getOrDefault(player.getUniqueId(), new FactionState(Faction.NONE, 0));
    }

    public void assignFaction(Player player, Faction faction, int level) {
        int clampedLevel = Math.max(0, Math.min(3, level));
        if (faction == Faction.NONE || clampedLevel == 0) {
            factions.remove(player.getUniqueId());
            save();
            return;
        }
        factions.put(player.getUniqueId(), new FactionState(faction, clampedLevel));
        save();
    }

    public void setLevel(Player player, int level) {
        FactionState current = state(player);
        if (current.faction() == Faction.NONE) {
            return;
        }
        assignFaction(player, current.faction(), level);
    }

    public String adminSummary(Player player) {
        FactionState state = state(player);
        if (state.faction() == Faction.NONE || state.level() == 0) {
            return "нет";
        }
        return state.faction().displayName() + " L" + state.level();
    }

    private void applyEffects() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            FactionState state = state(player);
            if (state.faction() == Faction.NONE || state.level() <= 0) {
                continue;
            }
            boolean inVoidZone = voidZoneLookup.isInVoidZone(player.getLocation());
            long nearbyPlayers = player.getNearbyEntities(18, 8, 18).stream().filter(entity -> entity instanceof Player).count();
            switch (state.faction()) {
                case SEALERS -> applySealer(player, state.level(), inVoidZone);
                case ENTROPY -> applyEntropy(player, state.level(), inVoidZone);
                case LONERS -> applyLoner(player, state.level(), nearbyPlayers);
                default -> {
                }
            }
        }
    }

    private void applySealer(Player player, int level, boolean inVoidZone) {
        if (inVoidZone) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 140, Math.max(0, level - 1), true, false));
            if (level >= 2) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, level - 2, true, false));
            }
        }
        if (player.getLocation().getY() < -40 && level >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, level - 2, true, false));
        }
    }

    private void applyEntropy(Player player, int level, boolean inVoidZone) {
        if (inVoidZone) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 120, Math.max(0, level - 1), true, false));
            if (level >= 2) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 220, 0, true, false));
            }
        } else if (player.getWorld().isClearWeather() && player.getLocation().getY() > 60 && level >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, level - 2, true, false));
        }
    }

    private void applyLoner(Player player, int level, long nearbyPlayers) {
        if (nearbyPlayers == 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, Math.max(0, level - 1), true, false));
        } else if (nearbyPlayers >= 2 && level >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 120, level - 2, true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, Math.max(0, level - 2), true, false));
        }
    }
}
