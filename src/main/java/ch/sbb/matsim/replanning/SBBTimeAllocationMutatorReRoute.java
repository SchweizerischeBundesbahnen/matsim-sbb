package ch.sbb.matsim.replanning;

import ch.sbb.matsim.config.SBBReplanningConfigGroup;
import org.matsim.core.config.groups.GlobalConfigGroup;
import org.matsim.core.config.groups.TimeAllocationMutatorConfigGroup;
import org.matsim.core.replanning.PlanStrategy;
import org.matsim.core.replanning.PlanStrategyImpl;
import org.matsim.core.replanning.modules.ReRoute;
import org.matsim.core.replanning.selectors.RandomPlanSelector;
import org.matsim.core.router.TripRouter;
import org.matsim.core.utils.timing.TimeInterpretation;
import org.matsim.facilities.ActivityFacilities;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

/**
 * THIS IS A COPY of the default TimeAllocationMutatorReRoute module. The only modification is a custom TimeAllocationStrategy which is called SBBTimeAllocationMutator
 *
 * @author PM / SBB
 */
public class SBBTimeAllocationMutatorReRoute implements Provider<PlanStrategy> {

    @Inject
    private Provider<TripRouter> tripRouterProvider;
    @Inject
    private GlobalConfigGroup globalConfigGroup;
    @Inject
    private TimeInterpretation timeInterpretation;
    @Inject
    private TimeAllocationMutatorConfigGroup timeAllocationMutatorConfigGroup;
    @Inject
    private ActivityFacilities activityFacilities;
    @Inject
    private SBBReplanningConfigGroup replanningConfigGroup;

    @Override
    public PlanStrategy get() {
        PlanStrategyImpl.Builder builder = new PlanStrategyImpl.Builder(new RandomPlanSelector<>());
        builder.addStrategyModule(new SBBTimeAllocationMutator(this.timeAllocationMutatorConfigGroup, this.globalConfigGroup, this.replanningConfigGroup));
        builder.addStrategyModule(new ReRoute(this.activityFacilities, this.tripRouterProvider, this.globalConfigGroup, this.timeInterpretation));
        return builder.build();
    }
}
