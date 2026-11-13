package dev.sovereigns;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.sovereigns.sim.Balance;
import dev.sovereigns.sim.Person;
import dev.sovereigns.sim.Province;
import dev.sovereigns.sim.State;
import dev.sovereigns.sim.World;
import dev.sovereigns.world.Realms;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** {@code /realm info|map|chronicle} and the strategy-layer part of {@code /realmdebug}. */
final class RealmCommands {
    private RealmCommands() {}

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("realm")
                .then(Commands.literal("info")
                        .executes(ctx -> reply(ctx.getSource(), RealmCommands::here))
                        .then(Commands.argument("state", IntegerArgumentType.integer(1))
                                .executes(ctx -> reply(ctx.getSource(), (w, b, src) -> state(w, b, IntegerArgumentType.getInteger(ctx, "state"))))))
                .then(Commands.literal("map").executes(ctx -> reply(ctx.getSource(), RealmCommands::map)))
                .then(Commands.literal("chronicle")
                        .executes(ctx -> reply(ctx.getSource(), (w, b, src) -> chronicle(w, 10)))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 200))
                                .executes(ctx -> reply(ctx.getSource(), (w, b, src) -> chronicle(w, IntegerArgumentType.getInteger(ctx, "count")))))));

        dispatcher.register(Commands.literal("realmdebug")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("advance").then(Commands.argument("days", IntegerArgumentType.integer(1, 1000))
                        .executes(ctx -> reply(ctx.getSource(), (w, b, src) -> {
                            int days = IntegerArgumentType.getInteger(ctx, "days");
                            double ms = Realms.advance(src.getServer().overworld(), days);
                            return String.format("Advanced %d days to day %d in %.1f ms (%.3f ms/day)", days, w.day, ms, ms / days);
                        }))))
                .then(Commands.literal("genregion").then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                .executes(ctx -> reply(ctx.getSource(), (w, b, src) -> {
                                    int x = IntegerArgumentType.getInteger(ctx, "x"), z = IntegerArgumentType.getInteger(ctx, "z");
                                    boolean queued = Realms.queueRegion(x, z,
                                            msg -> src.sendSuccess(() -> Component.literal(msg), true));
                                    return queued ? "Region at " + x + " " + z + " queued; sampling terrain..."
                                            : "Region at " + x + " " + z + " is already generated or queued";
                                }))))));
    }

    private interface Action {
        String run(World w, Balance b, CommandSourceStack src);
    }

    private static int reply(CommandSourceStack src, Action action) {
        World w = Realms.world();
        if (w == null || w.regions.isEmpty()) {
            src.sendFailure(Component.literal("The political map is still being generated"));
            return 0;
        }
        String text = action.run(w, Realms.balance(), src);
        src.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static String here(World w, Balance b, CommandSourceStack src) {
        Vec3 pos = src.getPosition();
        Province p = w.provinceAt((int) Math.floor(pos.x), (int) Math.floor(pos.z), b);
        if (p == null) return "No province here: open water or an ungenerated region";
        String text = String.format("Province %s [%d, %d], %s, settlement site (%d, %d)%n", p.name, p.cx, p.cz,
                p.land.name().toLowerCase(), p.siteX, p.siteZ);
        if (p.owner == 0) return text + "Unowned land";
        text += String.format("Population %.1f, level %d, stability %.0f, food %.0f%s%n", p.population, p.development,
                p.stability, p.food, p.starving ? ", STARVING" : "");
        return text + state(w, b, p.owner);
    }

    private static String state(World w, Balance b, int id) {
        State s = w.states.get(id);
        if (s == null) return "No state #" + id;
        Province cap = w.provinces.get(s.capital);
        Person ruler = w.people.get(s.ruler);
        List<Province> own = w.provincesOf(s.id);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("#%d %s (%s), founded day %d%n", s.id, w.title(s), s.culture.style, s.foundedDay));
        if (ruler != null) {
            sb.append(String.format("Ruler %s of %s, age %d; aggression %d, greed %d, pride %d, diplomacy %d%n", ruler.name,
                    ruler.dynasty, (w.day - ruler.birthDay) / 365, ruler.aggression, ruler.greed, ruler.pride, ruler.diplomacy));
        }
        sb.append(String.format("Capital %s at (%d, %d)%n", cap.name, cap.siteX, cap.siteZ));
        if (!s.independent()) sb.append(String.format("Liege #%d %s%n", s.liege, w.title(w.states.get(s.liege))));
        sb.append(String.format("Own provinces %d, population %.0f, treasury %.0f", own.size(),
                own.stream().mapToDouble(p -> p.population).sum(), s.treasury));
        if (s.independent()) {
            List<Province> all = w.provincesOfRealm(s);
            sb.append(String.format("%nRealm: provinces %d, population %.0f, army %.1f", all.size(),
                    all.stream().mapToDouble(p -> p.population).sum(), s.army));
        }
        List<String> vassals = w.states.values().stream().filter(v -> v.alive && v.liege == s.id)
                .map(v -> "#" + v.id + " " + w.title(v)).toList();
        if (!vassals.isEmpty()) sb.append("\nVassals: ").append(String.join(", ", vassals));
        return sb.toString();
    }

    private static String map(World w, Balance b, CommandSourceStack src) {
        List<String> lines = new ArrayList<>();
        long unowned = w.provinces.values().stream().filter(p -> p.owner == 0).count();
        lines.add(String.format("Day %d: %d independent states, %d regions, %d provinces (%d unowned)", w.day,
                w.realms().size(), w.regions.size(), w.provinces.size(), unowned));
        for (State r : w.realms().stream().sorted(Comparator.comparingInt((State r) -> -w.provincesOfRealm(r).size())).toList()) {
            List<Province> all = w.provincesOfRealm(r);
            Province cap = w.provinces.get(r.capital);
            lines.add(String.format("#%d %s: %d prov, pop %.0f, army %.0f, capital (%d, %d)", r.id, w.title(r), all.size(),
                    all.stream().mapToDouble(p -> p.population).sum(), r.army, cap.siteX, cap.siteZ));
            for (State v : w.tree(r)) {
                if (v != r) lines.add(String.format("   #%d %s: %d prov", v.id, w.title(v), w.provincesOf(v.id).size()));
            }
        }
        return String.join("\n", lines);
    }

    private static String chronicle(World w, int count) {
        List<World.Entry> all = new ArrayList<>(w.chronicle);
        List<String> lines = new ArrayList<>();
        for (World.Entry e : all.subList(Math.max(0, all.size() - count), all.size())) lines.add("Day " + e.day() + ": " + e.text());
        return lines.isEmpty() ? "The chronicle is empty" : String.join("\n", lines);
    }
}
