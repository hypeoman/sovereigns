package dev.sovereigns.world;

import java.util.ArrayList;
import java.util.List;

/** Pure layout math: huts in two rows north and south of the town hall, packed along X and centered on it. */
final class TownLayout {
    static final int GAP = 3;

    record Footprint(int sizeX, int sizeZ) {
    }

    /** Min corner of a footprint, relative to the town hall's min corner. */
    record Spot(int x, int z) {
    }

    private TownLayout() {
    }

    static List<Spot> arrange(Footprint hall, List<Footprint> huts) {
        // Each hut goes to the currently narrower row, so the rows stay balanced.
        List<List<Integer>> rows = List.of(new ArrayList<>(), new ArrayList<>());
        int[] width = new int[2];
        for (int i = 0; i < huts.size(); i++) {
            int row = width[0] <= width[1] ? 0 : 1;
            width[row] += (rows.get(row).isEmpty() ? 0 : GAP) + huts.get(i).sizeX();
            rows.get(row).add(i);
        }
        Spot[] spots = new Spot[huts.size()];
        for (int row = 0; row < 2; row++) {
            int x = hall.sizeX() / 2 - width[row] / 2;
            for (int i : rows.get(row)) {
                Footprint hut = huts.get(i);
                // Row 0 north (-Z), touching the hall's north edge through the gap; row 1 south.
                spots[i] = new Spot(x, row == 0 ? -GAP - hut.sizeZ() : hall.sizeZ() + GAP);
                x += hut.sizeX() + GAP;
            }
        }
        return List.of(spots);
    }
}
