package ch.sbb.matsim.zones;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;

import java.util.HashMap;
import java.util.Map;

public class ZonesQueryCache implements Zones {

    private final Zones zones;
    private final Map<Coord, Zone> cache = new HashMap<>();
    private final Map<Coord, Zone> nearestCache = new HashMap<>();

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

    @Override
    public Zone findZone(double x, double y) {
        Coord c = new Coord(x, y);
        Zone z = this.cache.get(c);
        if (z != null || this.cache.containsKey(c)) {
            return z;
        }
        z = this.zones.findZone(x, y);
        this.cache.put(c, z);
        return z;
    }

    @Override
    public Zone findNearestZone(double x, double y, double maxDistance) {
        Coord c = new Coord(x, y);
        Zone z = this.cache.get(c);
        if (z != null) {
            return z;
        }
        z = this.nearestCache.get(c);
        if (z != null || this.nearestCache.containsKey(c)) {
            return z;
        }
        z = this.zones.findZone(x, y);
        this.cache.put(c, z);
        if (z != null) {
            return z;
        }
        z = this.zones.findNearestZone(x, y, maxDistance);
        this.nearestCache.put(c, z);
        return z;

    }
}
