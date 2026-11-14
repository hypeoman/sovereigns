package dev.sovereigns.sim;

/** One cell of the global province grid that has land. */
public final class Province {
    public final int cx, cz;
    public final Land land;
    /** Reserved settlement site, block coordinates. */
    public final int siteX, siteZ;
    /** Height spread of the sampled land points: rough terrain makes a poor capital. */
    public final int roughness;
    public final boolean nearWater;

    public String name = "";
    /** Direct owner: a state node id, 0 when nobody owns it. */
    public int owner;
    public double population;
    public int development = 1;
    public double food;
    public double stability = 70;
    public double buildPoints;
    public boolean starving;

    public Province(int cx, int cz, Land land, int siteX, int siteZ, int roughness, boolean nearWater) {
        this.cx = cx;
        this.cz = cz;
        this.land = land;
        this.siteX = siteX;
        this.siteZ = siteZ;
        this.roughness = roughness;
        this.nearWater = nearWater;
    }

    public long key() {
        return key(cx, cz);
    }

    public static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }
}
