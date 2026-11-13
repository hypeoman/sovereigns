# Заметки о внутренностях модов сборки

Что узнали о чужих модах: классы, API, подводные камни. Пополняется по ходу работы.

## Окружение

- Dev-экземпляр: `C:/Users/Professional/curseforge/minecraft/Instances/State of War DEV`, NeoForge 21.1.252,
  Minecraft 1.21.1, 173 jar в `mods/`.
- JDK: портативный Temurin 21 в `~/.jdks/jdk-21.0.13+9`; Gradle-демон берёт его из `org.gradle.java.home`
  в `~/.gradle/gradle.properties`. Для запуска `gradlew` нужен `JAVA_HOME` на тот же путь.

## War 'N Taxes 5.0.9 (`net.machiavelli.minecolonytax`)

- Свой вассалитет игроков: `VassalRelation`, `VassalProposal`, страница книги `gui/book/VassalsPage`,
  дань `network/packets/ClaimVassalTributePayload`, доход `gui/data/VassalIncomeData`.
- Свои фракции: `faction/FactionManager`, `FactionData`, `FactionRelation`; мост к Easy Factions `compat/EasyFactionsBridge`.

## War 'n Nobility 1.1.1 (`com.newtl.warnnobility`)

- Требует MineColonies и War 'N Taxes; Easy Factions и Hundred Years War опциональны.
- Миксин в страницу вассалов WnT: `mixin/VassalsPageMixin`; статус вассала на клиенте `client/ClientVassalStatus`.

## MineColonies 1.1.1368 — колония без живого владельца (этап 0.1)

Изучено по декомпиляции jar из сборки (Vineflower из кэша Gradle). `com.minecolonies.api.*` — API,
`com.minecolonies.core.*` — реализация.

### Лицензия
- Код — **GPL-3.0** (`license="GPL3"` в `neoforge.mods.toml`); ассеты — All Rights Reserved (`assets/minecolonies/LICENSE`).
- Мы только вызываем API (`compileOnly`), код не копируем. Если начнём копировать код MineColonies (или форкать) —
  наш мод тоже придётся выпускать под GPL-3.0. Ассеты и схемы MineColonies не копируем.

### Владелец колонии — это просто UUID + имя
- `core.colony.permissions.Permissions` хранит владельца как `ownerUUID` + `ownerName` и запись с рангом 0 в `players`.
  Живой игрок не нужен.
- В NBT пишутся `id`, `name`, `rank` каждого игрока. При загрузке, если UUID нет в profile cache сервера,
  берётся сохранённое `name` → **фиктивный владелец переживает перезагрузку**.
- `setOwner(Player)` — единственный способ задать `ownerUUID/ownerName` (поля приватные). Подходит NeoForge
  `FakePlayer`: `FakePlayerFactory.get(serverLevel, new GameProfile(uuid, name))`.
- `ColonyPackageManager.addImportantColonyPlayer/addCloseSubscriber` **явно игнорируют `FakePlayer`** —
  фиктивный владелец не держит колонию активной, пакеты ему не шлются.
- `addPlayer(UUID, String name, Rank)` — добавить игрока с любым рангом без живого игрока (офицеры, враги).
- Авто-удаления заброшенных колоний в этой версии нет: `getColoniesAbandonedSince` нигде не вызывается.

### Рецепт создания колонии (для 0.2)
1. `FakePlayer ruler = FakePlayerFactory.get(level, new GameProfile(uuid, rulerName))`.
2. `IColony c = IColonyManager.getInstance().createColony(level, townHallPos, ruler, colonyName, stylePack)` —
   внутри: `IServerColonySaveData.createColony`, `setStructurePack`, `setName`, `permissions.setOwner(ruler)`,
   подписки (для FakePlayer пропускаются), `ChunkDataHelper.claimColonyChunks`.
   Проверки «близко к спавну / к другой колонии / одна колония на владельца» есть только в `CreateColonyMessage`
   (GUI) — **через API их нет, расстояния проверяем сами** (`IColonyManager.isFarEnoughFromColonies`).
3. Ратуша: поставить блок `ModBlocks.blockHutTownHall`, затем
   `((AbstractBlockHut) block).onBlockPlacedByBuildTool(level, pos, state, null, null, rotMir, pack, blueprintPath)` —
   `placer` может быть `null`; внутри `setPlacedBy` → `colony.getServerBuildingManager().addNewBuilding(hut, level)`.
4. Отправить `ColonyCreatedModEvent` в `IMinecoloniesAPI.getInstance().getEventBus()` — его слушают аддоны
   (GUI-путь его постит, API-путь — нет).

### Хижины (для 0.3)
- Путь build tool в креативе — `AbstractBlockHut.setup(...)`: якорный блок → `onBlockPlacedByBuildTool` →
  `building.setStructurePack/setBlueprintPath/setBuildingLevel(N)/setRotationMirror` →
  `building.onUpgradeComplete(blueprint, level)`. Уровень берётся из имени схемы (`...N.blueprint`).
