package dev.sovereigns.sim;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimTest {
    private final Balance b = new Balance();

    /** Smooth fake terrain: lakes and seas, hills, and land bands by z, all from the seed. */
    private MapGen.CellSample[] terrain(long seed, int rx, int rz) {
        int n = b.regionCells, p = MapGen.POINTS;
        MapGen.CellSample[] out = new MapGen.CellSample[n * n];
        double ox = (seed % 1000) * 37.0, oz = (seed % 777) * 53.0;
        for (int dz = 0; dz < n; dz++) {
            for (int dx = 0; dx < n; dx++) {
                Land[] lands = new Land[p * p];
                int[] heights = new int[p * p];
                for (int i = 0; i < p * p; i++) {
                    int x = MapGen.samplePoint(MapGen.firstCell(rx, b) + dx, i % p, b);
                    int z = MapGen.samplePoint(MapGen.firstCell(rz, b) + dz, i / p, b);
                    double h = 70 + 25 * Math.sin((x + ox) / 900.0) * Math.cos((z + oz) / 1100.0) + 30 * Math.sin((x - z + ox) / 400.0);
                    heights[i] = (int) h;
                    Land[] bands = {Land.SNOW, Land.TAIGA, Land.FOREST, Land.PLAINS, Land.SAVANNA, Land.DESERT};
                    lands[i] = h < 52 ? Land.WATER : bands[Math.floorMod(Math.floorDiv(z + (int) oz, 1500), bands.length)];
                }
                out[dz * n + dx] = new MapGen.CellSample(lands, heights);
            }
        }
        return out;
    }

    private World startWorld(long seed) {
        World w = new World(seed);
        Simulation.addRegion(w, b, 0, 0, terrain(seed, 0, 0), 0, 0);
        return w;
    }

    private double population(World w, State realm) {
        return w.provincesOfRealm(realm).stream().mapToDouble(p -> p.population).sum();
    }

    @Test
    void startRegionHasEightToTwelveRealmsOfDifferentSizes() {
        for (long seed : new long[]{1, 42, 12345, -987654321}) {
            World w = startWorld(seed);
            List<State> realms = w.realms();
            assertTrue(realms.size() >= 8 && realms.size() <= 12, "seed " + seed + ": " + realms.size() + " realms");
            List<Integer> sizes = realms.stream().map(r -> w.provincesOfRealm(r).size()).sorted().toList();
            assertTrue(sizes.get(0) < sizes.get(sizes.size() - 1), "sizes differ: " + sizes);
            assertTrue(w.provinces.values().stream().anyMatch(p -> p.owner == 0), "some land stays unowned");
            assertEquals(b.prehistoryDays, w.day);
            for (State r : realms) {
                Province cap = w.provinces.get(r.capital);
                assertEquals(r.id, cap.owner);
                assertTrue(Math.hypot(cap.siteX, cap.siteZ) >= b.capitalMinSpawnDistance);
            }
            System.out.println("seed " + seed + ": provinces per realm " + sizes + ", duchies "
                    + w.states.values().stream().filter(s -> !s.independent()).count());
        }
    }

    @Test
    void sameSeedSameMap() {
        assertArrayEquals(Codec.write(startWorld(7)), Codec.write(startWorld(7)));
        assertFalse(Arrays.equals(Codec.write(startWorld(7)), Codec.write(startWorld(8))));
    }

    @Test
    void newRegionAddsRealmsDeterministically() {
        World a = startWorld(5), c = startWorld(5);
        List<State> added = Simulation.addRegion(a, b, 1, 0, terrain(5, 1, 0), 0, 0);
        Simulation.addRegion(c, b, 1, 0, terrain(5, 1, 0), 0, 0);
        assertFalse(added.isEmpty());
        assertArrayEquals(Codec.write(a), Codec.write(c));
        for (State s : added) {
            Province cap = a.provinces.get(s.capital);
            assertEquals(1, MapGen.regionOf(cap.siteX, b));
        }
        assertTrue(Simulation.addRegion(a, b, 1, 0, terrain(5, 1, 0), 0, 0).isEmpty(), "a region is generated once");
    }

    @Test
    void atTheLimitNewRegionsAreUnowned() {
        World w = startWorld(3);
        b.independentStateLimit = w.realms().size();
        assertTrue(Simulation.addRegion(w, b, 0, 1, terrain(3, 0, 1), 0, 0).isEmpty());
        int first = MapGen.firstCell(1, b);
        assertTrue(w.provinces.values().stream().filter(p -> p.cz >= first).allMatch(p -> p.owner == 0));
        assertTrue(w.provinces.values().stream().anyMatch(p -> p.cz >= first));
    }

    @Test
    void hundredDaysChangeThingsReasonably() {
        World w = startWorld(11);
        Map<Integer, Double> before = w.realms().stream().collect(Collectors.toMap(r -> r.id, r -> population(w, r)));
        Map<Integer, Integer> devBefore = w.realms().stream().collect(Collectors.toMap(r -> r.id,
                r -> w.provincesOfRealm(r).stream().mapToInt(p -> p.development).sum()));
        long start = System.nanoTime();
        for (int i = 0; i < 100; i++) Simulation.tickDay(w, b, Set.of());
        double msPerDay = (System.nanoTime() - start) / 1e6 / 100;
        System.out.printf("tick %.3f ms/day%n", msPerDay);
        assertTrue(msPerDay < 5, "tick takes " + msPerDay + " ms");

        boolean anyGrew = false;
        for (State r : w.realms()) {
            double ratio = population(w, r) / before.get(r.id);
            System.out.printf("%s: pop %.0f -> %.0f, army %.1f, treasury %.0f, dev %d -> %d%n", w.title(r), before.get(r.id),
                    population(w, r), r.army, r.treasury, devBefore.get(r.id),
                    w.provincesOfRealm(r).stream().mapToInt(p -> p.development).sum());
            assertTrue(ratio > 0.5 && ratio < 10, w.title(r) + " population x" + ratio);
            anyGrew |= w.provincesOfRealm(r).stream().mapToInt(p -> p.development).sum() > devBefore.get(r.id);
        }
        assertTrue(anyGrew, "someone builds something");
    }

    @Test
    void farTicksMatchDailyTicks() {
        World daily = startWorld(9), far = startWorld(9);
        Set<Integer> all = daily.realms().stream().map(r -> r.id).collect(Collectors.toSet());
        for (int i = 0; i < 70; i++) {
            Simulation.tickDay(daily, b, all);
            Simulation.tickDay(far, b, Set.of());
        }
        Function<World, Double> total = w -> w.provinces.values().stream().mapToDouble(p -> p.population).sum();
        assertEquals(total.apply(daily), total.apply(far), total.apply(daily) * 0.05);
    }

    @Test
    void nearRealmsIncludeNeighbours() {
        World w = startWorld(4);
        State realm = w.realms().get(0);
        Province cap = w.provinces.get(realm.capital);
        b.nearRadius = 1;
        Set<Integer> near = Simulation.nearRealms(w, b, List.of(new int[]{cap.siteX, cap.siteZ}));
        assertTrue(near.contains(realm.id));
        assertTrue(Simulation.nearRealms(w, b, List.of(new int[]{1_000_000, 0})).isEmpty());
    }

    @Test
    void codecRoundTrip() {
        World w = startWorld(13);
        w.relations.put(World.pair(2, 1), -40);
        byte[] bytes = Codec.write(w);
        World back = Codec.read(Codec.SCHEMA, bytes);
        assertArrayEquals(bytes, Codec.write(back));
        assertEquals(-40, back.relation(1, 2));
        assertNotEquals(0, back.chronicle.size());
    }

    @Test
    void regionOfMatchesFirstCell() {
        for (int r = -3; r <= 3; r++) {
            int x = MapGen.firstCell(r, b) * b.provinceSize;
            assertEquals(r, MapGen.regionOf(x, b));
            assertEquals(r - 1, MapGen.regionOf(x - 1, b));
        }
    }
}
