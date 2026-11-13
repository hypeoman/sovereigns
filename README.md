# Sovereigns for MineColonies

Слой большой стратегии поверх MineColonies для одиночной игры на сборке State of War (NeoForge 1.21.1):
НПС-государства, дипломатия, войны, аннексия и вассалитет.

- Механики — `docs/DESIGN.md`, план — `docs/ROADMAP.md`, решения — `docs/DECISIONS.md`.
- Настройка окружения — `docs/SETUP.md`.

## Сборка

```
./gradlew build              # сборка и тесты
./gradlew runServer          # сервер с модами сборки (syncInstanceMods копирует их в run/mods)
./gradlew deployToInstance   # jar в mods/ dev-экземпляра
./gradlew inventoryInstance  # список модов сборки -> docs/PACK.md
```

Нужны JDK 21 (`JAVA_HOME`) и `sovereigns.instanceDir` в `~/.gradle/gradle.properties`.
