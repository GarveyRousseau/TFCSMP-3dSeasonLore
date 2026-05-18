package com.tfcsmp.seasonlore;

import java.util.List;

public final class LoreTexts {
    private LoreTexts() {
    }

    public record BookText(String title, List<String> pages) {
    }

    public static final BookText COPY_HOUSE = new BookText(
        "Дом, который ты не строил",
        List.of(
            "Оно сначала скопировало углы. Потом выучило твоё имя. Не спи здесь.",
            "Если дом похож на твой, проверь: двери открываются внутрь или вниз?"
        )
    );

    public static final BookText LAB_NOTE = new BookText(
        "Запись лаборатории 03",
        List.of(
            "НЕ КОПАТЬ НИЖЕ. Чёрное пространство за бедроком не пустое. Оно реагирует на звук кирки.",
            "Мы пытались назвать это пустотой, но пустота не отвечает. Это ответило.",
            "Карта заражения неполная. Зоны двигаются после грозы."
        )
    );

    public static final BookText WHISPER = new BookText(
        "Нечитаемый шёпот",
        List.of(
            "Буквы двигаются, когда никто не смотрит. Избранный не значит спасённый.",
            "Твоя тень стала длиннее, чем должна быть. Она уже знает дорогу вниз."
        )
    );

    public static final BookText LOST_MINER_NOTE = new BookText(
        "Записка шахтёра",
        List.of(
            "Я слышал шаги под бедроком. Не сверху. Не рядом. Под ним.",
            "Если найдёте мой тоннель, засыпьте его. Если он уже засыпан — не открывайте снова."
        )
    );

    public static final BookText MEMORY_FRAGMENT = new BookText(
        "Фрагмент памяти",
        List.of(
            "Ты уже был в этой комнате. Даже если построил её вчера.",
            "Воспоминание не твоё, но оно помнит твоё имя."
        )
    );

    public static final BookText THREE_PATHS = new BookText(
        "Три пути",
        List.of(
            "Печатники закрывают трещины. Энтропия открывает правду. Одиночки не служат никому.",
            "Выбор даст силу и цену. Отказ даст тишину. Тишина тоже выбирает за тебя.",
            "Команды: /function faction sealers, /function faction entropy, /function faction loners, /function faction none"
        )
    );

    public static final BookText SEALER_OATH = new BookText(
        "Клятва Печатника",
        List.of(
            "Мы закрываем то, что не должно было услышать нас.",
            "Плюс: устойчивость рядом с заражением. Цена: Пустота тяжелее давит в глубине."
        )
    );

    public static final BookText ENTROPY_SERMON = new BookText(
        "Проповедь Энтропии",
        List.of(
            "Мир не ломается. Он перестаёт притворяться клеткой.",
            "Плюс: сила внутри заражения. Цена: поверхность отвергает тебя при чистом небе."
        )
    );

    public static final BookText LONER_MANIFESTO = new BookText(
        "Манифест Одиночки",
        List.of(
            "Стаи спорят о печатях и свободе. Одинокий слышит, где трещина дышит.",
            "Плюс: скорость, когда рядом нет игроков. Цена: слабость и медлительность в толпе."
        )
    );

    public static final BookText FINAL_WHISPER = new BookText(
        "Последний шёпот",
        List.of(
            "Печать требует пустоты внутри мира. Энтропия требует мира внутри пустоты.",
            "Одиночка не обязан голосовать, но финал всё равно найдёт его след.",
            "Выберите, пока небо ещё держится."
        )
    );
}
