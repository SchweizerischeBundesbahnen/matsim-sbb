package ch.sbb.matsim.zones;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.index.SpatialIndex;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.geometry.geotools.MGC;

import java.util.ArrayList;
import java.util.List;

/**
 * A collection of zones.
 *
 * @author mrieser
 */
public class Zones {

    private final Id<Zones> id;
    private final List<Zone> zones = new ArrayList<>();
    private SpatialIndex qt = null;

    public Zones(Id<Zones> id) {
        this.id = id;
    }

    public Id<Zones> getId() {
        return this.id;
    }

    public void add(Zone zone) {
        this.zones.add(zone);
        this.qt = null;
    }

    public void remove(Zone zone) {
        this.zones.remove(zone);
        this.qt = null;
    }

    public void clear() {
        this.zones.clear();
    }

    public int size() {
        return this.zones.size();
    }

    public Zone findZone(double x, double y) {
        SpatialIndex qt = getSpatialIndex();
        Point pt = MGC.xy2Point(x, y);
        List elements = qt.query(pt.getEnvelopeInternal());
        for (Object o : elements) {
            Zone z = (Zone) o;
            if (z.contains(pt)) {
                return z;
            }
        }
        return null;
    }

    private SpatialIndex getSpatialIndex() {
        SpatialIndex qt = this.qt;
        if (qt == null) {
            qt = buildSpatialIndex();
            this.qt = qt;
        }
        return qt;
    }

    private synchronized SpatialIndex buildSpatialIndex() {
        SpatialIndex qt = new Quadtree();
        for (Zone zone : this.zones) {
            Envelope envelope = zone.getEnvelope();
            qt.insert(envelope, zone);
        }
        return qt;
    }
}
