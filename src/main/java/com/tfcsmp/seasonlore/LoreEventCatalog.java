package com.tfcsmp.seasonlore;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

public final class LoreEventCatalog {
    private static final Random RANDOM = new SecureRandom();

    private LoreEventCatalog() {
    }

    public record WeightedEvent(LoreEvent event, int weight) {
    }

    public record ChildEvent(LoreEvent event, double chance) {
    }

    public static List<WeightedEvent> rootEvents(int phase) {
        return switch (phase) {
            case 0 -> List.of(
                new WeightedEvent(LoreEvent.GHOST_JOIN, 55),
                new WeightedEvent(LoreEvent.WRONG_SOUNDS, 45)
            );
            case 1 -> List.of(
                new WeightedEvent(LoreEvent.GHOST_JOIN, 25),
                new WeightedEvent(LoreEvent.MISSING_BLOCKS, 30),
                new WeightedEvent(LoreEvent.ANIMALS_WATCHING, 25),
                new WeightedEvent(LoreEvent.WRONG_SOUNDS, 20)
            );
            case 2 -> List.of(
                new WeightedEvent(LoreEvent.VOID_ZONE, 35),
                new WeightedEvent(LoreEvent.SINK, 30),
                new WeightedEvent(LoreEvent.COPY, 20),
                new WeightedEvent(LoreEvent.ECHO, 15)
            );
            case 3 -> List.of(
                new WeightedEvent(LoreEvent.LAB, 30),
                new WeightedEvent(LoreEvent.WHISPER, 30),
                new WeightedEvent(LoreEvent.MEMORY_FRAGMENT, 20),
                new WeightedEvent(LoreEvent.COMPASS_BETRAYAL, 20)
            );
            case 4 -> List.of(
                new WeightedEvent(LoreEvent.BLACK_RAIN, 28),
                new WeightedEvent(LoreEvent.SILENCE, 22),
                new WeightedEvent(LoreEvent.FACTION_INVITATION, 25),
                new WeightedEvent(LoreEvent.RITUAL_MARK, 25)
            );
            default -> List.of(
                new WeightedEvent(LoreEvent.CHUNK_ROT, 35),
                new WeightedEvent(LoreEvent.SKY_CRACK, 25),
                new WeightedEvent(LoreEvent.FINAL_WHISPER, 25),
                new WeightedEvent(LoreEvent.GRAVITY_FAILURE, 15)
            );
        };
    }

    public static List<ChildEvent> childEvents(int phase) {
        return switch (phase) {
            case 0 -> List.of(
                new ChildEvent(LoreEvent.SHADOW_MARK, 0.18),
                new ChildEvent(LoreEvent.LOST_MINER_NOTE, 0.10)
            );
            case 1 -> List.of(
                new ChildEvent(LoreEvent.SHADOW_MARK, 0.22),
                new ChildEvent(LoreEvent.FAKE_DEATH_MESSAGE, 0.14),
                new ChildEvent(LoreEvent.LOST_MINER_NOTE, 0.18)
            );
            case 2 -> List.of(
                new ChildEvent(LoreEvent.VOID_PULL, 0.24),
                new ChildEvent(LoreEvent.MIRROR_STEP, 0.22),
                new ChildEvent(LoreEvent.INVENTORY_ECHO, 0.18),
                new ChildEvent(LoreEvent.ECHO, 0.16)
            );
            case 3 -> List.of(
                new ChildEvent(LoreEvent.NIGHTMARE, 0.22),
                new ChildEvent(LoreEvent.MEMORY_FRAGMENT, 0.26),
                new ChildEvent(LoreEvent.INVENTORY_ECHO, 0.16),
                new ChildEvent(LoreEvent.WHISPER, 0.20)
            );
            case 4 -> List.of(
                new ChildEvent(LoreEvent.MOB_POSSESSION, 0.24),
                new ChildEvent(LoreEvent.VOID_PULL, 0.18),
                new ChildEvent(LoreEvent.SHADOW_MARK, 0.20),
                new ChildEvent(LoreEvent.FACTION_INVITATION, 0.12)
            );
            default -> List.of(
                new ChildEvent(LoreEvent.GRAVITY_FAILURE, 0.24),
                new ChildEvent(LoreEvent.WHISPER, 0.18),
                new ChildEvent(LoreEvent.MOB_POSSESSION, 0.16),
                new ChildEvent(LoreEvent.MEMORY_FRAGMENT, 0.12)
            );
        };
    }

    public static LoreEvent chooseRootEvent(int phase) {
        List<WeightedEvent> events = rootEvents(phase);
        int totalWeight = events.stream().mapToInt(WeightedEvent::weight).sum();
        int roll = RANDOM.nextInt(Math.max(1, totalWeight));
        int cursor = 0;
        for (WeightedEvent weightedEvent : events) {
            cursor += weightedEvent.weight();
            if (roll < cursor) {
                return weightedEvent.event();
            }
        }
        return events.getLast().event();
    }

    public static List<LoreEvent> rollChildEvents(int phase) {
        return childEvents(phase).stream()
            .filter(childEvent -> RANDOM.nextDouble() <= childEvent.chance())
            .map(ChildEvent::event)
            .toList();
    }
}
