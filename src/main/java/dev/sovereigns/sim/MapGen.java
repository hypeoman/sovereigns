package dev.sovereigns.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.SplittableRandom;

/**
 * Generates one region of the political map from terrain samples. Depends only on the seed, the region
 * coordinates and what earlier regions already hold, never on loaded chunks.
 */
public final class MapGen {
    /** Terrain at the {@link #POINTS}×{@link #POINTS} sample points of a cell, row by row (x fastest). */
    public record CellSample(Land[] lands, int[] heights) {}

    public static final int POINTS = 3;

    private MapGen() {}

    /** First cell coordinate of region {@code r}; regions are centred so that region 0 holds the origin in its middle. */
    public static int firstCell(int r, Balance b) {
        return r * b.regionCells - b.regionCells / 2;
    }

    public static int regionOf(int blockCoord, Balance b) {
        return Math.floorDiv(Math.floorDiv(blockCoord, b.provinceSize) + b.regionCells / 2, b.regionCells);
    }

    /** Block coordinate of sample point {@code i} (0..POINTS-1) along one axis of cell {@code c}. */
    public static int samplePoint(int c, int i, Balance b) {
        return c * b.provinceSize + (2 * i + 1) * b.provinceSize / (2 * POINTS);
    }

    /**
     * Adds the region to the world. {@code samples} are indexed {@code dz * regionCells + dx} from the region's first cell.
     * Returns the new independent states (empty at the state limit).
     */
    public static List<State> generate(World w, Balance b, int rx, int rz, CellSample[] samples, int spawnX, int spawnZ) {
        long regionKey = Province.key(rx, rz);
        if (!w.regions.add(regionKey)) return List.of();
        SplittableRandom rng = new SplittableRandom(w.seed ^ (regionKey * 0x9E3779B97F4A7C15L));
        int n = b.regionCells, x0 = firstCell(rx, b), z0 = firstCell(rz, b);

        List<Province> land = new ArrayList<>();
        for (int dz = 0; dz < n; dz++) {
            for (int dx = 0; dx < n; dx++) {
                Province p = province(x0 + dx, z0 + dz, samples[dz * n + dx], b);
                if (p == null) continue;
                p.name = Culture.of(p.land).name(rng);
                w.provinces.put(p.key(), p);
                land.add(p);
            }
        }

        int allowed = b.independentStateLimit - w.realms().size();
        int wanted = Math.min(allowed, b.statesPerRegionMin + rng.nextInt(b.statesPerRegionMax - b.statesPerRegionMin + 1));
        List<Province> capitals = pickCapitals(w, b, land, wanted, rng, spawnX, spawnZ);

        List<State> created = new ArrayList<>();
        double[] reach = new double[capitals.size()];
        for (int i = 0; i < capitals.size(); i++) {
            Province cap = capitals.get(i);
            State s = newState(w, rng, Culture.of(cap.land), cap, 0, b);
            cap.name = s.name;
            created.add(s);
            reach[i] = b.claimRange * (0.5 + rng.nextDouble());
        }
        spreadBorders(w, created, reach, x0, z0, n);
        for (State s : created) {
            addDuchies(w, b, rng, s);
            settle(w, b, s);
            w.record(s.id, w.title(s) + " rises around " + w.provinces.get(s.capital).name, b.chronicleSize);
        }
        return created;
    }

