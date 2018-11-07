package ch.sbb.matsim.synpop.facilities;

import ch.sbb.matsim.synpop.zoneAggregator.Zone;
import ch.sbb.matsim.synpop.zoneAggregator.ZoneAggregator;
import org.apache.log4j.Logger;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;

import java.util.ArrayList;
import java.util.Collection;

public class ZoneIdAssigner {
    private final static Logger log = Logger.getLogger(ZoneIdAssigner.class);

    private ZoneAggregator<ActivityFacility> zoneAggregator;

    public ZoneIdAssigner(ZoneAggregator<ActivityFacility> zoneAggregator) {
        this.zoneAggregator = zoneAggregator;
    }

    public void addFacilitiesOfType(ActivityFacilities facilities, String type)    {
        Collection<ActivityFacility> workFacilities = facilities.getFacilitiesForActivityType(type).values();
        for (ActivityFacility activityFacility : workFacilities) {
            zoneAggregator.add(activityFacility, activityFacility.getCoord());
        }
    }

    public void assignIds() {
        log.info("adding tZone attribute to facilities");
        for (Zone<ActivityFacility> zone : zoneAggregator.getZones()) {
            ArrayList<ActivityFacility> facilites = zone.getData();
            for (ActivityFacility activityFacility : facilites) {
                activityFacility.getAttributes().putAttribute("tZone", zone.getId());
            }
        }
    }

    public void checkForMissingIds(ActivityFacilities facilities)    {
        int counter = 0;
        for (ActivityFacility activityFacility : facilities.getFacilities().values()) {
            if(activityFacility.getAttributes().getAttribute("tZone") == null)  {
                activityFacility.getAttributes().putAttribute("tZone", -1);
                counter++;
            }
        }
        log.info(counter + " elements not in shape file");
    }
}
