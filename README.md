# TFCSMP 3rd Season Lore Plugin

Paper-плагин для сервера **1.21.11**, который ведёт русскоязычный slow-burn лор сезона: нестабильный мир, Разлом, заражение, лаборатории, шёпоты, война культа, коллапс мира и две финальные концовки.

## Сборка

```bash
gradle build --no-daemon
```

Готовый jar: `build/libs/TFCSMP-3dSeasonLore-1.0.0.jar`.

## Установка

1. Соберите jar.
2. Скопируйте jar в папку Paper-сервера `plugins/`.
3. Один раз запустите сервер, чтобы появился `plugins/TFCSMPSeasonLore/config.yml`.
4. Настройте шанс автоматики, текущую фазу и опциональный resource pack.
5. Перезапустите сервер или выполните `/function reload`.

## Команды

Permission: `tfcsmp.lore.admin` (по умолчанию op).

- `/function start <event> [player]` — запустить событие вручную.
- `/function stop <event|all>` — остановить долгие события.
- `/function phase <0-5>` — посмотреть или поставить фазу лора.
- `/function zone [radius]` — создать Зону Пустоты на текущей позиции.
- `/function debug [on|off]` — включить/выключить debug-sidebar и bossbar стадии с таймером до следующей проверки автоматики.
- `/function status` — вывести фазу, следующий перелом, таймер, зоны и активные события.
- `/function reload` — перезагрузить конфиг.

Также есть aliases: `/voidlore` и `/tfclore`, если `/function` занят другим плагином или datapack-командами.

## Event IDs

`breach`, `ghost_join`, `missing_blocks`, `wrong_sounds`, `animals_watching`, `shadow_mark`, `fake_death_message`, `void_zone`, `sink`, `echo`, `copy`, `void_pull`, `mirror_step`, `inventory_echo`, `lab`, `whisper`, `compass_betrayal`, `nightmare`, `black_rain`, `silence`, `ritual_mark`, `mob_possession`, `chunk_rot`, `sky_crack`, `gravity_failure`, `final_whisper`, `final_seal`, `final_entropy`.

## Документация по стадиям

Смотри `docs/lore-events-and-stages.txt`: там перечислено, что делает каждая стадия, какие события доступны автоматике и какие события напрямую таргетят игрока.

## Resource pack

Смотри `docs/resource-pack-plan.txt` для точных имён файлов resource pack: искажённая луна, фиолетовый снег, OGG-звуки, font glyphs, модели артефактов и hosting/SHA-1 checklist.

## Идеи и задачи

ВСЕ ИДЕИ ПИШЕМ В ФАЙЛЕ `additional/IDEA-номер` с соответствующим номером и дублируем в чате с `#IDEA`

ВСЕ БУДУЩИЕ ЗАДАЧИ ПИШЕМ В ФАЙЛЕ `additional/TODO-номер` с соответствующим номером и дублируем в чате с `#TODO`

### Описываем идеи и задачи максимально подробно!!!
