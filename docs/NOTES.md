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
