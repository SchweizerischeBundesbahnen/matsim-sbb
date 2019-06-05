package ch.sbb.matsim.zones;

import org.junit.Assert;
import org.junit.Test;
import org.locationtech.jts.geom.*;
import org.matsim.api.core.v01.Id;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mrieser
 */
public class ZonesQueryCacheTest {

    private static final GeometryFactory F = new GeometryFactory();

    @Test
    public void testFindZone() {
        TestZones zones = new TestZones("test");
        zones.zones.add(new TestZone("A", "aa", 10, 0, 20, 10));
        zones.zones.add(new TestZone("B", "bb", 50, 0, 70, 10));
        zones.zones.add(new TestZone("C", "cc", 80, 0, 90, 10));

        Zone z;
        ZonesQueryCache cache = new ZonesQueryCache(zones);
        z = cache.findZone(15, 5);
        Assert.assertEquals("aa", z.getAttribute("-").toString());
        z = cache.findZone(65, 5);
        Assert.assertEquals("bb", z.getAttribute("-").toString());
        z = cache.findZone(85, 5);
        Assert.assertEquals("cc", z.getAttribute("-").toString());
        z = cache.findZone(95, 15);
        Assert.assertNull(z);
        /* repeat some calls to check actual cache (okay, we can't check
         * in the test if the cache is used, but we can check that there's
          * at least no error when the same coord gets queried a second time.
          * Or we can use the debugger once to check that the correct code path
          * is taken.)
         */
        z = cache.findZone(65, 5);
        Assert.assertEquals("bb", z.getAttribute("-").toString());
        z = cache.findZone(95, 15);
        Assert.assertNull(z);
    }

    @Test
    public void testFindNearestZone() {
        TestZones zones = new TestZones("test");
        zones.zones.add(new TestZone("A", "aa", 10, 0, 20, 10));
        zones.zones.add(new TestZone("B", "bb", 50, 0, 70, 10));
        zones.zones.add(new TestZone("C", "cc", 80, 0, 90, 10));

        Zone z;
        ZonesQueryCache cache = new ZonesQueryCache(zones);
        z = cache.findNearestZone(15, 5, 4);
        Assert.assertEquals("aa", z.getAttribute("-").toString());
        z = cache.findNearestZone(15, 13, 4);
        Assert.assertEquals("aa", z.getAttribute("-").toString());
        z = cache.findNearestZone(24.5, 5, 4);
        Assert.assertNull(z);
        z = cache.findNearestZone(46.5, 5, 4);
        Assert.assertEquals("bb", z.getAttribute("-").toString());
        // repeat some queries to test the cache (also see note above)
        z = cache.findNearestZone(15, 13, 4);
        Assert.assertEquals("aa", z.getAttribute("-").toString());
        z = cache.findNearestZone(24.5, 5, 4);
        Assert.assertNull(z);

        // check that the non-nearest query still returns the correct result
        z = cache.findZone(15, 13);
        Assert.assertNull(z);
    }

    @Test
    public void testGetZone() {
        TestZones zones = new TestZones("test");
        zones.zones.add(new TestZone("A", "aa", 10, 0, 20, 10));
        zones.zones.add(new TestZone("B", "bb", 50, 0, 70, 10));
        zones.zones.add(new TestZone("C", "cc", 80, 0, 90, 10));

        Zone z;
        ZonesQueryCache cache = new ZonesQueryCache(zones);
        z = cache.getZone(Id.create("A", Zone.class));
        Assert.assertEquals("aa", z.getAttribute("-").toString());
        z = cache.getZone(Id.create("B", Zone.class));
        Assert.assertEquals("bb", z.getAttribute("-").toString());
    }

    private static class TestZones implements Zones {

        private final Id<Zones> id;
        private final List<Zone> zones = new ArrayList<>();

        public TestZones(String id) {
            this.id = Id.create(id, Zones.class);
        }

        @Override
        public Id<Zones> getId() {
            return null;
        }

        @Override
        public int size() {
            return this.zones.size();
        }

        @Override
        public Zone findZone(double x, double y) {
            Point pt = F.createPoint(new Coordinate(x, y));
            for (Zone z : this.zones) {
                if (z.contains(pt)) {
                    return z;
                }
            }
            return null;
        }

        @Override
        public Zone findNearestZone(double x, double y, double maxDistance) {
            Point pt = F.createPoint(new Coordinate(x, y));
            Zone nearest = null;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (Zone z : this.zones) {
                double distance = z.distance(pt);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    nearest = z;
                }
            }
            if (bestDistance < maxDistance) {
                return nearest;
            }
            return null;
        }

        @Override
        public Zone getZone(Id<Zone> id) {
            for (Zone z : this.zones) {
                if (z.getId().equals(id)) {
                    return z;
                }
            }
            return null;
        }
    }

    private static class TestZone implements Zone {
        private final Id<Zone> id;
        private final String attribute;
        private final Geometry geom;

        public TestZone(String id, String attribute, double x1, double y1, double x2, double y2) {
            this.id = Id.create(id, Zone.class);
            this.attribute = attribute;
            Coordinate topLeft = new Coordinate(Math.min(x1, x2), Math.max(y1, y2));
            Coordinate topRight = new Coordinate(Math.max(x1, x2), Math.max(y1, y2));
            Coordinate bottomRight = new Coordinate(Math.max(x1, x2), Math.min(y1, y2));
            Coordinate bottomLeft = new Coordinate(Math.min(x1, x2), Math.min(y1, y2));
            this.geom = F.createPolygon(new Coordinate[] {topLeft, topRight, bottomRight, bottomLeft, topLeft});
        }

        @Override
        public Id<Zone> getId() {
            return this.id;
        }

        @Override
        public Object getAttribute(String name) {
            return this.attribute;
        }

        @Override
        public Envelope getEnvelope() {
            return this.geom.getEnvelopeInternal();
        }

        @Override
        public boolean contains(Point pt) {
            return this.geom.contains(pt);
        }

        @Override
        public double distance(Point pt) {
            return this.geom.distance(pt);
        }
    }

}
