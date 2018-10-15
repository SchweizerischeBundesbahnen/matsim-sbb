package ch.sbb.matsim.plans.discretizer;

import org.matsim.api.core.v01.Id;
import org.matsim.facilities.ActivityFacility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AbmFacilities {

    // TODO: add possibility to set weights... one possible solution would be adding a class 'AbmFacility'

    // Reminder: we might want to add some gravity forces as weight... e.g. for transit people, it is more
    // convenient to reach a facility close to a public transport station

    private Map<String, List<Id<ActivityFacility>>> facliitiestable = new HashMap<>();

    public AbmFacilities()  {
    }

    public void addTypeIfNotExists(String type)  {
        this.facliitiestable.putIfAbsent(type, new ArrayList<>());
    }

    public void addFacility(String type, Id<ActivityFacility> id)    {
        facliitiestable.get(type).add(id);
    }

    public List<Id<ActivityFacility>> getFacilitiesForType(String type) {
        return this.facliitiestable.get(type);
    }
}
