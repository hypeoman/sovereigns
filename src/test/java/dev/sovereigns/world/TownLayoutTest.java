package dev.sovereigns.world;

import dev.sovereigns.world.TownLayout.Footprint;
import dev.sovereigns.world.TownLayout.Spot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TownLayoutTest {
    @Test
    void footprintsKeepTheGapAndStayNearTheHall() {
        Footprint hall = new Footprint(27, 25);
        List<Footprint> huts = List.of(new Footprint(15, 13), new Footprint(9, 11), new Footprint(21, 17),
                new Footprint(11, 11), new Footprint(13, 9), new Footprint(7, 7), new Footprint(17, 15),
                new Footprint(11, 13), new Footprint(9, 9));
        List<Spot> spots = TownLayout.arrange(hall, huts);
        assertEquals(huts.size(), spots.size());

        List<int[]> boxes = new ArrayList<>(); // minX, minZ, maxX (exclusive), maxZ (exclusive)
        boxes.add(new int[]{0, 0, hall.sizeX(), hall.sizeZ()});
        for (int i = 0; i < huts.size(); i++) {
            Spot s = spots.get(i);
            boxes.add(new int[]{s.x(), s.z(), s.x() + huts.get(i).sizeX(), s.z() + huts.get(i).sizeZ()});
        }
        for (int a = 0; a < boxes.size(); a++) {
            int[] p = boxes.get(a);
            // Colony claim is ~4 chunks around the hall: everything must stay well inside it.
            assertTrue(Math.abs(p[0] - hall.sizeX() / 2) < 60 && Math.abs(p[2] - hall.sizeX() / 2) < 60, "x in claim");
            for (int b = a + 1; b < boxes.size(); b++) {
                int[] q = boxes.get(b);
                boolean apart = p[2] + TownLayout.GAP <= q[0] || q[2] + TownLayout.GAP <= p[0]
                        || p[3] + TownLayout.GAP <= q[1] || q[3] + TownLayout.GAP <= p[1];
                assertTrue(apart, "boxes " + a + " and " + b + " closer than the gap");
            }
        }
    }

    @Test
    void noHuts() {
        assertFalse(TownLayout.arrange(new Footprint(5, 5), List.of()).iterator().hasNext());
    }
}
