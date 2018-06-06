package ch.sbb.matsim.utils;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;

public class SBBPersonUtils {

    private final static Logger log = Logger.getLogger(SBBPersonUtils.class);

    private final static String ACT_TYPE_HOME = "home";

    public static Activity getHomeActivity(Person person)   {

        Plan plan = person.getSelectedPlan();
        PlanElement firstPlanElement = plan.getPlanElements().get(0);

        if (firstPlanElement instanceof Activity) {
            String type = ((Activity) firstPlanElement).getType();

            if (!type.equals(ACT_TYPE_HOME)) {
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
}
