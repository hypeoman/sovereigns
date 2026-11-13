package dev.sovereigns.world;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.storage.StructurePackMeta;
import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.api.blocks.AbstractBlockHut;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.Block;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Finds a style pack's blueprint for a hut by the hut block inside it: file names differ per pack
 * (Shire has "courier", "forester" where Medieval Oak has "deliveryman", "lumberjack").
 */
final class HutBlueprints {
    private static final String LEVEL_1 = "1.blueprint";
    private static final Map<String, Map<Block, String>> INDEX = new ConcurrentHashMap<>();

    private HutBlueprints() {
    }

    /** Pack-relative path like "fundamentals/townhall2.blueprint", or null when the pack has no such hut. */
    static String path(String pack, Block hut, int level, HolderLookup.Provider provider) {
        String base = INDEX.computeIfAbsent(pack, p -> build(p, provider)).get(hut);
        return base == null ? null : base + level + ".blueprint";
    }

    // shortcut: reads every level-1 blueprint of the pack once on the server thread (debug command); go async in stage 2.
    private static Map<Block, String> build(String pack, HolderLookup.Provider provider) {
        StructurePackMeta meta = StructurePacks.getStructurePack(pack);
        Map<Block, String> index = new HashMap<>();
        try (Stream<Path> files = Files.walk(meta.getPath())) {
            for (Iterator<Path> it = files.filter(f -> f.getFileName().toString().endsWith(LEVEL_1)).iterator(); it.hasNext(); ) {
                Path file = it.next();
                Blueprint blueprint = StructurePacks.getBlueprint(pack, file, true, provider);
                if (blueprint == null) {
                    continue;
                }
                Block block = blueprint.getBlockState(blueprint.getPrimaryBlockOffset()).getBlock();
                if (block instanceof AbstractBlockHut<?>) {
                    String sub = meta.getSubPath(file).replace('\\', '/');
                    index.merge(block, sub.substring(0, sub.length() - LEVEL_1.length()), HutBlueprints::preferred);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return index;
    }

    /** The plain variant beats "alt..." ones: shorter file name wins, ties by name for a stable choice. */
    static String preferred(String a, String b) {
        String na = a.substring(a.lastIndexOf('/') + 1), nb = b.substring(b.lastIndexOf('/') + 1);
        int c = na.length() != nb.length() ? Integer.compare(na.length(), nb.length()) : a.compareTo(b);
        return c <= 0 ? a : b;
    }
}
