package ch.sbb.matsim.synpop.zoneAggregator;

import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import ch.sbb.matsim.zones.ZonesQueryCache;
import java.util.Collection;
import java.util.HashMap;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.misc.Counter;

public class ZoneAggregator<T> {

	private final static Logger log = Logger.getLogger(ZoneAggregator.class);
	private final String shapeAttr;
	private final Counter counter;
	private HashMap<Integer, AggregationZone<T>> zones;
	private Zones allZones;

	public ZoneAggregator(String shapefile, String shapeAttr) {
		allZones = new ZonesQueryCache(ZonesLoader.loadZones("zones", shapefile, null));
		this.shapeAttr = shapeAttr;
		this.zones = new HashMap<>();
		this.counter = new Counter("Zone aggregator #");
	}

	public Collection<AggregationZone<T>> getZones() {
		return this.zones.values();
	}

	public void add(T element, Coord coord) {
		this.counter.incCounter();
		Zone z = this.allZones.findZone(coord.getX(), coord.getY());
		AggregationZone<T> zone;

		int zoneId;
		if (z == null) {
			log.info(element + " is not in shapefile");
			zoneId = -1;
		} else {
			zoneId = (int) Double.parseDouble(z.getAttribute(this.shapeAttr).toString());
		}
		zone = this.zones.computeIfAbsent(zoneId, k -> new AggregationZone<T>(zoneId));
		zone.addItem(element);
	}

}
