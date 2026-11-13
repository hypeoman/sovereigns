package dev.sovereigns;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.sovereigns.world.ColonySpawner;
import dev.sovereigns.world.OwnerChange;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod(Sovereigns.MODID)
public final class Sovereigns {
    public static final String MODID = "sovereigns";

    private final String version;

    public Sovereigns(ModContainer container) {
        this.version = container.getModInfo().getVersion().toString();
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("realmdebug")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("ping").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("Sovereigns " + version + ": pong"), false);
                    return 1;
                }))
                .then(Commands.literal("spawncolony")
                        .executes(ctx -> spawnColony(ctx.getSource(), "Debug Realm", DEFAULT_PACK))
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> spawnColony(ctx.getSource(), StringArgumentType.getString(ctx, "name"), DEFAULT_PACK))
                                .then(Commands.argument("style", StringArgumentType.greedyString())
                                        .executes(ctx -> spawnColony(ctx.getSource(), StringArgumentType.getString(ctx, "name"),
                                                StringArgumentType.getString(ctx, "style"))))))
                .then(Commands.literal("transfer")
                        .then(Commands.argument("colony", IntegerArgumentType.integer(1))
                                .then(Commands.literal("ruler").executes(ctx -> transfer(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "colony"), null)))
                                .then(Commands.literal("player").then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> transfer(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "colony"),
                                                EntityArgument.getPlayer(ctx, "target"))))))));
    }

    /** Hands the colony to its fake ruler ({@code player == null}) or to a player. */
    private static int transfer(CommandSourceStack source, int id, ServerPlayer player) {
        IColony colony = IColonyManager.getInstance().getColonyByWorld(id, source.getLevel());
        if (colony == null) {
            source.sendFailure(Component.literal("No colony #" + id + " in this dimension"));
            return 0;
        }
        if (player == null) {
            // Same province id and ruler name as spawncolony, so a debug colony gets its original ruler back.
            OwnerChange.toRuler(source.getLevel(), colony, "debug:" + colony.getName(), "Ruler of " + colony.getName());
        } else {
            OwnerChange.toPlayer(colony, player);
        }
        source.sendSuccess(() -> Component.literal("Colony #" + id + " '" + colony.getName() + "' now owned by "
                + colony.getPermissions().getOwnerName()), true);
        return id;
    }

    private static final String DEFAULT_PACK = "Medieval Oak";

    // Uses the source position, not a player, so console smoke tests can run it via "execute positioned".
    private static int spawnColony(CommandSourceStack source, String name, String pack) {
        try {
            // Debug colonies get a province id from their name until stage 1 provides real provinces.
            ColonySpawner.Spawned town = ColonySpawner.spawn(source.getLevel(), BlockPos.containing(source.getPosition()), "debug:" + name,
                    name, "Ruler of " + name, pack, 2, ColonySpawner.debugTown());
            IColony colony = town.colony();
            source.sendSuccess(() -> Component.literal("Colony #" + colony.getID() + " '" + name + "' (" + pack + ") owned by "
                    + colony.getPermissions().getOwnerName() + "; buildings " + town.placed() + "; citizens " + town.citizens()
                    + (town.skipped().isEmpty() ? "" : "; no blueprint for " + town.skipped())), true);
            return colony.getID();
        } catch (IllegalStateException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }
}
