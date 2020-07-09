package ch.sbb.matsim.utils;

import ch.sbb.matsim.routing.pt.raptor.IntermodalAwareRouterModeIdentifier;
import java.util.List;
import java.util.Set;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.algorithms.PlanAlgorithm;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.CollectionUtils;

/**
 * @author jbischoff / SBB
 */
public class RemovePtRoutes {

    public static void main(String[] args) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        String popWithRoutes = args[0];
        String popWithoutRoutes = args[1];
        Set<String> modesToRemoveRoutes = CollectionUtils.stringToSet(args[2]);
        removePtRoutes(modesToRemoveRoutes, scenario, popWithRoutes, popWithoutRoutes);
    }

    public static void removePtRoutes(Set<String> modesToRemoveRoutes, Scenario scenario, String popWithRoutes, String popWithoutRoutes) {
        SBBTripsToLegsAlgorithm algorithm = new SBBTripsToLegsAlgorithm(new IntermodalAwareRouterModeIdentifier(scenario.getConfig()), modesToRemoveRoutes);
        StreamingPopulationReader streamingPopulationReader = new StreamingPopulationReader(scenario);
        StreamingPopulationWriter streamingPopulationWriter = new StreamingPopulationWriter();
        streamingPopulationWriter.startStreaming(popWithoutRoutes);
        streamingPopulationReader.addAlgorithm(person -> {
            PersonUtils.removeUnselectedPlans(person);
            Plan plan = person.getSelectedPlan();
            algorithm.run(plan);
            streamingPopulationWriter.run(person);
        });
        streamingPopulationReader.readFile(popWithRoutes);
        streamingPopulationWriter.closeStreaming();
    }

    static class SBBTripsToLegsAlgorithm implements PlanAlgorithm {

        private final MainModeIdentifier mainModeIdentifier;
        private final Set<String> modesToClear;

        public SBBTripsToLegsAlgorithm(final MainModeIdentifier mainModeIdentifier, Set<String> modesToClear) {
            this.mainModeIdentifier = mainModeIdentifier;
            this.modesToClear = modesToClear;
        }

        @Override
        public void run(final Plan plan) {
            final List<PlanElement> planElements = plan.getPlanElements();
            final List<Trip> trips = TripStructureUtils.getTrips(plan);

            for (Trip trip : trips) {
                final List<PlanElement> fullTrip =
                        planElements.subList(
                                planElements.indexOf(trip.getOriginActivity()) + 1,
                                planElements.indexOf(trip.getDestinationActivity()));
                final String mode = mainModeIdentifier.identifyMainMode(fullTrip);
                if (modesToClear.contains(mode)) {
                    fullTrip.clear();
                    fullTrip.add(PopulationUtils.createLeg(mode));
                    if (fullTrip.size() != 1) {
                        throw new RuntimeException(fullTrip.toString());
                    }
                }
            }
        }
    }
}