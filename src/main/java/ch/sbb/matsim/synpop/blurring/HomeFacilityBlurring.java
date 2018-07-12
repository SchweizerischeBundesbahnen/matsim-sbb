package ch.sbb.matsim.synpop.blurring;

import ch.sbb.matsim.synpop.zoneAggregator.Zone;
import ch.sbb.matsim.synpop.zoneAggregator.ZoneAggregator;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;

public class HomeFacilityBlurring {
    private final static Logger log = Logger.getLogger(HomeFacilityBlurring.class);

    private ZoneAggregator<ActivityFacility> zoneAggregator;

    public HomeFacilityBlurring(ActivityFacilities facilities, String shapefile) {

        Collection<ActivityFacility> activityFacilities = facilities.getFacilitiesForActivityType("home").values();
        zoneAggregator = new ZoneAggregator<>(shapefile);

        for (ActivityFacility activityFacility : activityFacilities) {
            zoneAggregator.add(activityFacility, activityFacility.getCoord());
        }

        for (Zone<ActivityFacility> zone : zoneAggregator.getZones()) {
            this.blurZone(zone);
        }

    }

    public ZoneAggregator<ActivityFacility> getZoneAggregator() {
        return zoneAggregator;
    }

    private void blurZone(Zone<ActivityFacility> zone) {
        ArrayList<ActivityFacility> facilites = zone.getData();
        ArrayList<Coord> coordinates = new ArrayList<>();

        for (ActivityFacility activityFacility : facilites) {
            coordinates.add(activityFacility.getCoord());
        }

        int max = 100;

        for (ActivityFacility activityFacility : facilites) {
            Coord oldCoord = activityFacility.getCoord();
            Coord newCoord;
            int i = 0;
            do {
                i++;
                newCoord = coordinates.get(new Random().nextInt(coordinates.size()));
            } while (oldCoord.equals(newCoord) && i < max);

            if(i==max){
                log.error("Could not swap coordinates");
            }

            coordinates.remove(newCoord);
            activityFacility.setCoord(newCoord);
        }
    }

}
