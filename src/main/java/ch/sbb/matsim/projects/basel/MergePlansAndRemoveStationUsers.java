package ch.sbb.matsim.projects.basel;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.utils.SBBTripsToLegsAlgorithm;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.router.RoutingModeMainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.HashSet;
import java.util.List;

public class MergePlansAndRemoveStationUsers {

    public static void main(String[] args) {
        String inputPlans1 = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220412_Basel_2050\\sim\\0.1-v100-10pct\\output\\v100.1.output_plans.xml.gz";
//        String inputPlans2 = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220412_Basel_2050\\sim\\0.2-v100-50pct\\output_slice1\\v100.2.output_plans.xml.gz";
        List<String> plans = List.of(inputPlans1);
        List<Id<Link>> stopFacilityIds = List.of(Id.create("pt_19054489", Link.class));
        String outputFile = "C:\\devsbb\\v100.1.output_plans.xml.gz";


        StreamingPopulationWriter spw = new StreamingPopulationWriter();
        spw.startStreaming(outputFile);
        SBBTripsToLegsAlgorithm tripsToLegsAlgorithm = new SBBTripsToLegsAlgorithm(new RoutingModeMainModeIdentifier(), new HashSet<>(SBBModes.PT_PASSENGER_MODES));
        for (String s : plans) {
            Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
            StreamingPopulationReader spr = new StreamingPopulationReader(scenario);
            spr.addAlgorithm(p ->
            {
                PersonUtils.removeUnselectedPlans(p);
                Plan plan = p.getSelectedPlan();
                boolean removeRoutes = TripStructureUtils.getLegs(plan).stream()
                        .map(leg -> leg.getRoute())
                        .anyMatch(route -> (stopFacilityIds.contains(route.getStartLinkId()) || stopFacilityIds.contains(route.getEndLinkId())));
                if (removeRoutes) {
                    tripsToLegsAlgorithm.run(plan);
                }
                spw.run(p);

            });
            spr.readFile(s);
        }
        spw.closeStreaming();
    }
}
