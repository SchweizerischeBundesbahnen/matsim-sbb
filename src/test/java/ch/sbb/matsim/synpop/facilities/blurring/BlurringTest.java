package ch.sbb.matsim.synpop.facilities.blurring;

import ch.sbb.matsim.synpop.blurring.HomeFacilityBlurring;
import ch.sbb.matsim.synpop.zoneAggregator.AggregationZone;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.facilities.*;
import org.matsim.testcases.MatsimTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class BlurringTest {
    private final static Logger log = Logger.getLogger(BlurringTest.class);

    private String shapefile = "src/test/resources/shapefiles/AccessTime/accesstime_zone.SHP";
    private Coord sion = new Coord(593997, 120194);
    private Coord sion_2 = new Coord(593998, 120195);
    private Coord sion_3 = new Coord(593999, 120196);

    @Rule
    public MatsimTestUtils utils = new MatsimTestUtils();

    @Test
    public final void test() {

        ActivityFacilities activityFacilities = FacilitiesUtils.createActivityFacilities();

        ActivityFacilitiesFactory factory = activityFacilities.getFactory();
        ActivityOption option = factory.createActivityOption("home");

        Id.create("", ActivityFacility.class);

        List<Coord> coords = Arrays.asList(sion, sion, sion, sion_2, sion_2, sion_3);
        List<Id> ids = new ArrayList<>();
        int i = 0;
        for (Coord coord : coords) {
            Id id = Id.create(i, ActivityFacility.class);
            ids.add(id);
            ActivityFacility facility = factory.createActivityFacility(id, coord);
            facility.addActivityOption(option);
            activityFacilities.addActivityFacility(facility);
            i++;
        }

        activityFacilities.addActivityFacility(factory.createActivityFacility(Id.create(99, ActivityFacility.class), new Coord(99, 99)));

        HomeFacilityBlurring homeFacilityBlurring = new HomeFacilityBlurring(activityFacilities, shapefile, "ID");

        for(int j=0; j<i; j++){
            activityFacilities.getFacilities().get(ids.get(j));
            log.info(coords.get(j)+"\t"+activityFacilities.getFacilities().get(ids.get(j)).getCoord());
            Assert.assertNotEquals(coords.get(j), activityFacilities.getFacilities().get(ids.get(j)).getCoord());
        }

        for(AggregationZone<ActivityFacility> zone: homeFacilityBlurring.getZoneAggregator().getZones()){
            log.info(zone.count());
        }
    }
}
