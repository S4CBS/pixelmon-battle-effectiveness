# ⚡ Pixelmon Battle Effectiveness & Radar Suite

<p align="center">
  <img src="https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/items/expert-belt.png" alt="Pixelmon Battle Effectiveness Logo" width="96" height="96"/>
</p>

<p align="center">
  <strong>Client-side Minecraft 1.21.1 NeoForge mod for Pixelmon Reforged featuring move effectiveness hints, Pokédex-powered Pokémon & PokéLoot Radar with HUD & 3D ESP, Cobblemon-style info overlay, and bulk apricorn harvesting.</strong>
</p>

<p align="center">
  <a href="https://github.com/S4CBS/pixelmon-battle-effectiveness/releases"><img src="https://img.shields.io/github/v/release/S4CBS/pixelmon-battle-effectiveness?color=orange&label=Release" alt="Release"/></a>
  <a href="https://neoforged.net/"><img src="https://img.shields.io/badge/NeoForge-1.21.1-blue.svg" alt="NeoForge 1.21.1"/></a>
  <a href="https://reforged.gg/"><img src="https://img.shields.io/badge/Pixelmon-9.4.x-red.svg" alt="Pixelmon Reforged"/></a>
  <a href="https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html"><img src="https://img.shields.io/badge/Java-21-brightgreen.svg" alt="Java 21"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License"/></a>
</p>

---

## 🇷🇺 Основные возможности / Features

### 1. ⚔️ Боевой помощник (Battle Effectiveness)
- 🎯 **Работает во ВСЕХ битвах:** дикие покемоны, тренеры, гим-лидеры, рейды и PvP.
- ⚡ **Множители урона и подсказки:** `4x` (Сверхэфф.), `2x` (Суперэфф.), `1x` (Нейтрально), `0.5x` (Не очень эфф.), `0.25x` (Едва эфф.), `0x` (Не действует).
- 👥 **Double Battles (2 на 2):** динамический расчёт по выбранной цели.
- 💎 **Терасталлизация (Tera Type)** и Инверсные битвы (Inverse Battles).
- ❄️ **Особые приемы:** `Freeze-Dry` (2x против Воды), `Thousand Arrows` и другие.

### 2. ⚡ Радар покелутов и покемонов (HUD & 3D ESP)
- 🧭 **Экранный HUD (правый верхний угол):**
  - Имя покемона и уровень (или тип сундука).
  - Расстояние в метрах (`m`).
  - Стрелка высоты (`↑` / `↓`).
  - Стрелка точного направления (`▲`, `↗`, `▶`, `↘`, `▼`, `↙`, `◀`, `↖`).
- 👁️ **3D-метки сквозь стены (ESP):** полупрозрачные иконки покеболов и спрайтов покемонов прямо в игровом мире сквозь любые блоки, пещеры и горы.
- ⏳ **Умный учёт перезарядки:** автоматически скрывает залутанные сундуки на кулдауне.
- 🔔 **Звуковые оповещения:** сигнал при обнаружении редких покемонов (Шайни, Легендарных) и редкого лута (Master / Beast).

### 3. 📖 Интерактивный Покедекс для выбора покемонов (Pokédex Picker)
- 🎨 Красивый экран выбора покемонов со **спрайтами, номерами (#0025) и именами (EN и RU)**.
- 🔍 Мгновенный поиск по имени или номеру.
- 🏷️ Вкладки фильтрации по поколениям: **Ген 1 — Ген 9**.
- 🌟 Быстрый выбор редких покемонов кнопкой **«★ Редкие»**.
- 🖱️ Выбор по клику: просто нажмите на покемона, чтобы добавить его в радар.

### 4. 🐾 Инфо-панель покемона (Cobblemon-Style Overlay)
- Показывает подробную карточку покемона при наведении прицела: здоровье в сердечках `❤❤❤❤❤`, пойман/не пойман `✔`, пол, уровень, типы, EV-награду, а при приседании (Shift) — точные IVs (31/31), EVs, характер и способность!
- Клавиша **`I`** открывает меню настройки панели (позиция, масштаб, цвета, прозрачность).

### 5. 🌳 Быстрый сбор априкорнов (Apricorn Harvest)
- При нажатии **Shift + ПКМ** по стволу или листве дерева априкорнов или ягод весь урожай с дерева мгновенно собирается в инвентарь.

---

## ⌨️ Горячие клавиши / Keybindings

| Клавиша / Key | Действие / Action |
|:---:|---|
| **`K`** | Открыть настройки Радара и **Покедекс для выбора покемонов** |
| **`O`** | Быстрое включение / выключение Радара |
| **`Shift + O`** | Открыть настройки Радара |
| **`I`** | Открыть настройки инфо-панели покемона (Overlay) |

---

## 📥 Установка / Installation

1. Убедитесь, что у вас установлены:
   - **Minecraft 1.21.1**
   - **NeoForge** (версия `21.1.200` или новее)
   - **Pixelmon Reforged** (версия `9.4.0` / `9.4.1` или новее для 1.21.1)
2. Скачайте файл **`PixelmonEffectiveness-1.21.1-1.0.7.jar`** со страницы [Releases](https://github.com/S4CBS/pixelmon-battle-effectiveness/releases).
3. Поместите файл в папку `.minecraft/mods/`.
4. Запустите игру!

> 💡 **Client-Side**: Мод работает на стороне клиента и поддерживается на любых серверах Pixelmon.

---

## 📝 История версий / Changelog

### v1.0.7
- 📖 **Интерактивный Покедекс (Pokédex Picker):** новый графический интерфейс со спрайтами покемонов, номерами (#0025), именами (RU/EN) и фильтрами поколений (Gen 1-9) для мгновенного выбора целей радара.
- ⚡ **Радар покемонов:** отслеживание диких покемонов в HUD-радаре со стрелками направления, расстоянием в метрах и 3D-метками сквозь блоки.
- 🔍 **Поиск по названию:** поддержка поиска на русском и английском языках, а также списков через запятую.
- 👁️ **Сквозная видимость 3D-меток:** покелуты и покемоны отображаются через блоки в полупрозрачном режиме (`RenderType.textSeeThrough`).
- 🗺️ **Совместимость с JourneyMap:** автоматическая передача спрайтов покемонов на радар миникарты.

### v1.0.1
- 🔧 **Исправлена совместимость с Java 21**: сборка скомпилирована с жёстким таргетом Java 21 (`--release 21`, байткод v65).

### v1.0.0
- 🎉 Первый релиз мода для Minecraft 1.21.1 NeoForge и Pixelmon Reforged 9.4.x.

---

## 📄 Лицензия / License

Этот проект распространяется под свободной лицензией **[MIT License](LICENSE)**.
