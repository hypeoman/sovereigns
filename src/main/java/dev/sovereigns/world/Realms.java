package dev.sovereigns.world;

import com.mojang.logging.LogUtils;
import dev.sovereigns.Config;
import dev.sovereigns.sim.Balance;
import dev.sovereigns.sim.Codec;
import dev.sovereigns.sim.MapGen;
import dev.sovereigns.sim.Province;
import dev.sovereigns.sim.Simulation;
import dev.sovereigns.sim.World;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Keeps the strategy layer of the running server: saving, the daily tick and region generation spread over ticks. */
public final class Realms {
    private static final Logger LOG = LogUtils.getLogger();
    /** Time per server tick for sampling terrain of queued regions. */
    private static final long SAMPLE_BUDGET_NANOS = 5_000_000;
    private static final long TICKS_PER_DAY = 24000;

    /** Overworld saved data: the whole {@link World} as {@link Codec} bytes with their schema version. */
    public static final class Data extends SavedData {
        static final Factory<Data> FACTORY = new Factory<>(Data::new, Data::load, null);

        World world;
        long lastGameDay = -1;

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            if (world != null) {
                tag.putInt("schema", Codec.SCHEMA);
                tag.putByteArray("world", Codec.write(world));
            }
            tag.putLong("lastGameDay", lastGameDay);
            return tag;
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            if (tag.contains("world")) d.world = Codec.read(tag.getInt("schema"), tag.getByteArray("world"));
            d.lastGameDay = tag.getLong("lastGameDay");
            return d;
        }
    }

    private static final class Job {
        final int rx, rz;
        final MapGen.CellSample[] samples;
        final Consumer<String> onDone;
        int next, ticks;

        Job(int rx, int rz, int cells, Consumer<String> onDone) {
            this.rx = rx;
            this.rz = rz;
            this.samples = new MapGen.CellSample[cells];
            this.onDone = onDone;
        }
    }

    private static Data data;
    private static Balance balance;
    private static final Deque<Job> jobs = new ArrayDeque<>();

    private Realms() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Realms::onStarted);
        NeoForge.EVENT_BUS.addListener(Realms::onStopped);
        NeoForge.EVENT_BUS.addListener(Realms::onTick);
    }

    /** Null until the server has started. */
    public static World world() {
        return data == null ? null : data.world;
    }

    public static Balance balance() {
        return balance;
    }

    public static void changed() {
        data.setDirty();
    }

    /** Queues generation of the region holding the block; false if it exists or is queued already. */
    public static boolean queueRegion(int blockX, int blockZ, Consumer<String> onDone) {
        int rx = MapGen.regionOf(blockX, balance), rz = MapGen.regionOf(blockZ, balance);
        if (data.world.regions.contains(Province.key(rx, rz))
                || jobs.stream().anyMatch(j -> j.rx == rx && j.rz == rz)) return false;
        jobs.add(new Job(rx, rz, balance.regionCells * balance.regionCells, onDone));
        return true;
    }

    /** Runs {@code days} daily ticks right away; returns the time taken in ms. */
    public static double advance(ServerLevel overworld, int days) {
        long start = System.nanoTime();
        Set<Integer> near = Simulation.nearRealms(data.world, balance, players(overworld));
        for (int i = 0; i < days; i++) Simulation.tickDay(data.world, balance, near);
        data.setDirty();
        return (System.nanoTime() - start) / 1e6;
    }

    private static void onStarted(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        balance = Config.balance();
        data = overworld.getDataStorage().computeIfAbsent(Data.FACTORY, "sovereigns");
        if (data.world == null) data.world = new World(overworld.getSeed());
        if (data.lastGameDay < 0) data.lastGameDay = overworld.getGameTime() / TICKS_PER_DAY;
        data.setDirty();
        BlockPos spawn = overworld.getSharedSpawnPos();
        if (data.world.regions.isEmpty()) queueRegion(spawn.getX(), spawn.getZ(), msg -> {});
    }

    private static void onStopped(ServerStoppedEvent event) {
        data = null;
        jobs.clear();
    }

    private static void onTick(ServerTickEvent.Post event) {
        if (data == null) return;
        ServerLevel overworld = event.getServer().overworld();
        sampleRegions(overworld);
        if (overworld.getGameTime() % 100 == 0) {
            // Regions are made well before the player reaches them; chunk loading never waits for us.
            int ahead = balance.provinceSize * 2;
            for (int[] xz : players(overworld)) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) queueRegion(xz[0] + dx * ahead, xz[1] + dz * ahead, msg -> {});
                }
            }
        }
        long gameDay = overworld.getGameTime() / TICKS_PER_DAY;
        if (gameDay > data.lastGameDay) {
            data.lastGameDay = gameDay;
            if (!data.world.regions.isEmpty()) {
                long start = System.nanoTime();
                int ticked = Simulation.tickDay(data.world, balance, Simulation.nearRealms(data.world, balance, players(overworld)));
                LOG.info("Sovereigns day {}: {} realms ticked in {} ms", data.world.day, ticked,
                        String.format("%.2f", (System.nanoTime() - start) / 1e6));
            }
            data.setDirty();
        }
    }

    private static void sampleRegions(ServerLevel overworld) {
        Job job = jobs.peek();
        if (job == null) return;
        long deadline = System.nanoTime() + SAMPLE_BUDGET_NANOS;
        job.ticks++;
        int n = balance.regionCells;
        while (job.next < job.samples.length && System.nanoTime() < deadline) {
            int i = job.next++;
            job.samples[i] = Terrain.sample(overworld, MapGen.firstCell(job.rx, balance) + i % n,
                    MapGen.firstCell(job.rz, balance) + i / n, balance);
        }
        if (job.next < job.samples.length) return;
        jobs.poll();
        long start = System.nanoTime();
        BlockPos spawn = overworld.getSharedSpawnPos();
        int states = Simulation.addRegion(data.world, balance, job.rx, job.rz, job.samples, spawn.getX(), spawn.getZ()).size();
        data.setDirty();
        String msg = "Region (" + job.rx + ", " + job.rz + ") generated: " + states + " new states, "
                + data.world.realms().size() + " independent in total";
        LOG.info("Sovereigns: {} (terrain sampled over {} ticks; map and prehistory {} ms)", msg, job.ticks,
                String.format("%.1f", (System.nanoTime() - start) / 1e6));
        job.onDone.accept(msg);
    }

    private static List<int[]> players(ServerLevel overworld) {
        return overworld.players().stream().map((ServerPlayer p) -> new int[]{p.getBlockX(), p.getBlockZ()}).toList();
    }
}
