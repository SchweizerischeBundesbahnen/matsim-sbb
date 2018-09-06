package ch.sbb.matsim.plans.discretizer;

import org.matsim.api.core.v01.Id;
import org.matsim.facilities.ActivityFacility;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class AbmZoneFacilities {

    private Map<Integer, AbmFacilities> zonetable = new HashMap<>();

    public AbmZoneFacilities() {

    }

    public void addZoneIfNotExists(int zone)  {
        this.zonetable.putIfAbsent(zone, new AbmFacilities());
    }

    public AbmFacilities getActivityTypes(int zone) {
        return this.zonetable.get(zone);
    }

    public Set<Integer> getAllZones()  {
        return this.zonetable.keySet();
    }

    public void addFacility(int zone, String type, Id<ActivityFacility> fid)   {
        addZoneIfNotExists(zone);
        AbmFacilities facilities = getActivityTypes(zone);
        facilities.addTypeIfNotExists(type);
        facilities.addFacility(type, fid);
    }
}
