package ch.sbb.matsim.replanning;

import ch.sbb.matsim.config.variables.SBBActivities;
import org.matsim.core.config.groups.GlobalConfigGroup;
import org.matsim.core.config.groups.TimeAllocationMutatorConfigGroup;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.algorithms.PlanAlgorithm;
import org.matsim.core.replanning.modules.AbstractMultithreadedModule;

/**
 * THIS IS A COPY of the default TimeAllocationMutator.
 * It allows to create a custom instance of PlanAlgorithm.
 * @author PM / SBB
 *
 */
public class SBBTimeAllocationMutator extends AbstractMultithreadedModule {

    private final double mutationRange;

    public SBBTimeAllocationMutator(TimeAllocationMutatorConfigGroup timeAllocationMutatorConfigGroup, GlobalConfigGroup globalConfigGroup) {
        super(globalConfigGroup);
        this.mutationRange = timeAllocationMutatorConfigGroup.getMutationRange();
    }

    @Override
    public PlanAlgorithm getPlanAlgoInstance() {
        return new SBBTripPlanMutateTimeAllocation(SBBActivities.stageActivitiesTypes,
                this.mutationRange, MatsimRandom.getLocalInstance());
    }
}