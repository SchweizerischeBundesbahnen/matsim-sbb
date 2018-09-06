package ch.sbb.matsim.plans.discretizer;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;

import java.util.List;
import java.util.Random;
import java.util.Set;

public class FacilityDiscretizer {

    private static final Logger log = Logger.getLogger(FacilityDiscretizer.class);

    private final ActivityFacilities facilities;
    private final Random random;
    private AbmZoneFacilities zoneData;

    public FacilityDiscretizer(ActivityFacilities facilities)    {
        this.facilities = facilities;
        this.random = MatsimRandom.getRandom();
        assignFacilitiesToZones();
    }

    private void assignFacilitiesToZones()   {
        AbmZoneFacilities zoneData = new AbmZoneFacilities();

        for(ActivityFacility fac : this.facilities.getFacilities().values())  {
            int zoneId = (int) this.facilities.getFacilityAttributes().getAttribute(fac.getId().toString(), "tZone");
            if(zoneId != -99)   {
                Set<String> options = fac.getActivityOptions().keySet();
                for(String opt: options)    {
                    zoneData.addFacility(zoneId, opt, fac.getId());
                }
            }
        }
        this.zoneData = zoneData;
    }

    public ActivityFacility getRandomFacility(int zoneId, String type)  {
        List<Id<ActivityFacility>> facilityList = this.zoneData.getActivityTypes(zoneId).getFacilitiesForType(type);

        /*
        if(facilityList.size() == 0)    {
            facilityList = this.zoneData.getActivityTypes(zoneId).getFacilitiesForType(DefaultActivityTypes.home);
        }
        */

        log.info("ZoneId: " + zoneId + "; Type: " + type);
        Id<ActivityFacility> fid = facilityList.get(this.random.nextInt(facilityList.size()));
        return this.facilities.getFacilities().get(fid);
    }

    // TODO: get facility from weighted list
}
