package dev.sovereigns;

import net.minecraft.commands.Commands;
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
                })));
    }
}
