package ch.sbb.matsim.utils;

import ch.sbb.matsim.routing.pt.raptor.IntermodalAwareRouterModeIdentifier;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.algorithms.PersonAlgorithm;
import org.matsim.core.population.algorithms.TripsToLegsAlgorithm;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;

/**
 * @author jbischoff / SBB
 */
public class RemoveRoutesAndLinksFromPlans {
    public static void main(String[] args) {
        StreamingPopulationReader spr = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
        StreamingPopulationWriter spw = new StreamingPopulationWriter();
        TripsToLegsAlgorithm tripsToLegsAlgorithm = new TripsToLegsAlgorithm(new IntermodalAwareRouterModeIdentifier(ConfigUtils.createConfig()));
        spw.startStreaming(args[1]);
        spr.addAlgorithm(new PersonAlgorithm() {
            @Override
            public void run(Person person) {
                Plan plan = person.getSelectedPlan();
                tripsToLegsAlgorithm.run(plan);
                TripStructureUtils.getActivities(plan, TripStructureUtils.StageActivityHandling.ExcludeStageActivities).forEach(activity -> {
                    activity.setLinkId(null);

                });
                spw.run(person);
            }
        });
        spr.readFile(args[0]);
        spw.closeStreaming();


    }
}
