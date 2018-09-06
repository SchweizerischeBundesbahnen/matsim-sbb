package ch.sbb.matsim.plans.discretizer;

import ch.sbb.matsim.analysis.matrices.Utils;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;
import org.opengis.feature.simple.SimpleFeature;

import java.util.*;

public class FacilityDiscretizer {

    private static final Logger log = Logger.getLogger(FacilityDiscretizer.class);

    private final ActivityFacilities facilities;
    private final Random random;
    private final Map<Integer, SimpleFeature> zonesById;
    private AbmZoneFacilities zoneData;

    public FacilityDiscretizer(ActivityFacilities facilities, Map<Integer, SimpleFeature> zonesById)    {
        this.facilities = facilities;
        this.random = new Random(20180906L);
        this.zonesById = zonesById;
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

    // TODO: would be better to return a facility instead of coord
    public Coord getRandomCoord(int zoneId, String type)  {
        AbmFacilities typesInZone = this.zoneData.getActivityTypes(zoneId);
        if (typesInZone == null)    {
            Coord coord = Utils.getRandomCoordinateInFeature(this.zonesById.get(zoneId), this.random);
            log.info("Did not find any facility in zone " + zoneId + " for type " + type + "..");
            return coord;
        }

        List<Id<ActivityFacility>> facilityList = this.zoneData.getActivityTypes(zoneId).getFacilitiesForType(type);

        // TODO: This should not happen!!! Not consistent with destination choice...
        if(facilityList == null)    {
            Coord coord = Utils.getRandomCoordinateInFeature(this.zonesById.get(zoneId), this.random);
            log.info("Did not find any facility in zone " + zoneId + " for type " + type + "...");
            return coord;
        }

        Id<ActivityFacility> fid = facilityList.get(this.random.nextInt(facilityList.size()));
        return this.facilities.getFacilities().get(fid).getCoord();
    }

    // TODO: get facility from weighted list
}
