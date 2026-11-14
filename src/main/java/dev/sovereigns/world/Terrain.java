package dev.sovereigns.world;

import dev.sovereigns.sim.Balance;
import dev.sovereigns.sim.Land;
import dev.sovereigns.sim.MapGen;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.common.Tags;

/** Terrain of a province cell straight from the world generator: no chunks are loaded or generated. */
final class Terrain {
    private Terrain() {}

    static MapGen.CellSample sample(ServerLevel level, int cx, int cz, Balance b) {
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        int sea = gen.getSeaLevel(), n = MapGen.POINTS;
        Land[] lands = new Land[n * n];
        int[] heights = new int[n * n];
        for (int i = 0; i < n * n; i++) {
            int x = MapGen.samplePoint(cx, i % n, b), z = MapGen.samplePoint(cz, i / n, b);
            int h = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
            Holder<Biome> biome = gen.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(Math.max(h, sea)),
                    QuartPos.fromBlock(z), random.sampler());
            heights[i] = h;
            lands[i] = h < sea - 1 ? Land.WATER : land(biome);
        }
        return new MapGen.CellSample(lands, heights);
    }

    static Land land(Holder<Biome> biome) {
        if (biome.is(Tags.Biomes.IS_OCEAN) || biome.is(Tags.Biomes.IS_RIVER)) return Land.WATER;
        if (biome.is(Tags.Biomes.IS_MOUNTAIN)) return Land.MOUNTAIN;
        if (biome.is(Tags.Biomes.IS_SWAMP)) return Land.SWAMP;
        if (biome.is(Tags.Biomes.IS_JUNGLE)) return Land.JUNGLE;
        if (biome.is(Tags.Biomes.IS_SAVANNA) || biome.is(Tags.Biomes.IS_BADLANDS)) return Land.SAVANNA;
        if (biome.is(Tags.Biomes.IS_DESERT)) return Land.DESERT;
        if (biome.is(Tags.Biomes.IS_SNOWY)) return Land.SNOW;
        if (biome.is(Tags.Biomes.IS_TAIGA)) return Land.TAIGA;
        if (biome.is(Tags.Biomes.IS_FOREST)) return Land.FOREST;
        // Untagged modded biomes: fall back to the climate.
        float t = biome.value().getBaseTemperature();
        if (t < 0.15f) return Land.SNOW;
        if (t < 0.4f) return Land.TAIGA;
        if (t > 1.5f && !biome.value().hasPrecipitation()) return Land.DESERT;
        return Land.PLAINS;
    }
}
