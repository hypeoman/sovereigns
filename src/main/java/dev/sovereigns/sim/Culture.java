package dev.sovereigns.sim;

import java.util.SplittableRandom;

/** Culture of a state: picked from the land of its capital, gives the MineColonies style and name syllables. Append only: the ordinal is saved. */
public enum Culture {
    NORDIC("Nordic Spruce", "thor", "vald", "sig", "ulf", "hra", "bjor", "ey", "gard", "heim", "sk", "ar", "run"),
    SPRUCE("Medieval Spruce", "vol", "mir", "slav", "grad", "ost", "rad", "bor", "yar", "vel", "ko", "na", "drev"),
    OAK("Medieval Oak", "al", "bran", "wick", "ford", "ton", "mer", "ash", "dun", "wen", "ley", "cas", "ver"),
    BIRCH("Medieval Birch", "el", "wyn", "lor", "ian", "thal", "sil", "mor", "ra", "dor", "ae", "lin", "fen"),
    DESERT("Desert Oasis", "al", "zah", "ra", "kar", "sul", "qa", "mir", "dun", "han", "esh", "bar", "tam"),
    SAVANNA("Urban Savanna", "ka", "ma", "zu", "ri", "ndo", "ba", "tu", "we", "lo", "ki", "ra", "sa"),
    JUNGLE("Jungle Treehouse", "itz", "xa", "tl", "co", "pa", "hu", "ma", "ya", "te", "qui", "lo", "ca"),
    SWAMP("Dark Oak Treehouse", "mog", "bal", "fen", "gul", "dra", "sk", "mire", "wal", "ob", "ru", "ash", "ven");

    /** MineColonies structure pack name (from its pack.json). */
    public final String style;
    private final String[] syllables;

    Culture(String style, String... syllables) {
        this.style = style;
        this.syllables = syllables;
    }

    public static Culture of(Land land) {
        return switch (land) {
            case SNOW -> NORDIC;
            case TAIGA -> SPRUCE;
            case FOREST -> BIRCH;
            case DESERT -> DESERT;
            case SAVANNA -> SAVANNA;
            case JUNGLE -> JUNGLE;
            case SWAMP -> SWAMP;
            default -> OAK;
        };
    }

    /** A capitalised name of 2–3 syllables. */
    public String name(SplittableRandom rng) {
        StringBuilder sb = new StringBuilder();
        int n = 2 + rng.nextInt(2);
        for (int i = 0; i < n; i++) sb.append(syllables[rng.nextInt(syllables.length)]);
        return Character.toUpperCase(sb.charAt(0)) + sb.substring(1);
    }
}
