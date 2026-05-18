package com.tfcsmp.seasonlore;

import java.util.Locale;

public enum LoreEvent {
    BREACH(1, "Разлом"),
    GHOST_JOIN(1, "Вход Неизвестного"),
    MISSING_BLOCKS(1, "Пропажа Блоков"),
    WRONG_SOUNDS(1, "Неверные Звуки"),
    ANIMALS_WATCHING(1, "Животные Смотрят"),
    SHADOW_MARK(1, "Метка Тени"),
    FAKE_DEATH_MESSAGE(1, "Ложная Смерть"),
    LOST_MINER_NOTE(1, "Записка Шахтёра"),
    VOID_ZONE(2, "Зона Пустоты"),
    SINK(2, "Проседание"),
    ECHO(2, "Эхо"),
    COPY(2, "Копия"),
    VOID_PULL(2, "Тяга Пустоты"),
    MIRROR_STEP(2, "Чужие Шаги"),
    INVENTORY_ECHO(2, "Эхо Инвентаря"),
    LAB(3, "Лаборатория"),
    WHISPER(3, "Шёпот"),
    MEMORY_FRAGMENT(3, "Фрагмент Памяти"),
    COMPASS_BETRAYAL(3, "Предательский Компас"),
    NIGHTMARE(3, "Кошмар"),
    BLACK_RAIN(4, "Чёрный Дождь"),
    SILENCE(4, "Тишина"),
    FACTION_INVITATION(4, "Призыв Сторон"),
    RITUAL_MARK(4, "Ритуальная Метка"),
    MOB_POSSESSION(4, "Одержимость Моба"),
    CHUNK_ROT(5, "Гниение Чанка"),
    SKY_CRACK(5, "Трещина В Небе"),
    GRAVITY_FAILURE(5, "Сбой Гравитации"),
    FINAL_WHISPER(5, "Последний Шёпот"),
    FINAL_SEAL(5, "Концовка Печати"),
    FINAL_ENTROPY(5, "Концовка Энтропии");

    private final int phase;
    private final String displayName;

    LoreEvent(int phase, String displayName) {
        this.phase = phase;
        this.displayName = displayName;
    }

    public int phase() {
        return phase;
    }

    public String displayName() {
        return displayName;
    }

    public static LoreEvent parse(String value) {
        String normalized = value.toUpperCase(Locale.ROOT).replace('-', '_');
        for (LoreEvent event : values()) {
            String russianName = event.displayName.toUpperCase(Locale.ROOT).replace(' ', '_');
            if (event.name().equals(normalized) || russianName.equals(normalized)) {
                return event;
            }
        }
        throw new IllegalArgumentException("Неизвестное событие: " + value);
    }
}
