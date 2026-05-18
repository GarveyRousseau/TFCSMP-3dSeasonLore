package com.tfcsmp.seasonlore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public final class FactionManager {
    private final JavaPlugin plugin;
    private final Map<UUID, Faction> factions = new HashMap<>();
    private BukkitTask task;
    private VoidZoneLookup voidZoneLookup = location -> false;

    public FactionManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public interface VoidZoneLookup {
        boolean isInVoidZone(Location location);
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
                factions.put(UUID.fromString(key), Faction.parse(section.getString(key, "none")));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Неверная faction-запись в config.yml: " + key);
            }
        }
    }

    public void save() {
        plugin.getConfig().set("runtime.factions", null);
        for (Map.Entry<UUID, Faction> entry : factions.entrySet()) {
            if (entry.getValue() != Faction.NONE) {
                plugin.getConfig().set("runtime.factions." + entry.getKey(), entry.getValue().id());
            }
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

    public Faction faction(Player player) {
        return factions.getOrDefault(player.getUniqueId(), Faction.NONE);
    }

    public void setFaction(Player player, Faction faction) {
        if (faction == Faction.NONE) {
            factions.remove(player.getUniqueId());
        } else {
            factions.put(player.getUniqueId(), faction);
        }
        save();
        player.sendMessage(ChatColor.DARK_PURPLE + "Твой путь: " + ChatColor.LIGHT_PURPLE + faction.displayName());
        switch (faction) {
            case SEALERS -> player.sendMessage(ChatColor.GRAY + "Плюс: защита рядом с заражением. Минус: глубина давит сильнее.");
            case ENTROPY -> player.sendMessage(ChatColor.GRAY + "Плюс: сила внутри заражения. Минус: чистая поверхность истощает.");
            case LONERS -> player.sendMessage(ChatColor.GRAY + "Плюс: скорость в одиночку. Минус: слабость рядом с группой.");
            case NONE -> player.sendMessage(ChatColor.GRAY + "Ты отказался от стороны. Плюсов и минусов не будет.");
        }
    }

    private void applyEffects() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Faction faction = faction(player);
            if (faction == Faction.NONE) {
                continue;
            }
            boolean inVoidZone = voidZoneLookup.isInVoidZone(player.getLocation());
            long nearbyPlayers = player.getNearbyEntities(18, 8, 18).stream().filter(entity -> entity instanceof Player).count();
            switch (faction) {
                case SEALERS -> applySealer(player, inVoidZone);
                case ENTROPY -> applyEntropy(player, inVoidZone);
                case LONERS -> applyLoner(player, nearbyPlayers);
                default -> {
                }
            }
        }
    }

    private void applySealer(Player player, boolean inVoidZone) {
        if (inVoidZone) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 140, 0, true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 0, true, false));
        }
        if (player.getLocation().getY() < -40) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 0, true, false));
        }
    }

    private void applyEntropy(Player player, boolean inVoidZone) {
        if (inVoidZone) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 120, 0, true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 220, 0, true, false));
        } else if (player.getWorld().isClearWeather() && player.getLocation().getY() > 60) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 100, 0, true, false));
        }
    }

    private void applyLoner(Player player, long nearbyPlayers) {
        if (nearbyPlayers == 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 0, true, false));
        } else if (nearbyPlayers >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 120, 0, true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 0, true, false));
        }
    }
}
