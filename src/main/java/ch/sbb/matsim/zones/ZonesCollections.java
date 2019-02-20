package ch.sbb.matsim.zones;

import org.matsim.api.core.v01.Id;

import java.util.HashMap;
import java.util.Map;

/**
 * A data container to store one or more collection of zones.
 * Zones typically come from Shapefiles and have an Id.
 *
 * @author mrieser
 */
public class ZonesCollections {

    private final Map<Id<Zones>, Zones> zonesMap;

    public ZonesCollections() {
        this.zonesMap = new HashMap<>();
    }

    public Zones getZones(Id<Zones> id) {
        return this.zonesMap.get(id);
    }

    public void addZones(Zones zones) {
        this.zonesMap.put(zones.getId(), zones);
    }

    /**
     * @param zones the zones to be removed
     * @return <code>true</code> if the zones were part of the collection and were removed, <code>false</code> otherwise.
     */
    public boolean removeZones(Zones zones) {
        return this.zonesMap.remove(zones.getId()) != null;
    }

}
