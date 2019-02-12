package ch.sbb.matsim.replanning;

import ch.sbb.matsim.config.variables.SBBActivities;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.population.algorithms.PlanAlgorithm;
import org.matsim.core.router.StageActivityTypes;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.utils.misc.Time;

import java.util.List;
import java.util.Random;


public final class SBBTripPlanMutateTimeAllocation implements PlanAlgorithm {

    private final  StageActivityTypes stageActivities;
    private final double mutationRange;
    private final Random random;

    public SBBTripPlanMutateTimeAllocation(final StageActivityTypes stageActivities, final double mutationRange,
                                           final Random random) {
        this.stageActivities = stageActivities;
        this.mutationRange = mutationRange;
        this.random = random;
    }

    @Override
    public void run(final Plan plan) {
        mutatePlan(plan);
    }

    private void mutatePlan(final Plan plan) {
        String[] initialEndTimes = plan.getPerson().getAttributes().getAttribute("initialActivityEndTimes").toString().split("_");

        double now = 0;
        int i = 0;

        List<Activity> actList = TripStructureUtils.getActivities(plan, SBBActivities.stageActivitiesTypes);

        // apply mutation to all activities except the last home activity
        for (PlanElement pe : plan.getPlanElements()) {

            if (pe instanceof Activity) {
                Activity act = (Activity)pe;

                // handle first activity
                if (i == 0) {
                    // set start to midnight
                    act.setStartTime(now);
                    // mutate the end time of the first activity
                    double initialEndTime = Double.parseDouble(initialEndTimes[i]);
                    act.setEndTime(mutateTime(initialEndTime, mutationRange));
                    // calculate resulting duration
                    act.setMaximumDuration(act.getEndTime() - act.getStartTime());
                    // move now pointer
                    now += act.getEndTime();
                    i++;
                }

                // handle middle activities
                else if (i < (actList.size() - 1)) {
                    // assume that there will be no delay between arrival time and activity start time
                    act.setStartTime(now);
                    if (!this.stageActivities.isStageActivity(act.getType())) {
                        if (act.getEndTime() == Time.getUndefinedTime()) {
                            throw new IllegalStateException("Can not mutate activity end time because it is not set for Person: " + plan.getPerson().getId());
                        }
                        double initialEndTime = Double.parseDouble(initialEndTimes[i]);
                        double newEndTime = mutateTime(initialEndTime, mutationRange);
                        if (newEndTime < now) {
                            newEndTime = now;
                        }
                        act.setEndTime(newEndTime);
                        now = newEndTime;
                        i++;
                    }
                }

                // handle last activity
                else {
                    // assume that there will be no delay between arrival time and activity start time
                    act.setStartTime(now);
                    // invalidate duration and end time because the plan will be interpreted 24 hour wrap-around
                    act.setMaximumDuration(Time.getUndefinedTime());
                    act.setEndTime(Time.getUndefinedTime());
                }

            } else {
                Leg leg = (Leg) pe;
                // assume that there will be no delay between end time of previous activity and departure time
                leg.setDepartureTime(now);
                // let duration untouched. if defined add it to now
                if (leg.getTravelTime() != Time.getUndefinedTime()) {
                    now += leg.getTravelTime();
                }
                final double arrTime = now;
                // set planned arrival time accordingly
                leg.setTravelTime( arrTime - leg.getDepartureTime() );
            }
        }
    }

    private double mutateTime(final double time, final double mutationRange) {
        double t = time;
        if (t != Time.getUndefinedTime()) {
            t = t + (int)((this.random.nextDouble() * 2.0 - 1.0) * mutationRange);
            if (t < 0) t = 0;
            if (t > 24*3600) t = 24.0 * 3600;
        }
        // PM: This should never happen...
        else {
            t = this.random.nextInt(24*3600);
        }
        return t;
    }
}