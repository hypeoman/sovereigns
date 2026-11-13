package dev.sovereigns.sim;

/**
 * Balance numbers. Every public field becomes an entry of {@code sovereigns-server.toml} under the same name
 * (see {@code dev.sovereigns.Config}); the values here are the defaults. Money and food are abstract units,
 * rates are per day.
 */
public final class Balance {
    // ---- map ----
    /** Side of a province cell, blocks. */
    public int provinceSize = 512;
    /** Side of a region, in province cells. */
    public int regionCells = 8;
    public int statesPerRegionMin = 8;
    public int statesPerRegionMax = 12;
    /** Soft limit of independent states: regions generated at the limit get only unowned provinces. */
    public int independentStateLimit = 32;
    public int capitalMinDistance = 800;
    public int capitalMinSpawnDistance = 500;
    /** How far borders spread from a capital, in border cost (plains cost 1 per cell). */
    public double claimRange = 2.2;
    /** A state with at least this many provinces gets an internal vassal (duchy) per this many provinces. */
    public int provincesPerDuchy = 6;
    public int prehistoryDays = 75;

    // ---- tick ----
    /** Realms far from players tick once per this many days, for the whole period at once. */
    public int farTickInterval = 7;
    /** A realm with a province this close to a player (blocks) ticks every day, and so do its neighbours. */
    public int nearRadius = 2048;
    public int chronicleSize = 500;

    // ---- economy ----
    public double startPopulation = 12;
    public double capitalStartPopulation = 30;
    public int capitalStartDevelopment = 3;
    public int maxDevelopment = 10;
    /** Population cap of a province = populationBase + populationPerDevelopment * development. */
    public double populationBase = 8;
    public double populationPerDevelopment = 12;
    public double growthRate = 0.02;
    /** Starving provinces lose this share of people per day. */
    public double famineDeathRate = 0.02;
    /** Food a province can store, in days of its consumption. */
    public double foodStorageDays = 30;
    /** Extra food output per development level (share of base output). */
    public double foodPerDevelopment = 0.04;
    public double taxPerPerson = 0.1;
    /** Share of the treasury a state spends on construction every day. */
    public double investShare = 0.3;
    /** Build points to raise a province from level L to L+1 = developmentCost * (L+1). */
    public double developmentCost = 25;
    /** Share of tax income an internal vassal passes to its liege. */
    public double tributeShare = 0.25;
    /** Army a realm aims for, as a share of its population, scaled by 0.5..1.5 with the ruler's aggression. */
    public double armyShare = 0.06;
    public double soldierUpkeep = 0.04;
    public double recruitCost = 1.0;
    public double stabilityBase = 70;
    /** Realms with more provinces than this lose stability for each extra one. */
    public int overextensionFree = 8;
    public double overextensionPenalty = 2;
    public double faminePenalty = 25;
    /** Share of the gap to the target stability closed per day. */
    public double stabilityDrift = 0.05;
}
