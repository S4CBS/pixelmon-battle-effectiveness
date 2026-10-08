# ⚡ Pixelmon Battle Effectiveness

<p align="center">
  <img src="https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/items/expert-belt.png" alt="Pixelmon Battle Effectiveness Logo" width="96" height="96"/>
</p>

<p align="center">
  <strong>Client-side Minecraft 1.21.1 NeoForge mod for Pixelmon Reforged that displays move effectiveness and damage multipliers in every battle for every attack.</strong>
</p>

<p align="center">
  <a href="https://github.com/S4CBS/pixelmon-battle-effectiveness/releases"><img src="https://img.shields.io/github/v/release/S4CBS/pixelmon-battle-effectiveness?color=orange&label=Release" alt="Release"/></a>
  <a href="https://neoforged.net/"><img src="https://img.shields.io/badge/NeoForge-1.21.1-blue.svg" alt="NeoForge 1.21.1"/></a>
  <a href="https://reforged.gg/"><img src="https://img.shields.io/badge/Pixelmon-9.4.x-red.svg" alt="Pixelmon Reforged"/></a>
  <a href="https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html"><img src="https://img.shields.io/badge/Java-21-brightgreen.svg" alt="Java 21"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License"/></a>
</p>

---

## 🇷🇺 Описание на русском | Russian Description

### ❓ В чём проблема оригинального Pixelmon?

В коде Pixelmon (`ChooseAttack$MoveButton`) разработчики предусмотрели систему подсказок эффективности атак (иконки со стрелками и надписи над кнопками приёмов), однако в стандартной игре они почти никогда не появляются из-за двух жёстких ограничений:
1. **Требование пойманного покемона в Покедексе (`hasCaught`)** — в оригинале игра проверяет Покедекс игрока. Если дикий или тренерский покемон ещё не был вами пойман, подсказки **полностью скрываются**.
2. **Блокировка в битвах с несколькими противниками (`displayedEnemyPokemon.length <= 1`)** — в двойных битвах (Double Battles) показ отключался из-за упрощённого чтения только первого противника.

Именно поэтому вы могли видеть эту систему **только в битвах с рейд-боссами**, где босс всегда один и зарегистрирован в базе.

---

### 💡 Что делает этот мод?

Мод **Pixelmon Battle Effectiveness** полностью снимает эти ограничения и превращает систему подсказок в полноценного боевого помощника:

- 🎯 **Работает в КАЖДОЙ битве:**
  - Дикие покемоны (Wild Pokemon)
  - NPC-тренеры и Лидеры гимов (Gym Leaders)
  - PvP-битвы между игроками
  - Рейды и Тера-рейды (Raids & Tera Raids)
- ⚔️ **Показывает точную эффективность и множитель урона у КАЖДОЙ атаки:**
  - 🌟 **Сверхэфф. (4x)** (`Hyper Eff. (4x)`) — двойная стрелка вверх
  - ⚡ **Суперэфф. (2x)** (`Super Eff. (2x)`) — звезда / стрелка вверх
  - ⚪ **Эффективно (1x)** (`Effective (1x)`) — стандартный нейтральный урон
  - 🔻 **Не очень эфф. (0.5x)** (`Not Very Eff. (0.5x)`) — стрелка вниз
  - 🔻🔻 **Едва эфф. (0.25x)** (`Barely Eff. (0.25x)`) — двойная стрелка вниз
  - 🚫 **Не действует (0x)** (`Immune (0x)`) — значок невосприимчивости
- 👥 **Умная поддержка Double Battles (2 на 2):**
  - Автоматически рассчитывает эффективность против выбранной или наведённой цели!
  - При нокауте одного из врагов мгновенно переключается на оставшегося живого противника без сбоев интерфейса.
- 💎 **Поддержка Терасталлизации (Tera Type):**
  - Моментально адаптирует расчёт типов, когда босс или противник терасталлизуется.
  - Поддерживает правила Инверсных битв (Inverse Battle).
- ❄️ **Особые приемы:**
  - `Freeze-Dry` (Фриз-Драй): корректно рассчитывается как **2x суперэффективно** против Водного типа.
  - `Thousand Arrows` (Тысяча стрел): попадает по Летающему типу с обычным уроном (1x).
- 🛡️ **Грамотная работа со статусными приёмами:**
  - Статусные атаки (например, *Thunder Wave*, *Toxic*, *Hypnosis*) не вводят в заблуждение ложным «Суперэффективно», но честно предупреждают, если противник иммунен к приёму (*Не действует (0x)* — например, Земляной тип против паралича).
- 🌐 **Полная русификация (`ru_ru`) и англоязычная версия (`en_us`).**

---

## 🇬🇧 English Description

### ❓ What is the issue with default Pixelmon?

In Pixelmon Reforged, the battle move selection UI (`ChooseAttack$MoveButton`) actually contains built-in logic for move effectiveness hints (icons + text). However, it is restricted by two conditions:
1. **Pokédex `hasCaught` check** — effectiveness is only revealed if you have previously caught that species and registered it in your Pokédex (replicating the Gen 7+ mechanic). In wild encounters and trainer battles against uncaught Pokémon, effectiveness is completely blank.
2. **Double Battles lock (`displayedEnemyPokemon.length <= 1`)** — any battle with multiple opponents disables effectiveness hints entirely due to hardcoded single-enemy indexing.

As a result, players usually only ever saw effectiveness hints during **Raid Boss battles**!

### 💡 What does this mod do?

