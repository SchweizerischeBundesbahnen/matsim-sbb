package ch.sbb.matsim.projects.bnfri;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.utils.SBBTripsToLegsAlgorithm;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.router.RoutingModeMainModeIdentifier;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitLine;

import java.util.Set;

public class RemoveSpecificRoute {
    static void main() {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile("/Users/joschka.bischoff/tmp/107_sc54.output_plans.xml.gz");
        Set<Id<TransitLine>> filteredLines = Set.of(Id.create("Simba_2023_009-D-11668", TransitLine.class), Id.create("Simba_2023_009-D-11662", TransitLine.class));
        SBBTripsToLegsAlgorithm tripsToLegsAlgorithm = new SBBTripsToLegsAlgorithm(new RoutingModeMainModeIdentifier(), Set.of(SBBModes.PT,SBBModes.PTSubModes.RAIL));
        int i = 0;
        for (var person : scenario.getPopulation().getPersons().values()) {
            PersonUtils.removeUnselectedPlans(person);
            Plan plan = person.getSelectedPlan();

           if( plan.getPlanElements().stream().anyMatch(
                    pe -> pe instanceof Leg leg
                            && leg.getRoute() instanceof TransitPassengerRoute
                            && filteredLines.contains(((TransitPassengerRoute) leg.getRoute()).getLineId())
            )
            ){               tripsToLegsAlgorithm.run(plan);
               i++;
         }
    }
        System.out.println(i);
        new PopulationWriter(scenario.getPopulation()).write("/Users/joschka.bischoff/tmp/filtered_plans_m51_10pct_meilen.xml.gz");
    }
}
