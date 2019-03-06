package ch.sbb.matsim.zones;

import org.matsim.api.core.v01.Coord;

import java.util.HashMap;
import java.util.Map;

public class ZonesQueryCache {

    private final Zones zones;
    private final Map<Coord, Zone> cache = new HashMap<>();

    public ZonesQueryCache(Zones zones) {
        this.zones = zones;
    }

    public Zone findZone(double x, double y) {
        Coord c = new Coord(x, y);
        if (this.cache.containsKey(c)) {
            return this.cache.get(c);
        }
        Zone z = this.zones.findZone(x, y);
        this.cache.put(c, z);
        return z;
    }
}
