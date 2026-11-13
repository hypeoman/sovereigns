package dev.sovereigns;

import com.minecolonies.api.colony.IColony;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.sovereigns.world.ColonySpawner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
                                                StringArgumentType.getString(ctx, "style")))))));
    }

    private static final String DEFAULT_PACK = "Medieval Oak";

    // Uses the source position, not a player, so console smoke tests can run it via "execute positioned".
    private static int spawnColony(CommandSourceStack source, String name, String pack) {
        try {
            // Debug colonies get a province id from their name until stage 1 provides real provinces.
            IColony colony = ColonySpawner.spawn(source.getLevel(), BlockPos.containing(source.getPosition()), "debug:" + name, name,
                    "Ruler of " + name, pack);
            source.sendSuccess(() -> Component.literal("Colony #" + colony.getID() + " '" + name + "' (" + pack + ") owned by "
                    + colony.getPermissions().getOwnerName()), true);
            return colony.getID();
        } catch (IllegalStateException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }
}
