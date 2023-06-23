package ch.sbb.matsim.preparation.casestudies;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.FacilitiesWriter;
import org.matsim.facilities.MatsimFacilitiesReader;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class MergePlansThatTouchZones {

    public static void main(String[] args) {
        String refPlansFile = args[0];
        String newPlansFile = args[1];
        String outputPlansFile = args[2];
        String refFacilitiesFile = args[3];
        String newFacilitiesFile = args[4];
        String outputFacilitiesFile = args[5];
        String zonesFile = args[6];
        String relevantZonesFile = args[7];
        Zones zones = ZonesLoader.loadZones("zone", zonesFile, Variables.ZONE_ID);
        Set<String> whitelistZones = MergeRoutedAndUnroutedPlans.readWhiteListZones(relevantZonesFile);
        Set<Id<ActivityFacility>> facilityWhiteList = MergeRoutedAndUnroutedPlans.prepareRelevantFacilities(whitelistZones, zones, List.of(refFacilitiesFile, newFacilitiesFile));


        Scenario baseCaseScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Scenario newPlansScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Scenario outScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Set<Id<ActivityFacility>> allFacilitiesUsed = new HashSet<>();
        new PopulationReader(baseCaseScenario).readFile(refPlansFile);
        new PopulationReader(newPlansScenario).readFile(newPlansFile);
        for (Person p : newPlansScenario.getPopulation().getPersons().values()) {
            boolean keepPlan = planTouchesArea(zones, whitelistZones, facilityWhiteList, p.getSelectedPlan());
            if (keepPlan) {
                allFacilitiesUsed.addAll(getAllFacilitiyIdsInPlan(p.getSelectedPlan()));
                outScenario.getPopulation().addPerson(p);
            }
        }
        for (Person p : baseCaseScenario.getPopulation().getPersons().values()) {
            boolean dropPlan = planTouchesArea(zones, whitelistZones, facilityWhiteList, p.getSelectedPlan());
            if (!dropPlan) {
                if (!outScenario.getPopulation().getPersons().containsKey(p.getId())) {
                    outScenario.getPopulation().addPerson(p);
                    allFacilitiesUsed.addAll(getAllFacilitiyIdsInPlan(p.getSelectedPlan()));

                }
            }
        }
        new PopulationWriter(outScenario.getPopulation()).write(outputPlansFile);

        new MatsimFacilitiesReader(baseCaseScenario).readFile(refFacilitiesFile);
        new MatsimFacilitiesReader(newPlansScenario).readFile(newFacilitiesFile);
        for (var facId : allFacilitiesUsed) {
            ActivityFacility facility = newPlansScenario.getActivityFacilities().getFacilities().get(facId);
            if (facility == null) {
                facility = baseCaseScenario.getActivityFacilities().getFacilities().get(facId);
            }
            Gbl.assertNotNull(facility);
            outScenario.getActivityFacilities().addActivityFacility(facility);
        }
        new FacilitiesWriter(outScenario.getActivityFacilities()).write(outputFacilitiesFile);

    }

    private static Set<Id<ActivityFacility>> getAllFacilitiyIdsInPlan(Plan selectedPlan) {
        return TripStructureUtils.getActivities(selectedPlan, TripStructureUtils.StageActivityHandling.ExcludeStageActivities)
                .stream()
                .map(Activity::getFacilityId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static boolean planTouchesArea(Zones zones, Set<String> whitelistZones, Set<Id<ActivityFacility>> facilityWhiteList, Plan plan) {
        return TripStructureUtils.getActivities(plan, TripStructureUtils.StageActivityHandling.ExcludeStageActivities).stream().anyMatch(activity -> {
            Id<ActivityFacility> facilityId = activity.getFacilityId();
            if (facilityId != null) {
                return facilityWhiteList.contains(facilityId);
            } else {
                return MergeRoutedAndUnroutedPlans.isCoordinWhiteListZone(whitelistZones, zones, activity.getCoord());

            }
        });
    }
}
