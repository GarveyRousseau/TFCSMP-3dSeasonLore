package com.tfcsmp.seasonlore;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.util.Vector;

public final class SeasonLorePlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final String DEBUG_OBJECTIVE = "tfc_void_debug";

    private final Random random = new SecureRandom();
    private final Map<LoreEvent, BukkitTask> activeTasks = new EnumMap<>(LoreEvent.class);
    private final Map<UUID, Long> zoneCooldowns = new HashMap<>();
    private final Map<UUID, BossBar> debugBars = new HashMap<>();
    private final Set<UUID> debugViewers = new HashSet<>();
    private final List<VoidZone> voidZones = new ArrayList<>();
    private BossBar skyCrackBar;
    private BukkitTask automationTask;
    private BukkitTask debugTask;
    private long automationPeriodMillis;
    private long nextAutomationAtMillis;
    private String lastAutomationEvent = "ещё не было";
    private int phase;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        phase = getConfig().getInt("lore.phase", 0);
        loadVoidZones();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("function").setExecutor(this);
        getCommand("function").setTabCompleter(this);
        startAutomation();
        startDebugUpdater();
        getLogger().info("TFCSMP Season Lore включён для Paper 1.21.11. Текущая фаза: " + phaseName());
    }

    @Override
    public void onDisable() {
        stopAllEvents();
        stopDebugForAll();
        if (debugTask != null) {
            debugTask.cancel();
        }
        saveRuntimeState();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        sendResourcePack(player);
        if (phase == 0) {
            List<String> messages = getConfig().getStringList("lore.pre-season-messages");
            if (!messages.isEmpty()) {
                Bukkit.getScheduler().runTaskLater(this, () -> player.sendMessage(messages.get(random.nextInt(messages.size()))), 40L);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        stopDebug(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!getConfig().getBoolean("breach.auto-trigger-on-bedrock-break", true)) {
            return;
        }
        Block block = event.getBlock();
        if (block.getType() == Material.BEDROCK && block.getY() <= getConfig().getInt("breach.min-y", -58)) {
            event.setCancelled(true);
            triggerEvent(LoreEvent.BREACH, event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return;
        }
        Player player = event.getPlayer();
        VoidZone zone = zoneAt(player.getLocation());
        if (zone == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (zoneCooldowns.getOrDefault(player.getUniqueId(), 0L) > now) {
            return;
        }
        zoneCooldowns.put(player.getUniqueId(), now + 3500L);
        player.spawnParticle(Particle.ASH, player.getLocation().add(0, 1, 0), 20, 1.5, 0.9, 1.5, 0.01);
        player.spawnParticle(Particle.PORTAL, player.getLocation().add(0, 0.4, 0), 12, 0.6, 0.5, 0.6, 0.08);
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, getConfig().getInt("void-zones.darkness-seconds", 4) * 20, 0, true, false));
        player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 0.45f, 0.45f);
        if (player.getLocation().getY() < getConfig().getInt("void-zones.damage-below-y", -54)) {
            player.damage(1.0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (activeTasks.containsKey(LoreEvent.ANIMALS_WATCHING) && event.getEntity() instanceof Animals) {
            event.setCancelled(true);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tfcsmp.lore.admin")) {
            sender.sendMessage(ChatColor.RED + "У тебя нет доступа к движку лора.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "start" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Использование: /" + label + " start <ивент> [игрок]");
                    return true;
                }
                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : playerOrRandom(sender);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Нет онлайн-игрока для цели.");
                    return true;
                }
                try {
                    LoreEvent event = LoreEvent.parse(args[1]);
                    triggerEvent(event, target);
                    sender.sendMessage(ChatColor.DARK_PURPLE + "Запущено: " + event.displayName() + " рядом/для " + target.getName() + ".");
                } catch (IllegalArgumentException exception) {
                    sender.sendMessage(ChatColor.RED + exception.getMessage());
                }
                return true;
            }
            case "stop" -> {
                if (args.length >= 2 && !args[1].equalsIgnoreCase("all")) {
                    try {
                        LoreEvent event = LoreEvent.parse(args[1]);
                        stopEvent(event);
                        sender.sendMessage(ChatColor.GRAY + "Остановлено: " + event.displayName() + ".");
                    } catch (IllegalArgumentException exception) {
                        sender.sendMessage(ChatColor.RED + exception.getMessage());
                    }
                } else {
                    stopAllEvents();
                    sender.sendMessage(ChatColor.GRAY + "Все активные лор-ивенты остановлены.");
                }
                return true;
            }
            case "phase" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.DARK_PURPLE + "Текущая фаза: " + phaseName());
                    return true;
                }
                int newPhase = Math.max(0, Math.min(5, Integer.parseInt(args[1])));
                setPhase(newPhase);
                sender.sendMessage(ChatColor.DARK_PURPLE + "Фаза лора изменена: " + phaseName() + ".");
                return true;
            }
            case "zone" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Только игрок может создать зону по своей позиции.");
                    return true;
                }
                int radius = args.length >= 2 ? Integer.parseInt(args[1]) : getConfig().getInt("void-zones.ambient-radius", 48);
                addVoidZone(player.getLocation(), radius);
                sender.sendMessage(ChatColor.DARK_PURPLE + "Зона Пустоты добавлена. Радиус: " + radius + ".");
                return true;
            }
            case "debug" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Debug-меню видно только игроку в игре.");
                    return true;
                }
                boolean enable = args.length < 2 || !args[1].equalsIgnoreCase("off");
                if (enable) {
                    startDebug(player);
                    sender.sendMessage(ChatColor.GREEN + "Debug-меню лора включено: sidebar + bossbar.");
                } else {
                    stopDebug(player);
                    sender.sendMessage(ChatColor.GRAY + "Debug-меню лора выключено.");
                }
                return true;
            }
            case "status" -> {
                sender.sendMessage(ChatColor.DARK_PURPLE + "Фаза: " + phaseName());
                sender.sendMessage(ChatColor.DARK_PURPLE + "Следующая проверка автоматики: " + secondsUntilNextAutomation() + " сек.");
                sender.sendMessage(ChatColor.DARK_PURPLE + "Следующий перелом: " + nextBreakingPoint());
                sender.sendMessage(ChatColor.DARK_PURPLE + "Зон Пустоты: " + voidZones.size());
                sender.sendMessage(ChatColor.DARK_PURPLE + "Активные ивенты: " + activeTasks.keySet().stream().map(LoreEvent::displayName).collect(Collectors.joining(", ")));
                return true;
            }
            case "reload" -> {
                reloadConfig();
                phase = getConfig().getInt("lore.phase", phase);
                loadVoidZones();
                startAutomation();
                sender.sendMessage(ChatColor.GREEN + "Конфиг TFCSMP-лора перезагружен.");
                return true;
            }
            default -> {
                sendHelp(sender, label);
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("start", "stop", "phase", "status", "zone", "debug", "reload", "help"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("stop"))) {
            List<String> events = Arrays.stream(LoreEvent.values()).map(event -> event.name().toLowerCase(Locale.ROOT)).toList();
            return filter(args[1], events);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            return filter(args[1], List.of("on", "off"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("start")) {
            return filter(args[2], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        return Collections.emptyList();
    }

    private void triggerEvent(LoreEvent event, Player anchor) {
        lastAutomationEvent = event.displayName() + " → " + anchor.getName();
        switch (event) {
            case BREACH -> eventBreach(anchor);
            case GHOST_JOIN -> eventGhostJoin();
            case MISSING_BLOCKS -> eventMissingBlocks(anchor);
            case WRONG_SOUNDS -> eventWrongSounds(anchor);
            case ANIMALS_WATCHING -> eventAnimalsWatching(anchor);
            case SHADOW_MARK -> eventShadowMark(anchor);
            case FAKE_DEATH_MESSAGE -> eventFakeDeathMessage(anchor);
            case VOID_ZONE -> eventVoidZone(anchor);
            case SINK -> eventSink(anchor);
            case ECHO -> eventEcho(anchor);
            case COPY -> eventCopy(anchor);
            case VOID_PULL -> eventVoidPull(anchor);
            case MIRROR_STEP -> eventMirrorStep(anchor);
            case INVENTORY_ECHO -> eventInventoryEcho(anchor);
            case LAB -> eventLab(anchor);
            case WHISPER -> eventWhisper(anchor);
            case COMPASS_BETRAYAL -> eventCompassBetrayal(anchor);
            case NIGHTMARE -> eventNightmare(anchor);
            case BLACK_RAIN -> eventBlackRain(anchor.getWorld());
            case SILENCE -> eventSilence(anchor.getWorld());
            case RITUAL_MARK -> eventRitualMark(anchor);
            case MOB_POSSESSION -> eventMobPossession(anchor);
            case CHUNK_ROT -> eventChunkRot(anchor);
            case SKY_CRACK -> eventSkyCrack(anchor.getWorld());
            case GRAVITY_FAILURE -> eventGravityFailure(anchor);
            case FINAL_WHISPER -> eventFinalWhisper(anchor);
            case FINAL_SEAL -> eventFinalSeal(anchor.getWorld());
            case FINAL_ENTROPY -> eventFinalEntropy(anchor.getWorld());
        }
    }

    private void eventBreach(Player player) {
        Location breach = player.getLocation().clone();
        World world = breach.getWorld();
        if (world == null) {
            return;
        }
        setPhase(Math.max(phase, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 120, 1, true, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 180, 0, true, false));
        world.playSound(breach, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 2f, 0.55f);
        world.playSound(breach, Sound.ENTITY_WARDEN_HEARTBEAT, 1.4f, 0.6f);
        world.setStorm(true);
        world.setThundering(true);
        world.spawnParticle(Particle.SMOKE, breach, 140, 2.5, 1.2, 2.5, 0.02);
        world.spawnParticle(Particle.PORTAL, breach, 80, 1.2, 0.8, 1.2, 0.2);
        crackFloor(breach, 3);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            repairBedrockSeal(breach);
            broadcastSubtle("§8Сервер будто пропустил один удар сердца.");
        }, 35L);
        addVoidZone(breach, getConfig().getInt("void-zones.ambient-radius", 48));
    }

    private void eventGhostJoin() {
        Bukkit.broadcastMessage(ChatColor.YELLOW + "[НЕИЗВЕСТНО] вошёл в игру");
        Bukkit.getScheduler().runTaskLater(this, () -> Bukkit.broadcastMessage(ChatColor.YELLOW + "[НЕИЗВЕСТНО] вышел из игры"), 25L);
    }

    private void eventMissingBlocks(Player player) {
        List<Block> candidates = new ArrayList<>();
        Location base = player.getLocation();
        for (int x = -5; x <= 5; x++) {
            for (int y = -2; y <= 4; y++) {
                for (int z = -5; z <= 5; z++) {
                    Block block = base.clone().add(x, y, z).getBlock();
                    if (block.getType().isSolid() && block.getType() != Material.BEDROCK && block.getType() != Material.CHEST) {
                        candidates.add(block);
                    }
                }
            }
        }
        if (!candidates.isEmpty()) {
            Block chosen = candidates.get(random.nextInt(candidates.size()));
            if (chosen.getType() == Material.GLASS || chosen.getType() == Material.GLASS_PANE) {
                chosen.getWorld().playSound(chosen.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 0.6f);
            }
            chosen.setType(Material.AIR, false);
            player.sendMessage(ChatColor.DARK_GRAY + "Рядом чего-то не хватает.");
        }
    }

    private void eventWrongSounds(Player player) {
        Sound[] sounds = {Sound.AMBIENT_CAVE, Sound.BLOCK_STONE_STEP, Sound.ENTITY_GENERIC_EXPLODE, Sound.ENTITY_ENDERMAN_STARE};
        Location location = player.getLocation().add(random.nextInt(11) - 5, 0, random.nextInt(11) - 5);
        player.playSound(location, sounds[random.nextInt(sounds.length)], 0.8f, 0.45f + random.nextFloat() * 0.3f);
        player.sendMessage(ChatColor.DARK_GRAY + "Звук был не там, где должен быть.");
    }

    private void eventAnimalsWatching(Player player) {
        stopEvent(LoreEvent.ANIMALS_WATCHING);
        List<LivingEntity> frozen = player.getNearbyEntities(24, 8, 24).stream()
            .filter(entity -> entity instanceof Animals)
            .map(entity -> (LivingEntity) entity)
            .toList();
        for (LivingEntity animal : frozen) {
            animal.setAI(false);
            animal.teleport(animal.getLocation().setDirection(player.getLocation().toVector().subtract(animal.getLocation().toVector())));
        }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
            for (LivingEntity animal : frozen) {
                if (!animal.isDead()) {
                    animal.setAI(true);
                }
            }
            activeTasks.remove(LoreEvent.ANIMALS_WATCHING);
        }, 20L * 20);
        activeTasks.put(LoreEvent.ANIMALS_WATCHING, task);
    }

    private void eventShadowMark(Player player) {
        player.sendTitle("§8ТЕБЯ ЗАМЕТИЛИ", "§5Не оборачивайся", 10, 60, 20);
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0, true, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1, true, false));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 0.9f, 0.35f);
        player.spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 90, 0.7, 1.1, 0.7, 0.01);
    }

    private void eventFakeDeathMessage(Player player) {
        Bukkit.broadcastMessage(ChatColor.GRAY + player.getName() + " выпал из мира");
        Bukkit.getScheduler().runTaskLater(this, () -> player.sendMessage(ChatColor.DARK_PURPLE + "Но ты всё ещё здесь. Значит, сообщение было не для них."), 30L);
    }

    private void eventVoidZone(Player player) {
        addVoidZone(player.getLocation(), getConfig().getInt("void-zones.ambient-radius", 48));
        player.getWorld().spawnParticle(Particle.ASH, player.getLocation(), 250, 8, 2, 8, 0.02);
        broadcastSubtle("§8Один чанк мира стал холоднее.");
    }

    private void eventSink(Player player) {
        Location center = player.getLocation();
        World world = center.getWorld();
        int radius = getConfig().getInt("sink.radius", 5);
        int maxDrop = getConfig().getInt("sink.max-drop", 3);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius || random.nextDouble() > getConfig().getDouble("sink.decay-chance", 0.33)) {
                    continue;
                }
                Block top = world.getHighestBlockAt(center.getBlockX() + x, center.getBlockZ() + z).getRelative(BlockFace.DOWN);
                if (top.getType() != Material.BEDROCK && top.getY() > world.getMinHeight() + 4) {
                    Material material = top.getType();
                    top.setType(Material.AIR, false);
                    top.getRelative(BlockFace.DOWN, 1 + random.nextInt(maxDrop)).setType(material, false);
                }
            }
        }
        crackFloor(center, radius);
        world.playSound(center, Sound.BLOCK_GRAVEL_BREAK, 2.0f, 0.4f);
    }

    private void eventEcho(Player player) {
        Player echo = randomOtherPlayer(player);
        Location location = player.getLocation().add(random.nextInt(13) - 6, 0, random.nextInt(13) - 6);
        String name = echo == null ? player.getName() : echo.getName();
        player.sendMessage(ChatColor.GRAY + "Ты видел " + ChatColor.WHITE + name + ChatColor.GRAY + " одну секунду. Этого игрока там не было.");
        player.getWorld().spawnParticle(Particle.PORTAL, location.add(0, 1, 0), 55, 0.4, 0.8, 0.4, 0.12);
        player.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.55f);
    }

    private void eventCopy(Player player) {
        Location origin = player.getLocation().add(7, 0, 7);
        origin.setY(player.getWorld().getHighestBlockYAt(origin) + 1);
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                origin.clone().add(x, 0, z).getBlock().setType(Material.DARK_OAK_PLANKS);
                if (x == 0 || x == 4 || z == 0 || z == 4) {
                    origin.clone().add(x, 1, z).getBlock().setType(Material.DEEPSLATE_BRICKS);
                    origin.clone().add(x, 2, z).getBlock().setType(Material.DEEPSLATE_BRICKS);
                }
            }
        }
        origin.clone().add(2, 1, 2).getBlock().setType(Material.CHEST);
        Chest chest = (Chest) origin.clone().add(2, 1, 2).getBlock().getState();
        chest.getInventory().addItem(voidBook("Дом, который ты не строил", List.of("Оно сначала скопировало углы.", "Потом выучило твоё имя.", "Не спи здесь.")));
        chest.update();
        Block signBlock = origin.clone().add(2, 1, 0).getBlock();
        signBlock.setType(Material.OAK_SIGN);
        Sign sign = (Sign) signBlock.getState();
        sign.setLine(0, player.getName());
        sign.setLine(1, "уже был");
        sign.setLine(2, "здесь");
        sign.update();
        player.sendMessage(ChatColor.DARK_GRAY + "Ты нашёл место, которое знает твой стиль строительства.");
    }

    private void eventVoidPull(Player player) {
        player.setVelocity(player.getVelocity().add(new Vector(0, -0.9, 0)));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 120, 0, true, false));
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 0.4f);
        player.sendMessage(ChatColor.DARK_PURPLE + "Пустота потянула тебя вниз.");
    }

    private void eventMirrorStep(Player player) {
        Location behind = player.getLocation().subtract(player.getLocation().getDirection().normalize().multiply(2));
        for (int index = 0; index < 4; index++) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                player.playSound(behind, Sound.BLOCK_STONE_STEP, 0.9f, 0.65f);
                player.spawnParticle(Particle.ASH, behind.clone().add(0, 0.1, 0), 8, 0.25, 0.05, 0.25, 0.0);
            }, index * 12L);
        }
        player.sendMessage(ChatColor.DARK_GRAY + "Шаги повторили твой маршрут, но на один шаг позже.");
    }

    private void eventInventoryEcho(Player player) {
        ItemStack note = namedItem(Material.PAPER, "§5Квитанция Пустоты", List.of("§7Предмет найден в твоём инвентаре.", "§7Ты не помнишь, как он туда попал."));
        player.getInventory().addItem(note);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 0.5f);
        player.sendMessage(ChatColor.DARK_PURPLE + "В инвентаре появился чужой след.");
    }

    private void eventLab(Player player) {
        Location origin = player.getLocation().add(12, 0, -8);
        origin.setY(player.getWorld().getHighestBlockYAt(origin) + 1);
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                origin.clone().add(x, 0, z).getBlock().setType(Material.POLISHED_ANDESITE);
                if (x == 0 || x == 6 || z == 0 || z == 6) {
                    origin.clone().add(x, 1, z).getBlock().setType(Material.IRON_BLOCK);
                    origin.clone().add(x, 2, z).getBlock().setType(Material.GRAY_STAINED_GLASS);
                }
            }
        }
        Location portal = origin.clone().add(3, 1, 3);
        portal.getBlock().setType(Material.CRYING_OBSIDIAN);
        origin.clone().add(1, 1, 1).getBlock().setType(Material.BARREL);
        origin.clone().add(5, 1, 5).getBlock().setType(Material.CHEST);
        Chest notesChest = (Chest) origin.clone().add(5, 1, 5).getBlock().getState();
        notesChest.getInventory().addItem(voidBook("Запись лаборатории 03", List.of("НЕ КОПАТЬ НИЖЕ.", "Чёрное пространство за бедроком не пустое.", "Координаты помечены: " + origin.getBlockX() + " " + origin.getBlockY() + " " + origin.getBlockZ())));
        notesChest.update();
        addVoidZone(portal, 32);
        broadcastSubtle("§8Старый лабораторный сигнал мигнул и умер.");
    }

    private void eventWhisper(Player player) {
        List<String> messages = getConfig().getStringList("whispers.messages");
        if (messages.isEmpty()) {
            messages = List.of("§8[шёпот] §5Оно слушает.");
        }
        player.sendMessage(messages.get(random.nextInt(messages.size())));
        if (random.nextBoolean()) {
            player.getInventory().addItem(voidBook("Нечитаемый шёпот", List.of("Буквы двигаются, когда никто не смотрит.", "Избранный не значит спасённый.")));
        }
    }

    private void eventCompassBetrayal(Player player) {
        ItemStack compass = namedItem(Material.COMPASS, "§5Компас, который врёт", List.of("§7Стрелка смотрит не на север.", "§7Она смотрит вниз."));
        player.getInventory().addItem(compass);
        player.setCompassTarget(player.getLocation().clone().subtract(0, 64, 0));
        player.sendMessage(ChatColor.DARK_PURPLE + "Твой компас выбрал направление, которого нет на карте.");
    }

    private void eventNightmare(Player player) {
        player.sendTitle("§0ТЫ УЖЕ СПАЛ", "§5и проснулся не первым", 20, 80, 30);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0, true, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 120, 0, true, false));
        player.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 1.0f, 0.35f);
        player.spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 120, 1, 1, 1, 0.15);
    }

    private void eventBlackRain(World world) {
        stopEvent(LoreEvent.BLACK_RAIN);
        world.setStorm(true);
        world.setThundering(true);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : world.getPlayers()) {
                player.spawnParticle(Particle.ASH, player.getLocation().add(0, 7, 0), 80, 6, 2, 6, 0.03);
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, true, false));
                for (Entity entity : player.getNearbyEntities(18, 6, 18)) {
                    if (entity instanceof Mob mob) {
                        mob.setTarget(player);
                    }
                }
            }
        }, 0L, 20L);
        activeTasks.put(LoreEvent.BLACK_RAIN, task);
        Bukkit.getScheduler().runTaskLater(this, () -> stopEvent(LoreEvent.BLACK_RAIN), 20L * 180);
    }

    private void eventSilence(World world) {
        stopEvent(LoreEvent.SILENCE);
        for (Player player : world.getPlayers()) {
            player.sendTitle("§0", "§8Мир задержал дыхание.", 20, 80, 20);
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20 * 300, 0, true, false));
        }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Player player : world.getPlayers()) {
                player.playSound(player.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 2.0f, 0.25f);
            }
            activeTasks.remove(LoreEvent.SILENCE);
        }, 20L * 300);
        activeTasks.put(LoreEvent.SILENCE, task);
    }

    private void eventRitualMark(Player player) {
        Location center = player.getLocation();
        World world = center.getWorld();
        int[][] ring = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] offset : ring) {
            Block block = world.getHighestBlockAt(center.getBlockX() + offset[0], center.getBlockZ() + offset[1]).getRelative(BlockFace.UP);
            if (block.getType() == Material.AIR) {
                block.setType(random.nextBoolean() ? Material.BLACK_CANDLE : Material.SOUL_TORCH, false);
            }
        }
        player.sendTitle("§5МЕТКА РИТУАЛА", "§8Культ знает твоё место", 10, 70, 20);
        player.playSound(center, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.2f, 0.45f);
    }

    private void eventMobPossession(Player player) {
        Mob chosen = null;
        for (Entity entity : player.getNearbyEntities(20, 8, 20)) {
            if (entity instanceof Mob mob) {
                chosen = mob;
                break;
            }
        }
        if (chosen == null) {
            eventShadowMark(player);
            return;
        }
        chosen.setCustomName("§5Не " + chosen.getType().name().toLowerCase(Locale.ROOT));
        chosen.setCustomNameVisible(true);
        chosen.setTarget(player);
        chosen.getWorld().spawnParticle(Particle.PORTAL, chosen.getLocation().add(0, 1, 0), 120, 0.6, 0.8, 0.6, 0.15);
        player.sendMessage(ChatColor.DARK_PURPLE + "Один моб рядом смотрит только на тебя.");
    }

    private void eventChunkRot(Player player) {
        Location center = player.getLocation();
        World world = center.getWorld();
        int radius = getConfig().getInt("chunk-rot.radius", 8);
        for (int x = -radius; x <= radius; x++) {
            for (int y = -2; y <= 3; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (random.nextDouble() > getConfig().getDouble("chunk-rot.replace-chance", 0.18)) {
                        continue;
                    }
                    Block block = center.clone().add(x, y, z).getBlock();
                    if (block.getType().isSolid() && block.getType() != Material.BEDROCK) {
                        block.setType(random.nextBoolean() ? Material.BLACK_CONCRETE : Material.CRYING_OBSIDIAN, false);
                    }
                }
            }
        }
        addVoidZone(center, 64);
        world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.7f, 0.45f);
    }

    private void eventSkyCrack(World world) {
        stopEvent(LoreEvent.SKY_CRACK);
        skyCrackBar = Bukkit.createBossBar("§5§k||| §dНЕБО РАЗОШЛОСЬ ПО ШВУ §5§k|||", BarColor.PURPLE, BarStyle.SEGMENTED_10);
        for (Player player : world.getPlayers()) {
            skyCrackBar.addPlayer(player);
        }
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            skyCrackBar.setProgress(0.45 + random.nextDouble() * 0.2);
            for (Player player : world.getPlayers()) {
                Location sky = player.getLocation().add(0, 42, 0);
                player.spawnParticle(Particle.DUST, sky, 80, 18, 2, 1, new Particle.DustOptions(Color.PURPLE, 2.3f));
                player.spawnParticle(Particle.PORTAL, sky, 60, 18, 1, 1, 0.02);
            }
        }, 0L, 30L);
        activeTasks.put(LoreEvent.SKY_CRACK, task);
    }

    private void eventGravityFailure(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 50, 1, true, false));
        Bukkit.getScheduler().runTaskLater(this, () -> player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 160, 0, true, false)), 50L);
        player.playSound(player.getLocation(), Sound.ENTITY_SHULKER_SHOOT, 1.1f, 0.45f);
        player.sendMessage(ChatColor.DARK_PURPLE + "Мир на секунду забыл, где низ.");
    }

    private void eventFinalWhisper(Player player) {
        player.sendTitle("§5ПОСЛЕДНИЙ ВЫБОР", "§7Закрыть трещину или открыть её полностью?", 20, 100, 40);
        player.getInventory().addItem(voidBook("Последний шёпот", List.of("Печать требует пустоты внутри мира.", "Энтропия требует мира внутри пустоты.", "Выберите, пока небо ещё держится.")));
        player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.2f, 0.4f);
    }

    private void eventFinalSeal(World world) {
        stopAllEvents();
        setPhase(5);
        for (Player player : world.getPlayers()) {
            player.sendTitle("§fПЕЧАТЬ ЗАВЕРШЕНА", "§7Мир тихий, но пустой.", 20, 120, 60);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.4f, 0.7f);
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0, true, false));
        }
        world.setStorm(false);
        world.setThundering(false);
        voidZones.clear();
        saveRuntimeState();
    }

    private void eventFinalEntropy(World world) {
        stopAllEvents();
        setPhase(5);
        world.setStorm(true);
        world.setThundering(true);
        for (Player player : world.getPlayers()) {
            player.sendTitle("§5МИР НАКОНЕЦ СВОБОДЕН", "§dИскажённая луна. Фиолетовый снег. Печати нет.", 20, 140, 80);
            player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 1.4f, 0.45f);
            player.spawnParticle(Particle.PORTAL, player.getLocation(), 500, 10, 5, 10, 0.2);
        }
        eventSkyCrack(world);
    }

    private void crackFloor(Location center, int radius) {
        World world = center.getWorld();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (random.nextBoolean()) {
                    Block block = world.getHighestBlockAt(center.getBlockX() + x, center.getBlockZ() + z).getRelative(BlockFace.DOWN);
                    if (block.getType().isSolid() && block.getType() != Material.BEDROCK) {
                        block.setType(random.nextBoolean() ? Material.CRACKED_DEEPSLATE_BRICKS : Material.COBBLED_DEEPSLATE, false);
                    }
                }
            }
        }
    }

    private void repairBedrockSeal(Location location) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                location.clone().add(x, 0, z).getBlock().setType(Material.BEDROCK, false);
            }
        }
    }

    private ItemStack voidBook(String title, List<String> pages) {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();
        meta.setTitle(title);
        meta.setAuthor("НЕИЗВЕСТНО");
        meta.setPages(pages);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack namedItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void addVoidZone(Location location, int radius) {
        voidZones.add(new VoidZone(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), radius));
        saveRuntimeState();
    }

    private VoidZone zoneAt(Location location) {
        return voidZones.stream().filter(zone -> zone.contains(location)).findFirst().orElse(null);
    }

    private void loadVoidZones() {
        voidZones.clear();
        ConfigurationSection section = getConfig().getConfigurationSection("runtime.void-zones");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection zoneSection = section.getConfigurationSection(key);
            if (zoneSection != null) {
                voidZones.add(VoidZone.from(zoneSection));
            }
        }
    }

    private void saveRuntimeState() {
        FileConfiguration config = getConfig();
        config.set("lore.phase", phase);
        config.set("runtime.void-zones", null);
        for (int index = 0; index < voidZones.size(); index++) {
            VoidZone zone = voidZones.get(index);
            String path = "runtime.void-zones." + index;
            config.set(path + ".world", zone.worldName());
            config.set(path + ".x", zone.x());
            config.set(path + ".y", zone.y());
            config.set(path + ".z", zone.z());
            config.set(path + ".radius", zone.radius());
        }
        saveConfig();
    }

    private void setPhase(int newPhase) {
        phase = newPhase;
        getConfig().set("lore.phase", phase);
        saveConfig();
        updateDebugViews();
    }

    private void startAutomation() {
        if (automationTask != null) {
            automationTask.cancel();
        }
        if (!getConfig().getBoolean("automation.enabled", true)) {
            nextAutomationAtMillis = 0L;
            return;
        }
        long periodTicks = Math.max(10, getConfig().getLong("automation.tick-period-seconds", 35)) * 20L;
        automationPeriodMillis = periodTicks * 50L;
        nextAutomationAtMillis = System.currentTimeMillis() + automationPeriodMillis;
        automationTask = Bukkit.getScheduler().runTaskTimer(this, this::automationTick, periodTicks, periodTicks);
    }

    private void automationTick() {
        nextAutomationAtMillis = System.currentTimeMillis() + automationPeriodMillis;
        if (Bukkit.getOnlinePlayers().size() < getConfig().getInt("automation.min-online-players", 1)) {
            return;
        }
        double chance = getConfig().getDouble("automation.event-chance-by-phase." + phase, 0.0);
        if (random.nextDouble() > chance) {
            lastAutomationEvent = "проверка без события";
            return;
        }
        Player target = playerOrRandom(Bukkit.getConsoleSender());
        if (target == null) {
            return;
        }
        LoreEvent event = randomEventForPhase();
        triggerEvent(event, target);
    }

    private LoreEvent randomEventForPhase() {
        List<LoreEvent> possible = switch (phase) {
            case 0 -> List.of(LoreEvent.WRONG_SOUNDS, LoreEvent.GHOST_JOIN, LoreEvent.SHADOW_MARK);
            case 1 -> List.of(LoreEvent.GHOST_JOIN, LoreEvent.MISSING_BLOCKS, LoreEvent.WRONG_SOUNDS, LoreEvent.ANIMALS_WATCHING, LoreEvent.SHADOW_MARK, LoreEvent.FAKE_DEATH_MESSAGE);
            case 2 -> List.of(LoreEvent.VOID_ZONE, LoreEvent.SINK, LoreEvent.ECHO, LoreEvent.COPY, LoreEvent.VOID_PULL, LoreEvent.MIRROR_STEP, LoreEvent.INVENTORY_ECHO);
            case 3 -> List.of(LoreEvent.LAB, LoreEvent.WHISPER, LoreEvent.COMPASS_BETRAYAL, LoreEvent.NIGHTMARE, LoreEvent.ECHO, LoreEvent.INVENTORY_ECHO);
            case 4 -> List.of(LoreEvent.BLACK_RAIN, LoreEvent.SILENCE, LoreEvent.RITUAL_MARK, LoreEvent.MOB_POSSESSION, LoreEvent.VOID_PULL, LoreEvent.SHADOW_MARK);
            default -> List.of(LoreEvent.CHUNK_ROT, LoreEvent.SKY_CRACK, LoreEvent.GRAVITY_FAILURE, LoreEvent.FINAL_WHISPER, LoreEvent.WHISPER, LoreEvent.MOB_POSSESSION);
        };
        return possible.get(random.nextInt(possible.size()));
    }

    private void stopEvent(LoreEvent event) {
        BukkitTask task = activeTasks.remove(event);
        if (task != null) {
            task.cancel();
        }
        if (event == LoreEvent.SKY_CRACK && skyCrackBar != null) {
            skyCrackBar.removeAll();
            skyCrackBar = null;
        }
    }

    private void stopAllEvents() {
        for (LoreEvent event : new ArrayList<>(activeTasks.keySet())) {
            stopEvent(event);
        }
        if (skyCrackBar != null) {
            skyCrackBar.removeAll();
            skyCrackBar = null;
        }
    }

    private void startDebugUpdater() {
        debugTask = Bukkit.getScheduler().runTaskTimer(this, this::updateDebugViews, 20L, 20L);
    }

    private void startDebug(Player player) {
        debugViewers.add(player.getUniqueId());
        BossBar bar = debugBars.computeIfAbsent(player.getUniqueId(), ignored -> Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SEGMENTED_10));
        bar.addPlayer(player);
        updateDebugView(player);
    }

    private void stopDebug(Player player) {
        debugViewers.remove(player.getUniqueId());
        BossBar bar = debugBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            player.setScoreboard(manager.getMainScoreboard());
        }
    }

    private void stopDebugForAll() {
        for (UUID uuid : new HashSet<>(debugViewers)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                stopDebug(player);
            }
        }
        for (BossBar bar : debugBars.values()) {
            bar.removeAll();
        }
        debugBars.clear();
        debugViewers.clear();
    }

    private void updateDebugViews() {
        for (UUID uuid : new HashSet<>(debugViewers)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                debugViewers.remove(uuid);
                continue;
            }
            updateDebugView(player);
        }
    }

    private void updateDebugView(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            Scoreboard board = manager.getNewScoreboard();
            Objective objective = board.registerNewObjective(DEBUG_OBJECTIVE, "dummy", "§5§lЛор Пустоты");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            objective.getScore("§7Фаза: §d" + phaseName()).setScore(8);
            objective.getScore("§7След. проверка: §f" + secondsUntilNextAutomation() + "с").setScore(7);
            objective.getScore("§7Шанс: §f" + Math.round(getConfig().getDouble("automation.event-chance-by-phase." + phase, 0.0) * 100) + "%").setScore(6);
            objective.getScore("§7Перелом: §e" + shortText(nextBreakingPoint(), 18)).setScore(5);
            objective.getScore("§7Зон: §f" + voidZones.size()).setScore(4);
            objective.getScore("§7Активно: §f" + activeTasks.size()).setScore(3);
            objective.getScore("§7Последнее:").setScore(2);
            objective.getScore("§d" + shortText(lastAutomationEvent, 24)).setScore(1);
            player.setScoreboard(board);
        }
        BossBar bar = debugBars.computeIfAbsent(player.getUniqueId(), ignored -> Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SEGMENTED_10));
        bar.setTitle("§5Фаза " + phase + ": §d" + phaseName() + " §7| Перелом: §f" + nextBreakingPoint());
        bar.setProgress(Math.max(0.05, Math.min(1.0, (phase + 1) / 6.0)));
        bar.addPlayer(player);
    }

    private long secondsUntilNextAutomation() {
        if (nextAutomationAtMillis <= 0L) {
            return -1L;
        }
        return Math.max(0L, (nextAutomationAtMillis - System.currentTimeMillis() + 999L) / 1000L);
    }

    private String phaseName() {
        return switch (phase) {
            case 0 -> "0 Предсезон: нестабильность";
            case 1 -> "1 Тревога";
            case 2 -> "2 Заражение";
            case 3 -> "3 Открытие";
            case 4 -> "4 Война культа";
            default -> "5 Коллапс";
        };
    }

    private String nextBreakingPoint() {
        return switch (phase) {
            case 0 -> "Разлом";
            case 1 -> "первые Зоны Пустоты";
            case 2 -> "старая Лаборатория";
            case 3 -> "Чёрный дождь";
            case 4 -> "Трещина в небе";
            default -> "финальный выбор";
        };
    }

    private String shortText(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(0, max - 1)) + "…";
    }

    private void sendResourcePack(Player player) {
        String url = getConfig().getString("lore.resource-pack-url", "");
        if (url == null || url.isBlank()) {
            return;
        }
        String sha1 = getConfig().getString("lore.resource-pack-sha1", "");
        player.setResourcePack(url, sha1 == null ? "" : sha1, false);
    }

    private Player playerOrRandom(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        return players.isEmpty() ? null : players.get(random.nextInt(players.size()));
    }

    private Player randomOtherPlayer(Player player) {
        List<Player> players = new ArrayList<>();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                players.add(other);
            }
        }
        return players.isEmpty() ? null : players.get(random.nextInt(players.size()));
    }

    private void broadcastSubtle(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() != GameMode.SPECTATOR) {
                player.sendMessage(message);
            }
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.DARK_PURPLE + "Управление лором TFCSMP:");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " start <ивент> [игрок]");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " stop <ивент|all>");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " phase <0-5>");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " zone [радиус]");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " debug [on|off]");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " status | reload");
    }

    private List<String> filter(String prefix, List<String> values) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }
}
