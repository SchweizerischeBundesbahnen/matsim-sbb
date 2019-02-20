package ch.sbb.matsim.zones;

import org.matsim.api.core.v01.Coord;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ZonesQueryCache {

    private final Zones zones;
    private final Map<Coord, Zone> cache = new ConcurrentHashMap<>();

    public ZonesQueryCache(Zones zones) {
        this.zones = zones;
    }

    public Zone findZone(double x, double y) {
        Coord c = new Coord(x, y);
        return this.cache.computeIfAbsent(c, k -> zones.findZone(x, y));
    }
}
