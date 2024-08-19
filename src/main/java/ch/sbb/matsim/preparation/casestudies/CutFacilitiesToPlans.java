package ch.sbb.matsim.preparation.casestudies;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.FacilitiesWriter;
import org.matsim.facilities.MatsimFacilitiesReader;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class CutFacilitiesToPlans {


    private final Scenario scenario;

    public CutFacilitiesToPlans(Scenario scenario) {
        this.scenario = scenario;
    }

    public static void main(String[] args) {
        String inputFacilitiesFile = "C:\\devsbb\\21_va_distances.facilities.xml.gz";
        String populationFile = "C:\\devsbb\\code\\matsim-sbb\\test\\input\\scenarios\\mobi50test\\plans.xml.gz";
        String outputFacilitiesFile = "C:\\devsbb\\code\\matsim-sbb\\test\\input\\scenarios\\mobi50test\\facilities.xml.gz";
        Scenario matsimScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(matsimScenario).readFile(populationFile);
        new MatsimFacilitiesReader(matsimScenario).readFile(inputFacilitiesFile);
        CutFacilitiesToPlans cutFacilitiesToPlans = new CutFacilitiesToPlans(matsimScenario);
        cutFacilitiesToPlans.cut();
        new FacilitiesWriter(matsimScenario.getActivityFacilities()).write(outputFacilitiesFile);
    }

    public void cut() {
        var relevantFacilityIds = scenario.getPopulation().getPersons()
                .values()
                .stream()
                .flatMap(person -> person.getPlans().stream())
                .flatMap(plan -> TripStructureUtils.getActivities(plan, TripStructureUtils.StageActivityHandling.ExcludeStageActivities).stream())
                .map(activity -> activity.getFacilityId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        System.out.println(relevantFacilityIds);
        Set<Id<ActivityFacility>> allFacilitiyIds = new HashSet<>(scenario.getActivityFacilities().getFacilities().keySet());
        allFacilitiyIds.removeAll(relevantFacilityIds);
        allFacilitiyIds.forEach(activityFacilityId -> scenario.getActivityFacilities().getFacilities().remove(activityFacilityId));
    }
}
