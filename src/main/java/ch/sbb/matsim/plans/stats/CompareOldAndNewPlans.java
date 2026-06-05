package ch.sbb.matsim.plans.stats;

import ch.sbb.matsim.config.variables.Variables;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompareOldAndNewPlans {

    static void main() {
        List<String> plansFiles = List.of("/Users/joschka.bischoff/Desktop/test.plans.xml.gz"
        //        ,"/Volumes/mobi/50_Ergebnisse/MOBi_5.1/plans/106_release/output/106_release.plans.xml.gz"
        );

        for (String planFile : plansFiles) {
        StreamingPopulationReader streamingPopulationReader = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
        Map<String,ActivityStats> activityStats = new HashMap<>();
        streamingPopulationReader.addAlgorithm(person -> {
            if (PopulationUtils.getSubpopulation(person).equals(Variables.REGULAR)){
            Plan plan = person.getSelectedPlan();
            if (plan!=null){
            TripStructureUtils.getTrips(plan).forEach(trip -> {
                   String act = trip.getDestinationActivity().getType();
                   if (act.startsWith("education")){
                       act = "education";
                   }
                   double distance = CoordUtils.calcEuclideanDistance(trip.getOriginActivity().getCoord(), trip.getDestinationActivity().getCoord());
                   activityStats.computeIfAbsent(act,(a)->new ActivityStats(a,new DescriptiveStatistics())).distance.addValue(distance);
                });
            }}
        });
        streamingPopulationReader.readFile(planFile);
        evaluateStats(activityStats);
        }
    }

    private static void evaluateStats(Map<String, ActivityStats> activityStats) {
        System.out.println("activityType,trips,avgDistance,medianDistance");

        activityStats.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    ActivityStats stats = entry.getValue();
                    DescriptiveStatistics distance = stats.distance();
                    long trips = distance.getN();
                    double avgDistance = distance.getMean();
                    double medianDistance = distance.getPercentile(50.0);

                    System.out.printf("%s,%d,%.2f,%.2f%n", stats.activityType(), trips, avgDistance, medianDistance);
                });
    }


    record ActivityStats(String activityType, DescriptiveStatistics distance ){}
}
