package com.tfcsmp.seasonlore;

import java.util.Locale;

public enum Faction {
    NONE("Без стороны", "none"),
    SEALERS("Печатники", "sealers"),
    ENTROPY("Энтропия", "entropy"),
    LONERS("Одиночки", "loners");

    private final String displayName;
    private final String id;

    Faction(String displayName, String id) {
        this.displayName = displayName;
        this.id = id;
    }

    public String displayName() {
        return displayName;
    }

    public String id() {
        return id;
    }

    public static Faction parse(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        for (Faction faction : values()) {
            if (faction.id.equals(normalized) || faction.name().toLowerCase(Locale.ROOT).equals(normalized) || faction.displayName.toLowerCase(Locale.ROOT).equals(normalized)) {
                return faction;
            }
        }
        throw new IllegalArgumentException("Неизвестная сторона: " + value);
    }
}
