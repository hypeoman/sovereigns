package dev.sovereigns.sim;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The daily tick of the strategy layer: production, consumption, taxes, growth, construction, stability. */
public final class Simulation {
    private Simulation() {}

    /**
     * Advances the world one day. Realms in {@code near} tick every day, the rest once per
     * {@link Balance#farTickInterval} days for the whole period. Returns how many realms ticked.
     */
    public static int tickDay(World w, Balance b, Set<Integer> near) {
        w.day++;
        int ticked = 0;
        for (State realm : w.realms()) {
            int days = w.day - realm.lastTick;
            if (days <= 0 || (days < b.farTickInterval && !near.contains(realm.id))) continue;
            tickRealm(w, b, realm, days);
            ticked++;
        }
        return ticked;
    }

    /**
     * Generates a region and lets its new realms live through their prehistory. The first region of a world
     * runs the whole world forward; later ones age only their own realms.
     */
    public static List<State> addRegion(World w, Balance b, int rx, int rz, MapGen.CellSample[] samples, int spawnX, int spawnZ) {
        boolean first = w.regions.isEmpty();
        List<State> created = MapGen.generate(w, b, rx, rz, samples, spawnX, spawnZ);
        if (first) {
            for (int i = 0; i < b.prehistoryDays; i++) tickDay(w, b, Set.of());
        } else {
            prehistory(w, b, created);
        }
        return created;
    }

    /** Short history of freshly generated realms; the world day does not move. */
    static void prehistory(World w, Balance b, Collection<State> realms) {
        for (State realm : realms) {
            for (int done = 0; done < b.prehistoryDays; done += b.farTickInterval) {
                tickRealm(w, b, realm, Math.min(b.farTickInterval, b.prehistoryDays - done));
            }
            realm.lastTick = w.day;
        }
    }

    /** Realms with a province within {@link Balance#nearRadius} of a player, plus their neighbours. */
    public static Set<Integer> nearRealms(World w, Balance b, List<int[]> playersXZ) {
        Set<Integer> near = new HashSet<>();
        for (Province p : w.provinces.values()) {
            if (p.owner == 0) continue;
            for (int[] xz : playersXZ) {
                if (Math.hypot(p.siteX - xz[0], p.siteZ - xz[1]) <= b.nearRadius) near.add(w.realmOf(p.owner).id);
            }
        }
        Set<Integer> withNeighbours = new HashSet<>(near);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (Province p : w.provinces.values()) {
            if (p.owner == 0 || !near.contains(w.realmOf(p.owner).id)) continue;
            for (int[] d : dirs) {
                Province q = w.provinces.get(Province.key(p.cx + d[0], p.cz + d[1]));
                if (q != null && q.owner != 0) withNeighbours.add(w.realmOf(q.owner).id);
            }
        }
        return withNeighbours;
    }

    static void tickRealm(World w, Balance b, State realm, int days) {
        List<Province> all = w.provincesOfRealm(realm);
        if (all.isEmpty()) return;
        double population = all.stream().mapToDouble(p -> p.population).sum();
        double armyFoodPerPerson = population > 0 ? realm.army / population : 0;
        double overextension = Math.max(0, all.size() - b.overextensionFree) * b.overextensionPenalty;

        for (State node : w.tree(realm)) {
            double income = 0;
            for (Province p : w.provincesOf(node.id)) income += tickProvince(w, b, p, days, armyFoodPerPerson, overextension);
            node.treasury += income;
            if (node.liege != 0) {
                double tribute = income * b.tributeShare;
                node.treasury -= tribute;
                w.states.get(node.liege).treasury += tribute;
            }
            build(w, b, node, days);
        }

        // Army: upkeep, desertion when unpaid, recruiting towards the ruler's taste.
        realm.treasury -= realm.army * b.soldierUpkeep * days;
        if (realm.treasury < 0) {
            realm.army *= Math.pow(0.9, days);
            realm.treasury = 0;
        }
        Person ruler = w.people.get(realm.ruler);
        double target = population * b.armyShare * (0.5 + (ruler == null ? 5 : ruler.aggression) / 10.0);
        double recruits = Math.min(target - realm.army, realm.treasury * 0.5 / b.recruitCost);
        if (recruits > 0) {
            realm.army += recruits;
            realm.treasury -= recruits * b.recruitCost;
            for (Province p : all) p.population -= recruits * p.population / population;
        }
        realm.lastTick = w.day;
    }

    /** Returns tax income of the province over the period. */
    private static double tickProvince(World w, Balance b, Province p, int days, double armyFoodPerPerson, double overextension) {
        double stabilityFactor = 0.75 + p.stability / 280; // 1.0 at stability 70
        double produced = p.population * p.land.fertility * (1 + b.foodPerDevelopment * (p.development - 1)) * stabilityFactor;
        double eaten = p.population * (1 + armyFoodPerPerson);
        double net = produced - eaten;
        p.food = Math.min(p.food + net * days, eaten * b.foodStorageDays);

        double cap = b.populationBase + b.populationPerDevelopment * p.development;
        boolean wasStarving = p.starving;
        p.starving = p.food < 0;
        if (p.starving) {
            p.food = 0;
            p.population *= Math.pow(1 - b.famineDeathRate, days);
            if (!wasStarving) {
                w.record(p.owner, "Famine strikes " + p.name, b.chronicleSize);
            }
        } else if (net > 0) {
            // Logistic growth, solved for the whole period so far ticks do not overshoot the cap.
            double k = Math.exp(b.growthRate * days * stabilityFactor);
            p.population = cap * p.population * k / (cap + p.population * (k - 1));
        }

        double target = b.stabilityBase - overextension - (p.starving ? b.faminePenalty : 0);
        p.stability += (target - p.stability) * (1 - Math.pow(1 - b.stabilityDrift, days));
        return p.population * b.taxPerPerson * p.stability / 100 * days;
    }

    /** Spends a share of the treasury on the least developed province of the node, the seat first on ties. */
    private static void build(World w, Balance b, State node, int days) {
        double spend = node.treasury * (1 - Math.pow(1 - b.investShare, days));
        if (spend <= 0) return;
        Province target = null;
        for (Province p : w.provincesOf(node.id)) {
            if (p.development >= b.maxDevelopment) continue;
            if (target == null || p.development < target.development
                    || (p.development == target.development && p.key() == node.capital)) target = p;
        }
        if (target == null) return;
        node.treasury -= spend;
        target.buildPoints += spend;
        double cost = b.developmentCost * (target.development + 1);
        while (target.buildPoints >= cost && target.development < b.maxDevelopment) {
            target.buildPoints -= cost;
            target.development++;
            cost = b.developmentCost * (target.development + 1);
            if (target.key() == node.capital) {
                w.record(node.id, target.name + " grows to level " + target.development, b.chronicleSize);
            }
        }
    }
}
