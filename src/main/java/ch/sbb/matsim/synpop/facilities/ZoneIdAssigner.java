package ch.sbb.matsim.synpop.facilities;

import ch.sbb.matsim.synpop.zoneAggregator.AggregationZone;
import ch.sbb.matsim.synpop.zoneAggregator.ZoneAggregator;
import java.util.ArrayList;
import java.util.Collection;
import org.apache.log4j.Logger;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;

public class ZoneIdAssigner {

	private final static Logger log = Logger.getLogger(ZoneIdAssigner.class);

	private ZoneAggregator<ActivityFacility> zoneAggregator;

	public ZoneIdAssigner(ZoneAggregator<ActivityFacility> zoneAggregator) {
		this.zoneAggregator = zoneAggregator;
	}

	public void addFacilitiesOfType(ActivityFacilities facilities, String type) {
		Collection<ActivityFacility> workFacilities = facilities.getFacilitiesForActivityType(type).values();
		for (ActivityFacility activityFacility : workFacilities) {
			zoneAggregator.add(activityFacility, activityFacility.getCoord());
		}
	}

	public void assignIds(String attributeName) {
		log.info("adding " + attributeName + " attribute to facilities");
		for (AggregationZone<ActivityFacility> zone : zoneAggregator.getZones()) {
			ArrayList<ActivityFacility> facilites = zone.getData();
			for (ActivityFacility activityFacility : facilites) {
				activityFacility.getAttributes().putAttribute(attributeName, zone.getId());
			}
		}
	}

	public void checkForMissingIds(ActivityFacilities facilities, String attributeName) {
		int counter = 0;
		for (ActivityFacility activityFacility : facilities.getFacilities().values()) {
			if (activityFacility.getAttributes().getAttribute(attributeName) == null) {
				activityFacility.getAttributes().putAttribute(attributeName, -1);
				counter++;
			}
		}
		log.info(counter + " elements not in shape file");
	}
}
