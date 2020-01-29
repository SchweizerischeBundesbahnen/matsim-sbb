package ch.sbb.matsim.zones;

import com.google.common.collect.ImmutableList;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.index.SpatialIndex;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.geometry.geotools.MGC;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A collection of zones.
 *
 * @author mrieser
 */
public class ZonesImpl implements Zones {

    private final Id<Zones> id;
    private final List<Zone> zones = new ArrayList<>();
    private ConcurrentHashMap<Id<Zone>, Zone> lookup;
    private SpatialIndex qt = null;

    public ZonesImpl(Id<Zones> id) {
        this.id = id;
    }

    @Override
    public Id<Zones> getId() {
        return this.id;
    }

    public void add(Zone zone) {
        this.zones.add(zone);
        this.qt = null;
        this.lookup = null;
    }

    public void remove(Zone zone) {
        this.zones.remove(zone);
        this.qt = null;
        this.lookup = null;
    }

    public void clear() {
        this.zones.clear();
    }

    @Override
    public int size() {
        return this.zones.size();
    }

    @Override
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

    @Override
    public Zone findNearestZone(double x, double y, double maxDistance) {
        SpatialIndex qt = getSpatialIndex();
        Point pt = MGC.xy2Point(x, y);
        Envelope env = pt.getEnvelopeInternal();
        env.expandBy(maxDistance);
        List elements = qt.query(env);
        Zone nearestZone = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (Object o : elements) {
            Zone z = (Zone) o;
            double distance = z.distance(pt);
            if (distance < nearestDistance) {
                nearestZone = z;
                nearestDistance = distance;
                if (distance == 0.0) {
                    break;
                }
            }
        }
        if (nearestDistance <= maxDistance) {
            return nearestZone;
        }
        return null;
    }

    @Override
    public Zone getZone(Id<Zone> id) {
        return getLookupMap().get(id);
    }

    private SpatialIndex getSpatialIndex() {
        SpatialIndex qt = this.qt;
        if (qt == null) {
            qt = buildSpatialIndex();
        }
        return qt;
    }

    private synchronized SpatialIndex buildSpatialIndex() {
        SpatialIndex qt = this.qt;
        if (qt != null) {
            return qt;
        }
        qt = new Quadtree();
        for (Zone zone : this.zones) {
            Envelope envelope = zone.getEnvelope();
            qt.insert(envelope, zone);
        }
        this.qt = qt;
        return qt;
    }

    private Map<Id<Zone>, Zone> getLookupMap() {
        Map<Id<Zone>, Zone> map = this.lookup;
        if (map == null) {
            map = buildLookupMap();
        }
        return map;
    }

    private synchronized Map<Id<Zone>, Zone> buildLookupMap() {
        ConcurrentHashMap<Id<Zone>, Zone> map = this.lookup;
        if (map != null) {
            return map;
        }
        map = new ConcurrentHashMap<>();
        for (Zone zone : this.zones) {
            map.put(zone.getId(), zone);
        }
        this.lookup = map;
        return map;
    }

    public List<Zone> getZones() {
        return ImmutableList.copyOf(zones);
    }
}
