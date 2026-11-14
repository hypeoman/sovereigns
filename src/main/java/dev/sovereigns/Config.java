package dev.sovereigns;

import dev.sovereigns.sim.Balance;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/** {@code sovereigns-server.toml}: one entry per public field of {@link Balance}, same name, its value as default. */
public final class Config {
    private static final Map<Field, ModConfigSpec.ConfigValue<? extends Number>> VALUES = new LinkedHashMap<>();
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder()
                .comment("Balance of the Sovereigns strategy layer; meaning of each value: dev.sovereigns.sim.Balance")
                .push("balance");
        Balance defaults = new Balance();
        try {
            for (Field f : Balance.class.getFields()) {
                VALUES.put(f, f.getType() == int.class
                        ? builder.defineInRange(f.getName(), f.getInt(defaults), Integer.MIN_VALUE, Integer.MAX_VALUE)
                        : builder.defineInRange(f.getName(), f.getDouble(defaults), -Double.MAX_VALUE, Double.MAX_VALUE));
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        SPEC = builder.pop().build();
    }

    private Config() {}

    /** Current values; call once the server config is loaded. */
    public static Balance balance() {
        Balance b = new Balance();
        try {
            for (var e : VALUES.entrySet()) e.getKey().set(b, e.getValue().get());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return b;
    }
}