- Мгновенная постройка: `CreativeBuildingStructureHandler.loadAndPlaceStructureWithRotation(level, futureBlueprint,
  pos, rotMir, fancy, null)` — `player` nullable; ставит в очередь Structurize `Manager` (растянуто по тикам),
  хендлер сам регистрирует блоки здания (`registerBlockPosition`).
- Схемы: `StructurePacks.getStructurePack(pack)` (Structurize).

### Жители и стража (для 0.4)
- `CitizenManager.onColonyTick` сам спавнит жителей, если есть ратуша, включена настройка `BuildingTownHall.MOVE_IN`
  и есть места. Принудительно: `colony.getCitizenManager().spawnOrCreateCivilian(null, level, list, true)`
  (так делает `/mc citizens spawnNew`), затем `CitizenAddedModEvent`.
- Найм — через `WorkerBuildingModule` хижины (авто-найм по умолчанию) — проверить в 0.4.
- Колония тикает только в `ACTIVE`: игрок в её чанках (close subscribers) или онлайн владелец/офицер при >40
  загруженных чанках. Иначе `UNLOADED`/`INACTIVE` — вдали от игрока правда в данных, как и задумано.

### Подвох: «давность контакта» отключает бои
- `ColonyPackageManager.updateAwayTime`: пока нет онлайн-владельца/офицеров, `lastContactInHours` растёт раз в час.
- При `lastContactInHours > 1` `Colony.isValidAttackingPlayer` и `isValidAttackingGuard` дают `false` —
  PvP-атака по колонии (`pvp_mode`) и бои стражи колония-против-колонии **выключаются**. У НПС-колонии так будет
  всегда. Лечение без рефлексии: `colony.getPackageManager().setLastContactInHours(0)` (публичный API)
  для воюющих НПС-колоний.
- Враждебность стражи между колониями (`core.colony.pvp.AttackingPlayer.isValidAttack`): стража A бьёт жителей B,
  если владелец A имеет ранг `Hostile` в B (или наоборот). С фиктивными UUID работает через
  `permissions.addPlayer(otherRulerUuid, name, getRankHostile())` — готовый механизм войн НПС↔НПС и НПС↔игрок.
- Игрок с рангом `Hostile` атакуется стражей независимо от давности контакта.

### Смена владельца (для 0.5)
- Команда MineColonies `ownerchange` = `permissions.setOwner(player)`. Старый владелец просто удаляется из `players`
  (становится нейтралом). Игрок → правитель: `setOwner(fakePlayer)` + `setPlayerRank(playerUuid, rank, level)`
  при желании. Правитель → игрок: `setOwner(serverPlayer)`.
- После смены — `colony.getPackageManager().sendPermissionsPackets()`, иначе клиент покажет старые права.

### Прочее
- `IColonyManager.getIColonyByOwner(level, uuid)` возвращает **первую** колонию владельца: MineColonies рассчитан
  на «один владелец — одна колония».
- Colony Autopilot в сборке **нет** — решения мэра НПС-колоний будут своим ИИ (этап 2).

## План 0.2–0.5 и риски

- **0.2** — `/realmdebug spawncolony [имя] [стиль]`: выровнять площадку, `FakePlayer` с детерминированным UUID
  (`UUID.nameUUIDFromBytes("sovereigns:ruler:<id>")`), `createColony`, ратуша, `ColonyCreatedModEvent`.
  Код — `dev.sovereigns.world`, единственное место, где трогаем MineColonies.
- **0.3** — хижины списком `(тип, уровень, смещение)`, схема по стилю, мгновенная постановка через
  `CreativeBuildingStructureHandler`, уровни через `setBuildingLevel + onUpgradeComplete`.
- **0.4** — `spawnOrCreateCivilian(force=true)` до числа мест, проверить авто-найм; стража из бараков/башен.
- **0.5** — `/realmdebug transfer <колония> <ruler|player>` через `setOwner` + `sendPermissionsPackets`.

Риски и запасные варианты:
1. **Аддоны ждут живого владельца** (War 'N Taxes, Colonies at War ищут `ServerPlayer` по UUID владельца →
   `null`/NPE). Проверка на 0.6. Запасной: адаптер не регистрирует НПС-колонии в аддоне.
2. **Одна колония на владельца.** Рекомендация: UUID на провинцию (`sovereigns:ruler:<state>:<province>`),
   принадлежность государству — в наших данных. Ничего не стоит, снимает конфликт `getIColonyByOwner`.
3. **Бои отключены давностью контакта** — лечится `setLastContactInHours(0)`.
4. **В API нет проверок расстояний** — следим сами, иначе пересекающиеся клеймы.
5. **Мгновенная постройка тяжёлая** — очередь Structurize растягивает по тикам, но фриз на 5–10 хижинах проверить.
6. Классы `core.*` используем только через интерфейсы `api.*`; если понадобится `core.*` — пометка `// FRAGILE`.

**Вывод: путь без живого владельца виден, форк и свой физический слой не нужны.**