    static Province province(int cx, int cz, CellSample s, Balance b) {
        int water = 0, min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        int[] counts = new int[Land.values().length];
        for (int i = 0; i < s.lands().length; i++) {
            Land l = s.lands()[i];
            counts[l.ordinal()]++;
            if (l == Land.WATER) {
                water++;
            } else {
                min = Math.min(min, s.heights()[i]);
                max = Math.max(max, s.heights()[i]);
            }
        }
        if (water * 2 > s.lands().length) return null;
        Land major = Land.PLAINS;
        for (Land l : Land.values()) if (l != Land.WATER && counts[l.ordinal()] > counts[major.ordinal()]) major = l;
        int rough = max - min;
        if (rough > b.mountainRoughness) major = Land.MOUNTAIN;

        // Site: the land point whose land neighbours are closest to its own height.
        int best = -1, bestScore = Integer.MAX_VALUE;
        for (int i = 0; i < s.lands().length; i++) {
            if (s.lands()[i] == Land.WATER) continue;
            int score = 0, px = i % POINTS, pz = i / POINTS;
            for (int j = 0; j < s.lands().length; j++) {
                if (j == i || s.lands()[j] == Land.WATER || Math.abs(j % POINTS - px) > 1 || Math.abs(j / POINTS - pz) > 1) continue;
                score += Math.abs(s.heights()[j] - s.heights()[i]);
            }
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return new Province(cx, cz, major, samplePoint(cx, best % POINTS, b), samplePoint(cz, best / POINTS, b), rough, water > 0);
    }

    private static List<Province> pickCapitals(World w, Balance b, List<Province> land, int wanted, SplittableRandom rng,
                                               int spawnX, int spawnZ) {
        List<Province> placed = new ArrayList<>();
        for (State s : w.states.values()) if (s.alive) placed.add(w.provinces.get(s.capital));
        Map<Province, Double> score = new HashMap<>();
        for (Province p : land) score.put(p, p.roughness - (p.nearWater ? 10 : 0) + rng.nextDouble() * 12);
        List<Province> candidates = new ArrayList<>(land.stream()
                .filter(p -> p.land != Land.MOUNTAIN && p.land != Land.SWAMP)
                .filter(p -> dist(p.siteX, p.siteZ, spawnX, spawnZ) >= b.capitalMinSpawnDistance).toList());
        candidates.sort(Comparator.comparingDouble(score::get));
        List<Province> out = new ArrayList<>();
        for (Province p : candidates) {
            if (out.size() >= wanted) break;
            if (placed.stream().allMatch(q -> dist(p.siteX, p.siteZ, q.siteX, q.siteZ) >= b.capitalMinDistance)) {
                out.add(p);
                placed.add(p);
            }
        }
        return out;
    }

    /** Multi-source Dijkstra from the capitals over unowned cells of the region; the cheapest claim wins. */
    private static void spreadBorders(World w, List<State> states, double[] reach, int x0, int z0, int n) {
        record Step(double cost, int state, Province p) {}
        PriorityQueue<Step> queue = new PriorityQueue<>(Comparator.comparingDouble(Step::cost)
                .thenComparingInt(Step::state).thenComparingLong(s -> s.p().key()));
        for (int i = 0; i < states.size(); i++) queue.add(new Step(0, i, w.provinces.get(states.get(i).capital)));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            Step step = queue.poll();
            if (step.p().owner != 0 && step.cost() > 0) continue; // capitals start owned
            step.p().owner = states.get(step.state()).id;
            for (int[] d : dirs) {
                int cx = step.p().cx + d[0], cz = step.p().cz + d[1];
                if (cx < x0 || cz < z0 || cx >= x0 + n || cz >= z0 + n) continue;
                Province next = w.provinces.get(Province.key(cx, cz));
                if (next == null || next.owner != 0) continue;
                // Rivers and lakes inside a cell make it a little harder to cross.
                double cost = step.cost() + (next.land.crossCost + (next.nearWater ? 0.3 : 0)) / reach[step.state()];
                if (cost <= 1) queue.add(new Step(cost, step.state(), next));
            }
        }
    }

    /** Splits off one duchy per {@link Balance#provincesPerDuchy} provinces, seated in the far end of the realm. */
    private static void addDuchies(World w, Balance b, SplittableRandom rng, State realm) {
        Province cap = w.provinces.get(realm.capital);
        int total = w.provincesOf(realm.id).size();
        int duchies = total >= b.provincesPerDuchy ? total / b.provincesPerDuchy : 0;
        for (int i = 0; i < duchies; i++) {
            List<Province> free = new ArrayList<>(w.provincesOf(realm.id).stream().filter(p -> p != cap).toList());
            if (free.isEmpty()) return;
            Province seat = free.stream().max(Comparator.comparingInt((Province p) -> cells(p, cap))
                    .thenComparingLong(Province::key)).orElseThrow();
            State duchy = newState(w, rng, realm.culture, seat, realm.id, b);
            free.sort(Comparator.comparingInt((Province p) -> cells(p, seat)).thenComparingLong(Province::key));
            for (Province p : free.subList(0, Math.min(free.size(), total / (duchies + 1)))) p.owner = duchy.id;
        }
    }

    private static State newState(World w, SplittableRandom rng, Culture culture, Province capital, int liege, Balance b) {
        State s = new State(w.nextId++, culture.name(rng), culture, rng.nextInt(0x1000000), w.day);
        s.capital = capital.key();
        s.liege = liege;
        s.lastTick = w.day - s.id % b.farTickInterval; // staggers far ticks of different realms
        capital.owner = s.id;
        Person ruler = new Person(w.nextId++, culture.name(rng), s.name, w.day - 365 * (20 + rng.nextInt(40)),
                rng.nextInt(11), rng.nextInt(11), rng.nextInt(11), rng.nextInt(11));
        w.people.put(ruler.id, ruler);
        s.ruler = ruler.id;
        w.states.put(s.id, s);
        return s;
    }

    private static void settle(World w, Balance b, State realm) {
        for (Province p : w.provincesOfRealm(realm)) {
            boolean seat = w.states.get(p.owner).capital == p.key();
            boolean capital = p.key() == realm.capital;
            p.development = capital ? b.capitalStartDevelopment : seat ? 2 : 1;
            p.population = capital ? b.capitalStartPopulation : b.startPopulation;
            p.stability = b.stabilityBase;
            p.food = p.population * 5;
        }
    }

    private static int cells(Province a, Province b) {
        return Math.abs(a.cx - b.cx) + Math.abs(a.cz - b.cz);
    }

    private static double dist(int ax, int az, int bx, int bz) {
        return Math.hypot(ax - bx, az - bz);
    }
}
