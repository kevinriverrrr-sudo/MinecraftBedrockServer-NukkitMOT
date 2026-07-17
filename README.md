# Minecraft Bedrock Server — Nukkit-MOT

Полноценный Minecraft Bedrock сервер на ядре Nukkit-MOT с 7 кастомными плагинами и GeyserMC для кроссплатформы.

## 🚀 Быстрый старт

### Требования
- **Java 17+** (скрипт `start.sh` автоматически скачает и установит JDK 17, если Java не найдена)
- **ОС:** Linux (Ubuntu/Debian/CentOS)
- **RAM:** минимум 1GB (рекомендуется 2GB+)
- **Порты:** 19132 (Bedrock), 19133 (Java Edition через GeyserMC)

### Запуск

```bash
chmod +x start.sh
./start.sh
```

Скрипт автоматически:
1. Проверит наличие Java 17+ в системе
2. Если Java не найдена — скачает и установит Adoptium JDK 17
3. Запустит Nukkit-MOT сервер на порту 19132
4. Запустит GeyserMC прокси на порту 19133

### Запуск без GeyserMC (только Bedrock)

```bash
java -Dterminal.jline=false -Dterminal.ansi=true \
    -Dlog4j.configurationFile=./log4j2.xml \
    -Xms512m -Xmx1024m \
    -jar Nukkit-MOT.jar
```

## 🎮 Для хостингов (Wispbyte / Pterodactyl)

Если используешь панель управления хостингом, где нет Java в контейнере:

**Вариант 1:** Используй `entrypoint.sh` как команду запуска в панели:
```bash
bash entrypoint.sh
```
Он автоматически установит Java 17 и запустит сервер.

**Вариант 2:** Вручную укажи путь к Java в команде запуска:
```bash
$HOME/java/jdk-17*/bin/java -Dterminal.jline=false -Dterminal.ansi=true -Dlog4j.configurationFile=./log4j2.xml -Xms512m -Xmx1024m -jar Nukkit-MOT.jar
```

**Вариант 3:** Выбери Docker-образ с Java 17 в панели хостинга (например `eclipse-temurin:17-jdk`).

## 🔌 Плагины (7 штук, написаны с нуля)

| Плагин | Описание | Команды |
|--------|----------|---------|
| **CustomPerms** | Система прав и групп (аналог LuckPerms) | `/perms`, `/group` |
| **PvPSystem** | PvP арены, ELO рейтинг, киллстрики, комбат-тег | `/arena`, `/pvpstats` |
| **CustomChat** | Кастомный чат, каналы, фильтр, анти-спам, ЛС, ники | `/chat`, `/nick`, `/msg` |
| **KitSystem** | Киты предметов, кастомные предметы, кулдауны | `/kit`, `/kitadmin`, `/customitem` |
| **DonationSystem** | Донат-ранги, коды активации, перки, авто-проверка | `/donate`, `/perk`, `/donateadmin` |
| **CustomDungeons** | Подземелья с волнами, боссами, лутом, пати | `/dungeon`, `/party` |
| **ServerAesthetics** | Скорборд, TAB-лист, эффекты входа, спавн, автобродкаст | `/spawn`, `/setspawn`, `/scoreboard`, `/serverinfo` |

## 📁 Структура

```
.
├── Nukkit-MOT.jar          # Ядро сервера
├── start.sh                # Скрипт запуска (с автоустановкой Java)
├── entrypoint.sh           # Entrypoint для хостинг-панелей
├── stop.sh                 # Скрипт остановки
├── log4j2.xml              # Конфигурация логов
├── server.properties       # Настройки сервера
├── plugins/                # Скомпилированные плагины (.jar)
├── geyser/                 # GeyserMC для Java Edition
│   ├── Geyser-Standalone.jar
│   └── config/geyser-config.yml
├── build/                  # Скомпилированные JAR-файлы
├── nukkit-mot-core/        # Исходный код ядра Nukkit-MOT
└── plugins-source/         # Исходный код плагинов
```

## 📥 Скачать отдельные части

Все файлы разделены на отдельные архивы для удобной загрузки на хостинг:

| Архив | Описание | Ссылка |
|-------|----------|--------|
| Server-Core.zip | Ядро + скрипты запуска | [VikingFile](https://vikingfile.com/f/ESAI6hxmrV) |
| GeyserMC.zip | GeyserMC прокси + конфиг | [VikingFile](https://vikingfile.com/f/al5xyEd63u) |
| Plugins-Compiled.zip | 7 скомпилированных плагинов | [VikingFile](https://vikingfile.com/f/Oq9Hj8iYO7) |
| Plugins-Configs.zip | Конфиги плагинов | [VikingFile](https://vikingfile.com/f/SaAod1UHDO) |
| Plugins-Source.zip | Исходники плагинов | [VikingFile](https://vikingfile.com/f/I6MukUBdLA) |
| Nukkit-MOT-Source.zip | Исходники ядра | [VikingFile](https://vikingfile.com/f/WjKifbNjIZ) |

## 🔗 Подключение

- **Bedrock Edition:** IP:порт `your-server-ip:19132`
- **Java Edition:** (через GeyserMC) IP:порт `your-server-ip:19133`

## ⚠️ Устранение проблем

### `java: command not found`
Скрипт `start.sh` автоматически скачает Java 17. Если на хостинге ограничен доступ:
1. Используй `entrypoint.sh` как команду запуска
2. Или выбери Docker-образ с Java в панели хостинга

### Плагины не загружаются
Убедись что все `.jar` файлы лежат в папке `plugins/` рядом с `Nukkit-MOT.jar`.

### GeyserMC не запускается
Проверь что папка `geyser/` содержит `Geyser-Standalone.jar` и `config/geyser-config.yml`.
