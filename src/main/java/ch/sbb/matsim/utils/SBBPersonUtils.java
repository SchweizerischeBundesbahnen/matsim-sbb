package ch.sbb.matsim.utils;

import ch.sbb.matsim.config.variables.SBBActivities;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;

public class SBBPersonUtils {

    private final static Logger log = Logger.getLogger(SBBPersonUtils.class);

    public static Activity getHomeActivity(Person person)   {
        Plan plan = person.getSelectedPlan();
        if(plan == null)   {
            log.warn("person " + person.getId().toString() + " has no selected plan!");
            return null;
        }

        if(plan.getPlanElements().isEmpty())   {
            log.warn("selected plan of person " + person.getId().toString() + " has no plan elements!");
            return null;
        }
        PlanElement firstPlanElement = plan.getPlanElements().get(0);

        if (firstPlanElement instanceof Activity) {
            String type = ((Activity) firstPlanElement).getType();

            if (!type.equals(SBBActivities.home)) {
                log.warn("first plan element of person " + person.getId().toString() + " is not of type home");
                return null;
            } else {
                return (Activity) firstPlanElement;
            }
        } else {
            log.warn("first planelement of person " + person.getId().toString() + " is not an activity. Something is wrong" +
                    "with this plan");
            return null;
        }
    }

    public static ActivityFacility getHomeFacility(Person person, ActivityFacilities facilities)   {
        Activity homeActivity = getHomeActivity(person);
        return facilities.getFacilities().get(homeActivity.getFacilityId());
    }
}
