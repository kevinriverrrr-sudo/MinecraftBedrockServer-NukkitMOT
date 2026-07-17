# Minecraft Bedrock Server — Nukkit-MOT

Полная сборка Minecraft Bedrock сервера на ядре **Nukkit-MOT** с 7 кастомными плагинами и **GeyserMC** для кроссплатформенности.

## 🎮 Плагины

| Плагин | Описание |
|--------|----------|
| **CustomPerms** | Самописная система разрешений (аналог LuckPerms). Группы, наследование, wildcard-пермиссоны, отрицание, временные группы |
| **PvPSystem** | Комбо-система, килл-стрики, ELO-рейтинг, PvP-арены (1v1/2v2), кастомные сообщения о смертях |
| **CustomChat** | 4 канала (global, local, staff, trade), фильтр мата, анти-реклама, анти-спам, ЛС, ники |
| **KitSystem** | 5 китов, кулдауны, кастомные предметы, создание китов из инвентаря |
| **DonationSystem** | 4 ранга (VIP→Legend), перки (/fly, /heal, /feed, /repair, /hat, /workbench, /ec, /back), донат-коды |
| **CustomDungeons** | 3 данжа (Undead Crypt, Fire Temple, Void Fortress), боссы с фазами, лут, пати-система |
| **ServerAesthetics** | Скорборд, анимированный таб, эффекты входа, фейерверки, авто-бродкаст |

## 🚀 Запуск

```bash
java -Dterminal.jline=false -Dterminal.ansi=true -Dlog4j.configurationFile=./log4j2.xml -Xms512m -Xmx1024m -jar build/Nukkit-MOT-SNAPSHOT.jar
```

С GeyserMC (для Java Edition игроков):
```bash
java -Dterminal.jline=false -Dterminal.ansi=true -Dlog4j.configurationFile=./log4j2.xml -Xms512m -Xmx1024m -jar build/Nukkit-MOT-SNAPSHOT.jar & sleep 10 && cd geyser && java -Xms256m -Xmx512m -jar Geyser-Standalone.jar &
```

## 📡 Подключение

- **Bedrock** (PE, Xbox, PS, Switch) → IP: порт `19132`
- **Java Edition** → IP: порт `19133` (через GeyserMC)

## 📁 Структура

```
├── nukkit-mot-core/       # Исходный код ядра Nukkit-MOT
├── plugins/               # Исходный код всех 7 плагинов
│   ├── CustomPerms/
│   ├── PvPSystem/
│   ├── CustomChat/
│   ├── KitSystem/
│   ├── DonationSystem/
│   ├── CustomDungeons/
│   └── ServerAesthetics/
├── build/                 # Скомпилированные JAR файлы
├── geyser/                # GeyserMC прокси
├── server.properties      # Настройки сервера
├── log4j2.xml             # Конфигурация логгирования
├── start.sh               # Скрипт запуска
└── stop.sh                # Скрипт остановки
```

## ⚙️ Команды

### CustomPerms
```
/perms group create|delete|list|info|addperm|delperm|setprefix|setsuffix|setpriority|setdefault|parent
/perms user <player> info|addperm|delperm|setgroup|addgroup|removegroup
/perms reload
```

### PvPSystem
```
/pvp arena create|delete|list|setspawn
/pvp join|leave|spectate
/pvpstats [player]
/pvpleaderboard <kills|kd|elo>
```

### CustomChat
```
/channel <global|local|staff|trade>
/msg <player> <message>
/reply <message>
/ignore <player>
/nick <set|reset> [name]
```

### KitSystem
```
/kit list|preview|<name>
/kitadmin create|delete|setcooldown|setpermission|give
/customitem give|list
```

### DonationSystem
```
/donate ranks|info|activate <code>
/donategive <player> <rank> [duration]
/donategencode <rank> [amount] [duration]
/fly|heal|feed|repair|hat|workbench|enderchest|back
```

### CustomDungeons
```
/dungeon list|enter|leave|info
/dungeon party create|invite|accept|leave|list
/dungeon leaderboard
```

### ServerAesthetics
```
/spawn|setspawn|serverinfo|scoreboard|broadcast
```
