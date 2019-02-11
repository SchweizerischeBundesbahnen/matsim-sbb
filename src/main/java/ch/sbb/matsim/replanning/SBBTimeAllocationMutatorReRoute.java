package ch.sbb.matsim.replanning;

import org.matsim.core.config.groups.GlobalConfigGroup;
import org.matsim.core.config.groups.TimeAllocationMutatorConfigGroup;
import org.matsim.core.replanning.PlanStrategy;
import org.matsim.core.replanning.PlanStrategyImpl;
import org.matsim.core.replanning.modules.ReRoute;
import org.matsim.core.replanning.selectors.RandomPlanSelector;
import org.matsim.core.router.TripRouter;
import org.matsim.facilities.ActivityFacilities;

import javax.inject.Inject;
import javax.inject.Provider;

public class SBBTimeAllocationMutatorReRoute implements Provider<PlanStrategy> {
    @Inject private Provider<TripRouter> tripRouterProvider;
    @Inject private GlobalConfigGroup globalConfigGroup;
    @Inject private TimeAllocationMutatorConfigGroup timeAllocationMutatorConfigGroup;
    @Inject private ActivityFacilities activityFacilities;

    @Override
    public PlanStrategy get() {
        PlanStrategyImpl.Builder builder = new PlanStrategyImpl.Builder(new RandomPlanSelector()) ;
        builder.addStrategyModule(new SBBTimeAllocationMutator(this.timeAllocationMutatorConfigGroup, this.globalConfigGroup));
        builder.addStrategyModule(new ReRoute(this.activityFacilities, this.tripRouterProvider, this.globalConfigGroup));
        return builder.build();
    }
}