**Pixelmon Battle Effectiveness** unlocks and enhances this feature for **every single battle**:
- Shows effectiveness hints for **all attacks in all battles** (wild, trainer, gym, raid, PvP).
- Displays both the **effectiveness label and the exact damage multiplier** (`4x`, `2x`, `1x`, `0.5x`, `0.25x`, `0x`).
- Full **Double Battle support** with dynamic target awareness.
- Supports **Terastallization (Tera Type)** and **Inverse Battles**.
- Supports signature move matchups (e.g., **Freeze-Dry** super effective against Water, **Thousand Arrows** hitting Flying).
- Clean handling of status moves (warns if the target is immune without showing misleading damage multipliers).

---

## 📊 Сравнение / Comparison

| Ситуация / Encounter | Обычный Pixelmon / Vanilla Pixelmon | С модом Pixelmon Battle Effectiveness |
|---|:---:|:---:|
| Битва с рейд-боссом (Raid Boss) | ✅ Работает (если зарегистрирован) | ✅ **Работает с точным множителем (2x, 4x...)** |
| Дикий покемон (Wild Pokemon) | ❌ Скрыто (если не пойман ранее) | ✅ **Показывает всегда** |
| Битва с NPC-тренером (Trainer) | ❌ Скрыто (если не пойман ранее) | ✅ **Показывает всегда** |
| Лидер стадиона / Гим (Gym Battle) | ❌ Скрыто (если не пойман ранее) | ✅ **Показывает всегда** |
| PvP битва между игроками | ❌ Скрыто (если не пойман ранее) | ✅ **Показывает всегда** |
| Двойные битвы (Double Battles 2v2) | ❌ Полностью отключено | ✅ **Динамический расчёт по цели** |
| Показ точного множителя (4x, 2x, 0.5x...) | ❌ Только текст | ✅ **Текст + точный множитель** |
| Терасталлизация (Tera Type) | ⚠️ Базовый расчёт | ✅ **Полный учёт тера-типа** |
| Особые атаки (Freeze-Dry и др.) | ❌ 0.5x как обычный лёд | ✅ **2x против Водного типа** |

---

## 📥 Установка / Installation

1. Убедитесь, что у вас установлены:
   - **Minecraft 1.21.1**
   - **NeoForge** (версия `21.1.200` или новее)
   - **Pixelmon Reforged** (версия `9.4.0` / `9.4.1` или новее для 1.21.1)
2. Скачайте последний `.jar` файл со страницы [Releases](https://github.com/S4CBS/pixelmon-battle-effectiveness/releases).
3. Поместите скачанный файл `PixelmonEffectiveness-1.21.1-1.0.1.jar` в папку `.minecraft/mods/`.
4. Запустите игру и вступайте в бой!

> 💡 **Клиентский мод**: Мод является исключительно клиентским (Client-Side). Он не требует установки на сервер и прекрасно работает при игре на любых серверах Pixelmon!

---

## ⚙️ Настройки / Configuration

При первом запуске создаётся конфигурационный файл:  
`config/pixelmoneffectiveness-client.toml`

```toml
[general]
    # Включить или отключить подсказки эффективности в битвах
    # Enable or disable battle move effectiveness display
    enabled = true

    # Показывать ли точный множитель урона рядом с надписью (2x, 0.5x и т.д.)
    # Show effectiveness multiplier number next to text (e.g. 2x, 0.5x)
    showMultiplier = true

    # Показывать ли подсказку для нейтральных 1x атак (Эффективно / 1x)
    # Show effectiveness for neutral 1x moves (Effective / 1x)
    showForNormalMoves = true

    # Показывать ли подсказки для статусных приёмов без урона
    # If false, status moves only display when target is immune (0x)
    showForStatusMoves = false
```

---

## 🛠️ Сборка из исходников / Building from Source

```bash
git clone https://github.com/S4CBS/pixelmon-battle-effectiveness.git
cd pixelmon-battle-effectiveness
./gradlew build
```
Собранный файл появится в `build/libs/PixelmonEffectiveness-1.21.1-1.0.1.jar`.

---

## 📝 История версий / Changelog

### v1.0.1
- 🔧 **Исправлена совместимость с Java 21**: сборка скомпилирована с жёстким таргетом Java 21 (`--release 21`, байткод v65), что предотвращает ошибку `UnsupportedClassVersionError` на стандартных версиях Java в лаунчерах Minecraft 1.21.1.
- 📦 Обновлена версия мода до `1.0.1`.

### v1.0.0
- 🎉 Первый релиз мода для Minecraft 1.21.1 NeoForge и Pixelmon Reforged 9.4.x.
- ⚡ Подсказки эффективности и множителей урона в каждой битве для всех атак.
- 👥 Полная поддержка Double Battles, терасталлизации и особых приёмов (Freeze-Dry).

---

## 🏷️ Теги и ключевые слова / Tags & Keywords

`#minecraft` `#pixelmon` `#neoforge` `#minecraft1211` `#pixelmonmod` `#pixelmonreforged`  
`#typeeffectiveness` `#battlehelper` `#pixelmonraid` `#pokemon` `#pixelmonaddon` `#minecraftmod`

### Search Keywords:
- Pixelmon move effectiveness in every battle
- Pixelmon type effectiveness mod 1.21.1 NeoForge
- Pixelmon raid boss effectiveness in normal battles
- Pixelmon battle effectiveness indicator
- Pixelmon Reforged super effective hints
- Как включить подсказки эффективности атак в Pixelmon
- Мод на эффективность атак в каждой битве Пиксельмон 1.21.1

---

## 📄 Лицензия / License

Этот проект распространяется под свободной лицензией **[MIT License](LICENSE)**.
