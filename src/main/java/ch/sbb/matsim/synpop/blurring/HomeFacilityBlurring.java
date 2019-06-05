package ch.sbb.matsim.synpop.blurring;

import ch.sbb.matsim.synpop.zoneAggregator.AggregationZone;
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

    private static Random random = new Random(20180806);
    private ZoneAggregator<ActivityFacility> zoneAggregator;

    public HomeFacilityBlurring(ActivityFacilities facilities, String shapefile, String attribute) {

        Collection<ActivityFacility> activityFacilities = facilities.getFacilitiesForActivityType("home").values();
        zoneAggregator = new ZoneAggregator<>(shapefile, attribute);

        for (ActivityFacility activityFacility : activityFacilities) {
            zoneAggregator.add(activityFacility, activityFacility.getCoord());
        }

        int maxTries = 100;
        for (AggregationZone<ActivityFacility> zone : zoneAggregator.getZones()) {
            for (int i = 0; i <= maxTries; i++) {
                try {
                    this.blurZone(zone);
                    break;
                } catch (BlurringSwapException e) {
                    if (i == maxTries) {
                        log.info("Zone can not be blurred.");
                        for (ActivityFacility facility : zone.getData()) {
                            log.info(facility.getCoord());
                        }
                    }
                }


            }
        }
    }

    public ZoneAggregator<ActivityFacility> getZoneAggregator() {
        return zoneAggregator;
    }

    private void blurZone(AggregationZone<ActivityFacility> zone) {

        ArrayList<ActivityFacility> facilites = zone.getData();
        ArrayList<Coord> coordinates = new ArrayList<>();

        log.info("Blurring zone " + String.valueOf(zone.getId()) + " with " + facilites.size() + " facilities");

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
                newCoord = coordinates.get(random.nextInt(coordinates.size()));
            } while (oldCoord.equals(newCoord) && i < max);

            if (i == max) {
                throw new BlurringSwapException();
            }

            coordinates.remove(newCoord);
            activityFacility.setCoord(newCoord);
        }
    }

}
