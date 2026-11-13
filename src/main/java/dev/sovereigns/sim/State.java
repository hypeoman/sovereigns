package dev.sovereigns.sim;

/**
 * A node of the holdings tree: an independent realm ({@code liege == 0}) or an internal vassal of one.
 * Provinces point at their direct owner node.
 */
public final class State {
    public final int id;
    public final String name;
    public final Culture culture;
    public final int color;
    public final int foundedDay;
    public long capital;
    public int liege;
    public int ruler;
    public double treasury;
    public double army;
    /** Day of the last economy tick of the realm (kept on the top node). */
    public int lastTick;
    public boolean alive = true;

    public State(int id, String name, Culture culture, int color, int foundedDay) {
        this.id = id;
        this.name = name;
        this.culture = culture;
        this.color = color;
        this.foundedDay = foundedDay;
    }

    public boolean independent() {
        return liege == 0;
    }
}
