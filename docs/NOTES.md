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
   Проверено на 0.2: `createColony` с FakePlayer пишет два WARN «Adding fakeplayer as important subscriber: this
   should not happen» со стектрейсом — это MineColonies сам отбрасывает FakePlayer, безвредно. Чтобы убрать шум,
   пришлось бы повторить `createColony` вручную через `core.ChunkDataHelper` — не стоит того.
   Консольная команда MineColonies для проверки: `minecolonies colony info <id>` / `minecolonies colony list`.
4. Отправить `ColonyCreatedModEvent` в `IMinecoloniesAPI.getInstance().getEventBus()` — его слушают аддоны
   (GUI-путь его постит, API-путь — нет).

### Хижины (для 0.3)
- Путь build tool в креативе — `AbstractBlockHut.setup(...)`: якорный блок → `onBlockPlacedByBuildTool` →
  `building.setStructurePack/setBlueprintPath/setBuildingLevel(N)/setRotationMirror` →
  `building.onUpgradeComplete(blueprint, level)`. Уровень берётся из имени схемы (`...N.blueprint`).
- Мгновенная постройка: `Manager.addToQueue(new PlaceStructureOperation(new StructurePlacer(
  new CreativeBuildingStructureHandler(level, anchor, blueprint, rotMir, true)), null))` — `player` nullable,
  очередь Structurize растянута по тикам, хендлер сам регистрирует блоки здания (`registerBlockPosition`).
  **Не использовать** `CreativeBuildingStructureHandler.loadAndPlaceStructureWithRotation(..., Future, ...)`:
  он проверяет `hasBluePrint()` до чтения Future и молча ничего не ставит (на 0.2 так появился только блок хижины).
- Схемы: `StructurePacks.getStructurePack(pack)` (Structurize), ключ — имя из `pack.json` («Medieval Oak»).
- **Подземные слои.** Схемы MineColonies несут свой фундамент: нижние слои целиком из `structurize:blocksolidsubstitution`
  / `blocksubstitution` (у `medievaloak/fundamentals/townhall1` — слои 0–3, якорь на слое 5). Верхний подземный слой
  должен заменить поверхность земли. Число слоёв — метка `groundlevel` (`BlueprintTagUtils.getNumberOfGroundLevels`;
  хранится в `blueprintDataProvider.posTagMap` якорного TE, позиция относительно якоря). Метка есть только у ~36%
  схем уровней (2789 из 7682), у Medieval Oak и Nordic почти ни у одной хижины её нет.
  Если поставить схему нижним слоем на землю, получается пьедестал (баг 0.2).
- **Как угадываем без метки** (проверено на всех 2789 размеченных схемах сборки, скрипт разбора NBT на Python):
  - высота земли относительно якоря одинакова на всех уровнях одной хижины (2761 из 2770) — считаем по уровню 1
    и переносим на нужный уровень через якорь;
  - на уровне 1 подземные — нижние слои, где воздуха < 10%. «Совсем без воздуха» ломается на подвалах: у
    `townhall2` Medieval Oak подвал в слоях 1–3 → насчитывался 1 слой, ратуша висела на 3 блока (баг 0.3);
  - правила по одной схеме (без воздуха / <10% / <20% воздуха / по `blocksolidsubstitution` / по скачку воздуха)
    угадывают метку лишь в 67–82% случаев, через уровень 1 с порогом 10% — 82%, 20% — 86%, но 20% утапливает
    на блок пол с проходами у deliveryman1/warehouse1 Medieval Oak (в игре стоят верно при 10%).
- **Имена схем различаются по стилям**: у Shire `courier`, `forester`, `restaurant` вместо `deliveryman`, `lumberjack`, `cook`,
  и пути тоже разные. Поэтому схему ищем по блоку хижины в якоре (`blueprint.getBlockState(getPrimaryBlockOffset())`):
  `HutBlueprints` один раз на стиль читает все `*1.blueprint` пака (быстро, доли секунды) и строит индекс
  «блок хижины → путь без уровня»; из вариантов (`altfarmer`) берётся самое короткое имя. `StructurePacks.findBlueprint(pack, predicate)`
  не годится: грузит весь пак на каждый вызов.
- Проверено на 0.3: 10 зданий (ратуша 2 + 9 хижин уровней 1–2) в Medieval Oak и Shire регистрируются, но вставка
  целого города через очередь Structurize грузит серверный поток: «Can't keep up» на 25–45 с, пока идёт вставка.
  Для материализации (этап 2) — ставить хижины в очередь по одной и/или вне глаз игрока.
- Очередь Structurize **не сохраняется** при остановке сервера: недостроенная схема так и останется недостроенной.

## Дымовые тесты на сервере

- Консоль через stdin `runServer` для сценариев не годится: Gradle отдаёт ввод серверу пачкой в конце, паузы между
  командами теряются (постройка не успевает, проверки идут в том же тике).
- Используем RCON: `tools/smoke.sh <файл-команд>` (строки `wait N` — настоящие паузы). Нужно в `run/server.properties`:
  `enable-rcon=true`, `rcon.password=svdev`.
- Позицию брать `positioned over motion_blocking_no_leaves`: `world_surface` ставит на кроны деревьев.
- Проверка блоков: `execute unless block X Y Z minecraft:air run say ...` по столбцам; подсчёт непустых блоков в области —
  `clone <область> <пустое место> masked` («Successfully cloned N»). Ратуша 1 Medieval Oak: 352 блока над землёй.

### Жители и стража (для 0.4)
- `CitizenManager.onColonyTick` сам спавнит жителей, если есть ратуша, включена настройка `BuildingTownHall.MOVE_IN`
  и есть места. Принудительно: `colony.getCitizenManager().spawnOrCreateCivilian(null, level, list, true)`
  (так делает `/mc citizens spawnNew`), затем `CitizenAddedModEvent`.
- Сам MineColonies спавнит лишь `initialCitizenAmount` жителей; дальше новые приходят только детьми или через таверну.
  Поэтому НПС-колонию заселяем сами (`world.Settler`): на каждый `IAssignsJob`-модуль хижины (работники и стража),
  пока `!isFull()`, — `createAndRegisterCivilianData` → `setGenderAndGenerateName` → предметы в `getInventory()` →
  `spawnOrCreateCivilian(data, level, List.of(townHall), true)` → `module.assignCitizen(data)` → `CitizenAddedModEvent`.
  Авто-найм (`WorkerBuildingModule.onColonyTick`, настройка ратуши `AUTO_HIRING_MODE`) работает только в `ACTIVE`,
  прямое назначение — сразу. Дома жители занимают сами (`LivingBuildingModule.onColonyTick`, `AUTO_HOUSING_MODE`).
- **Не каждый `IAssignsJob` даёт работу**: `warehouse_couriers` у склада только привязывает уже нанятых курьеров
  (`assignCitizen` → `true`, а `getJob()` остаётся `null`). На 0.4 так получились 2 безработных. Проверяем `getJob() != null`.
- Инструменты НПС-колонии не принесёт никто — выдаём стартовый набор в инвентарь жителя.
- Проверено на сервере 0.4: город из 0.3 (Medieval Oak) → 6 жителей, у всех профессия (по сохранению `colonyN.dat`).
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
- `Permissions.setOwner` (по байткоду): `players.remove(старый владелец)`, новые `ownerName/ownerUUID`, запись ранга 0,
  `fullyAbandoned = false`, `markDirty`. Прежний владелец не остаётся даже офицером. Реализовано в `world.OwnerChange`,
  команда `/realmdebug transfer <id> ruler|player <ник>`.

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
