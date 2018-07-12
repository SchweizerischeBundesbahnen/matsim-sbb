package ch.sbb.matsim.synpop.zoneAggregator;

import ch.sbb.matsim.analysis.LocateAct;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.opengis.feature.simple.SimpleFeature;

import java.util.Collection;
import java.util.HashMap;

public class ZoneAggregator<T> {

    private HashMap<String, Zone<T>> zones;
    private LocateAct locateAct;
    private final static Logger log = Logger.getLogger(ZoneAggregator.class);

    public ZoneAggregator(String shapefile) {
        locateAct = new LocateAct(shapefile);
        this.zones = new HashMap<>();
    }

    public Collection<Zone<T>> getZones() {
        return this.zones.values();
    }

    public void add(T element, Coord coord) {
        SimpleFeature feature = this.locateAct.getZone(coord);
        Zone<T> zone;

        if (feature == null) {
            log.info(element+" is not in shapefile");
            zone = new Zone<T>(null);
            this.zones.put(zone.getId(), zone);
        } else {
            if (!zones.containsKey(feature.getID())) {
                zone = new Zone<T>(feature);
                this.zones.put(zone.getId(), zone);
            }
            else{
                zone = this.zones.get(feature.getID());
            }
        }
        zone.addItem(element);
    }

}
