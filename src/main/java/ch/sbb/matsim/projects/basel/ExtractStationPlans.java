package ch.sbb.matsim.projects.basel;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.List;

public class ExtractStationPlans {


    public ExtractStationPlans(List<String> plans, int factor, List<Id<Link>> stopFacilityIds, String extractedTrips) {
        run(plans, factor, stopFacilityIds, extractedTrips);

    }

    public static void main(String[] args) {
        String inputPlans1 = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220412_Basel_2050\\sim\\0.6-v100.1-10pct\\output\\v100.1.output_plans.xml.gz";
        String inputPlans2 = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220412_Basel_2050\\sim\\0.2-v100-50pct\\output_slice1\\v100.2.output_plans.xml.gz";
        String extractedTrips = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220412_Basel_2050\\sim\\0.6-v100.1-10pct\\reporting\\eingaenge\\outputplans-mitte.xml.gz";
        List<String> plans = List.of(inputPlans1);

        List<Id<Link>> stopFacilityIds = List.of(Id.createLinkId("pt_19054489"));
        int factor = 10;
        new ExtractStationPlans(plans, factor, stopFacilityIds, extractedTrips);


    }

    private void run(List<String> plans, int factor, List<Id<Link>> stopFacilityLinkId, String extractedTrips) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Population extractedPopulation = scenario.getPopulation();
        PopulationFactory f = extractedPopulation.getFactory();
        for (String s : plans) {
            Scenario readScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
            StreamingPopulationReader spr = new StreamingPopulationReader(readScenario);
            spr.addAlgorithm(person -> {
                int count = 0;
                Plan plan = person.getSelectedPlan();
                for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(plan)) {
                    boolean touchesStop = trip.getTripElements()
                            .stream()
                            .filter(planElement -> planElement instanceof Activity)
                            .anyMatch(planElement -> stopFacilityLinkId.contains(((Activity) planElement).getLinkId()));
                    if (touchesStop) {
                        for (int i = 0; i < factor; i++) {
                            Person newPerson = f.createPerson(Id.createPersonId(person.getId().toString() + "_" + count));
                            extractedPopulation.addPerson(newPerson);
                            Plan plan1 = f.createPlan();
                            newPerson.addPlan(plan1);
                            plan1.addActivity(trip.getOriginActivity());
                            for (PlanElement pe : trip.getTripElements()) {
                                if (pe instanceof Activity) {
                                    plan1.addActivity((Activity) pe);
                                } else if (pe instanceof Leg) {
                                    plan1.addLeg((Leg) pe);
                                }
                            }
                            plan1.addActivity(trip.getDestinationActivity());

                            count++;

                        }
                    }
                }

            });


            spr.readFile(s);
        }
        new PopulationWriter(extractedPopulation).write(extractedTrips);
    }
}
