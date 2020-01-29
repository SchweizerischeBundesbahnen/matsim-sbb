package ch.sbb.matsim.zones;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ZonesQueryCache implements Zones {

    private static final Zone NO_ZONE = new SimpleFeatureZone(null, null);
    private final Zones zones;
    private final Map<Coord, Zone> cache = new ConcurrentHashMap<>();
    private final Map<Coord, Zone> nearestCache = new ConcurrentHashMap<>();

    public ZonesQueryCache(Zones zones) {
        this.zones = zones;
    }

    @Override
    public Id<Zones> getId() {
        return this.zones.getId();
    }

    @Override
    public int size() {
        return this.zones.size();
    }

    private Zone zoneOrNull(Zone zone) {
        if (zone == NO_ZONE) {
            return null;
        }
        return zone;
    }

    @Override
    public Zone findZone(double x, double y) {
        Coord c = new Coord(x, y);
        Zone z = this.cache.get(c);
        if (z != null || this.cache.containsKey(c)) {
            return zoneOrNull(z);
        }
        z = this.zones.findZone(x, y);
        if (z == null) {
            z = NO_ZONE;
        }
        this.cache.put(c, z);
        return zoneOrNull(z);
    }

    @Override
    public Zone findNearestZone(double x, double y, double maxDistance) {
        Coord c = new Coord(x, y);
        Zone z = this.cache.get(c);
        if (z != null && z != NO_ZONE) {
            return zoneOrNull(z);
        }
        z = this.nearestCache.get(c);
        if (z != null || this.nearestCache.containsKey(c)) {
            return zoneOrNull(z);
        }
        z = this.zones.findZone(x, y);
        if (z == null) {
            this.cache.put(c, NO_ZONE);
        } else {
            this.cache.put(c, z);
            return z;
        }
        z = this.zones.findNearestZone(x, y, maxDistance);
        if (z == null) {
            this.nearestCache.put(c, NO_ZONE);
            return null;
        }
        this.nearestCache.put(c, z);
        return z;
    }

    @Override
    public Zone getZone(Id<Zone> id) {
        return this.zones.getZone(id);
    }
}
