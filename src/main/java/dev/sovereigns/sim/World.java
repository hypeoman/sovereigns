package dev.sovereigns.sim;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** The whole strategy layer of one Minecraft world. Sorted maps keep iteration, and so the simulation, deterministic. */
public final class World {
    public record Entry(int day, int state, String text) {}

    public final long seed;
    public int day;
    public int nextId = 1;
    public final Map<Long, Province> provinces = new TreeMap<>();
    public final Map<Integer, State> states = new TreeMap<>();
    public final Map<Integer, Person> people = new TreeMap<>();
    /** Keys ({@link Province#key}) of generated regions. */
    public final Set<Long> regions = new TreeSet<>();
    /** Relation of a pair of states, -100..100, keyed by {@link #pair}; missing means 0. */
    public final Map<Long, Integer> relations = new TreeMap<>();
    public final Deque<Entry> chronicle = new ArrayDeque<>();

    public World(long seed) {
        this.seed = seed;
    }

    public Province provinceAt(int blockX, int blockZ, Balance b) {
        return provinces.get(Province.key(Math.floorDiv(blockX, b.provinceSize), Math.floorDiv(blockZ, b.provinceSize)));
    }

    /** Top node of the holdings tree above {@code stateId}. */
    public State realmOf(int stateId) {
        State s = states.get(stateId);
        while (s != null && s.liege != 0) s = states.get(s.liege);
        return s;
    }

    public List<State> realms() {
        return states.values().stream().filter(s -> s.alive && s.independent()).toList();
    }

    /** The realm and all its vassals, vassals before their lieges. */
    public List<State> tree(State realm) {
        List<State> out = new ArrayList<>();
        for (State s : states.values()) if (s.alive && s.liege == realm.id) out.addAll(tree(s));
        out.add(realm);
        return out;
    }

    public List<Province> provincesOf(int stateId) {
        return provinces.values().stream().filter(p -> p.owner == stateId).toList();
    }

    public List<Province> provincesOfRealm(State realm) {
        return provinces.values().stream().filter(p -> p.owner != 0 && realmOf(p.owner) == realm).toList();
    }

    public String title(State s) {
        if (!s.independent()) return "Duchy of " + s.name;
        return (provincesOfRealm(s).size() >= 8 ? "Kingdom of " : "Principality of ") + s.name;
    }

    public int relation(int a, int b) {
        return relations.getOrDefault(pair(a, b), 0);
    }

    public static long pair(int a, int b) {
        return Province.key(Math.min(a, b), Math.max(a, b));
    }

    public void record(int state, String text, int maxSize) {
        chronicle.addLast(new Entry(day, state, text));
        while (chronicle.size() > maxSize) chronicle.removeFirst();
    }
}
