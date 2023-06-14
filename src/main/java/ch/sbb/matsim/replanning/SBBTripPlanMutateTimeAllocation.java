package ch.sbb.matsim.replanning;

import ch.sbb.matsim.config.variables.Variables;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.population.algorithms.PlanAlgorithm;
import org.matsim.core.utils.misc.OptionalTime;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * SBB version of the default TripPlanMutateTimeAllocation. It strongly relies on the person attribute initialActivityEndTimes and works with this attribute only. In contrast the original mutation
 * strategy, the SBBTripPlanMutateTimeAllocation allows time allocation from initial activity end times only.
 *
 * @author PM / SBB
 */
public final class SBBTripPlanMutateTimeAllocation implements PlanAlgorithm {

	private final double mutationRange;
	private final int minimumMutation;
	private final Random random;

	public SBBTripPlanMutateTimeAllocation(final double mutationRange, final int minimumMutation, final Random random) {
		this.mutationRange = mutationRange;
		this.random = random;
		this.minimumMutation = minimumMutation;
	}

	@Override
	public void run(final Plan plan) {
		mutatePlan(plan);
	}

	private void mutatePlan(final Plan plan) {
		List<OptionalTime> initialEndTimes = Stream
				.of(((String) plan.getPerson().getAttributes().getAttribute(Variables.INIT_END_TIMES)).split("_"))
				.map(s -> s.equals(Variables.NO_INIT_END_TIME) ? OptionalTime.undefined() : OptionalTime.defined(Double.parseDouble(s)))
				.collect(Collectors.toList());

		double now = 0;
		int i = 0;
		Activity lastAct = (Activity) plan.getPlanElements().listIterator(plan.getPlanElements().size()).previous();

		// apply mutation to all activities except the last home activity
		for (PlanElement pe : plan.getPlanElements()) {

			if (pe instanceof Activity) {
				Activity act = (Activity) pe;

				// skip outside activities
				if ("outside".equals(act.getType())) {
					continue;
				}

				// handle first activity
				if (i == 0) {
					// set start to midnight
					act.setStartTime(now);
					// mutate the end time of the first activity
					OptionalTime initialEndTime = initialEndTimes.get(i);
					act.setEndTime(mutateTime(initialEndTime, mutationRange));
					// calculate resulting duration
					act.setMaximumDuration(act.getEndTime().seconds() - act.getStartTime().seconds());
					// move now pointer
					now += act.getEndTime().seconds();
					i++;
				}
				// handle middle activities
				else if (act != lastAct) {
					// assume that there will be no delay between arrival time and activity start time
					if (!act.getType().endsWith("interaction")) {
						act.setStartTime(now);
						if (act.getEndTime().isUndefined()) {
							throw new IllegalStateException("Can not mutate activity end time because it is not set for Person: " + plan.getPerson().getId());
						}
						OptionalTime initialEndTime = initialEndTimes.get(i);
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
					act.setMaximumDurationUndefined();
					act.setEndTimeUndefined();
				}

			} else {
				Leg leg = (Leg) pe;
				// assume that there will be no delay between end time of previous activity and departure time
				leg.setDepartureTime(now);
				// let duration untouched. if defined add it to now
				if (leg.getTravelTime().isDefined()) {
					now += leg.getTravelTime().seconds();
				}
				final double arrTime = now;
				// set planned arrival time accordingly
				leg.setTravelTime(arrTime - leg.getDepartureTime().seconds());
			}
		}
	}

	private double mutateTime(final OptionalTime t, final double mutationRange) {
		double newTime;
		if (t.isDefined()) {
			int mutation = minimumMutation * ((int) ((random.nextDouble() * 2.0 - 1.0) * mutationRange) / minimumMutation);
			newTime = t.seconds() + mutation;
			if (newTime <= 0) {
				newTime = 1;
			}
			if (newTime > 29 * 3600) {
				newTime = 29.0 * 3600;
			}
		} else {
			newTime = this.random.nextInt(24 * 3600);
		}
		return newTime;
	}
}