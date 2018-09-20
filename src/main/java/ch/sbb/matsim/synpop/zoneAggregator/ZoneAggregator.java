package ch.sbb.matsim.synpop.zoneAggregator;

import ch.sbb.matsim.analysis.LocateAct;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.misc.Counter;
import org.opengis.feature.simple.SimpleFeature;

import java.util.Collection;
import java.util.HashMap;

public class ZoneAggregator<T> {

    private final String attribute;
    private HashMap<Integer, Zone<T>> zones;
    private LocateAct locateAct;
    private final static Logger log = Logger.getLogger(ZoneAggregator.class);
    private final Counter counter;

    public ZoneAggregator(String shapefile, String attribute) {
        locateAct = new LocateAct(shapefile);
        this.attribute = attribute;
        this.zones = new HashMap<>();
        this.counter = new Counter("Zone aggregator #");
    }

    public Collection<Zone<T>> getZones() {
        return this.zones.values();
    }

    public void add(T element, Coord coord) {
        this.counter.incCounter();
        SimpleFeature feature = this.locateAct.getZone(coord);
        Zone<T> zone;

        if (feature == null) {
            log.info(element + " is not in shapefile");
            zone = new Zone<T>(-1);
            this.zones.put(-1, zone);
        } else {
            int zoneId = (int) Double.parseDouble(feature.getAttribute(this.attribute).toString());
            if (!zones.containsKey(zoneId)) {
                zone = new Zone<T>(zoneId);
                this.zones.put(zoneId, zone);
            } else {
                zone = this.zones.get(zoneId);
            }
        }
        zone.addItem(element);
    }

}
