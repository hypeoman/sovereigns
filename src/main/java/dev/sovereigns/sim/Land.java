package dev.sovereigns.sim;

/** What a map point or a province is, as far as the strategy layer cares. Append only: the ordinal is saved. */
public enum Land {
    WATER(0, 99),
    PLAINS(1.45, 1.0),
    FOREST(1.35, 1.2),
    TAIGA(1.25, 1.3),
    SNOW(1.1, 1.6),
    DESERT(1.1, 1.4),
    SAVANNA(1.35, 1.1),
    JUNGLE(1.3, 1.7),
    SWAMP(1.2, 1.7),
    MOUNTAIN(1.1, 3.0);

    /** Food per inhabitant per day before development and stability; 1.0 just feeds the people. */
    public final double fertility;
    /** Cost for a state's borders to spread into this land: mountains make natural borders. */
    public final double crossCost;

    Land(double fertility, double crossCost) {
        this.fertility = fertility;
        this.crossCost = crossCost;
    }
}
