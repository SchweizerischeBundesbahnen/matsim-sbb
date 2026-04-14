package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.utils.ChoiceUtils;
import ch.sbb.matsim.plans.synpop.SynPopUtils;
import ch.sbb.matsim.zones.Zone;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.ActivityOption;
import org.matsim.households.Household;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

public class AssignFacilityLocations implements ModelStep{


    private final Scenario scenario;
    private final ActivityFacilities activityFacilities;
    private final int numberOfThreads;
    private final Path dataFolder;
    private static final Logger log = LogManager.getLogger(AssignFacilityLocations.class);



    public AssignFacilityLocations(Scenario scenario, MOBiPlansConfig mobiPlansConfig) {
        this.scenario = scenario;
        this.activityFacilities = scenario.getActivityFacilities();
        this.numberOfThreads = mobiPlansConfig.models().threads();
        this.dataFolder = Paths.get(mobiPlansConfig.data().modelBaseDir()).resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_DATA);

    }


    @Override
    public String getName() {
        return MOBiPlansRunner.FACILITY_ALLOCATION;
    }

    @Override
    public void run() {
        prepareAttractivenessDataPerFacility();
        log.info("Preparing attractiveness data per facility...");

        log.info("Grouping facilities by Zone and Activity Type for fast lookup...");
        var facilityWeightsByZoneAndType = groupFacilities();

        log.info("Assigning exact facility coordinates to Secondary Activities...");
        assignFacilitiesToPopulation(facilityWeightsByZoneAndType);
    }
    
    
    private void assignFacilitiesToPopulation(Map<ZoneActivityKey, Map<Id<ActivityFacility>, Double>> facilityWeights) {
        try (ForkJoinPool customPool = new ForkJoinPool(numberOfThreads)) {
            customPool.submit(() ->
                    scenario.getPopulation().getPersons().values().parallelStream()
                            .filter(p -> new PopulationSampler.ActivePersonPredicate().test(p))
                            .forEach(person -> processPerson(person, facilityWeights))
            ).get();
        } catch (Exception e) {
            throw new RuntimeException("Parallel facility assignment failed", e);
        }
    }

    private void processPerson(Person person, Map<ZoneActivityKey, Map<Id<ActivityFacility>, Double>> facilityWeights) {
        Random random = SynPopUtils.getRandom(person.getId(), getName());
        Plan plan = person.getSelectedPlan();
        for (PlanElement pe : plan.getPlanElements()) {
            if (pe instanceof Activity act) {

                // home, work and education are handled differently
                if (act.getType().equals(SBBActivities.home)) {
                    Household household = ((Household) person.getAttributes().getAttribute(SynpopConstants.HOUSEHOLD));
                    Id<ActivityFacility> facId = createFacilityIdForHouseholdId(household.getId());
                    assignFacilityToActivity(act, facId);
                }
                else if (act.getType().equals(SBBActivities.work)) {
                    Id<ActivityFacility> workFacility = (Id<ActivityFacility>) act.getAttributes().getAttribute(SynpopConstants.WORK_FACILITY);
                    if (workFacility != null) {
                   assignFacilityToActivity(act, workFacility);
                    }
                }
                else if (act.getType().equals(SBBActivities.education)) {
                    Id<ActivityFacility> educationFacility = (Id<ActivityFacility>) act.getAttributes().getAttribute(SynpopConstants.EDUCATION_FACILITY);
                    if (educationFacility != null) {
                   assignFacilityToActivity(act, educationFacility);
                    }
                }
                else {

                // Retrieve the temporary Zone object we attached in the Destination Choice step
                Zone assignedZone = (Zone) act.getAttributes().getAttribute(CombinedDestinationAndDurationChoiceModel.ASSIGNED_ZONE);

                if (assignedZone != null) {
                    ZoneActivityKey lookupKey = new ZoneActivityKey(assignedZone.getId(), act.getType());

                    // A single, clean lookup!
                    Map<Id<ActivityFacility>, Double> options = facilityWeights.getOrDefault(lookupKey, Collections.emptyMap());

                    if (!options.isEmpty()) {
                        // Roll the roulette wheel!
                        Id<ActivityFacility> chosenFacId = ChoiceUtils.selectFromProbabilities(options, random);
                        assignFacilityToActivity(act, chosenFacId);
                    } else {
                        // Fallback: If no facilities offer this activity in this zone, just use the Zone Centroid
                        act.setCoord(MGC.coordinate2Coord(assignedZone.getEnvelope().centre()));
                        log.debug("Fallback: No facilities for '{}' in Zone {}. Using zone centroid.", lookupKey.activityType, lookupKey.zoneId);
                    }

                    // Clean up the temporary attribute so it doesn't bloat the final output XML
                    act.getAttributes().removeAttribute(CombinedDestinationAndDurationChoiceModel.ASSIGNED_ZONE);
                }
            }
            }
        }
    }

    private void assignFacilityToActivity(Activity act, Id<ActivityFacility> facId) {
        ActivityFacility f = activityFacilities.getFacilities().get(facId);
        if (f != null) {
            act.setFacilityId(f.getId());
            act.setCoord(f.getCoord());
        }
    }



    private record ZoneActivityKey(Id<Zone> zoneId, String activityType) {}
    /*
        * Groups facilities by their zone and activity type, and sums up the weights (capacities) for each group.
     */
    private Map<ZoneActivityKey, Map<Id<ActivityFacility>, Double>> groupFacilities() {
        Map<ZoneActivityKey, Map<Id<ActivityFacility>, Double>> grouped = new HashMap<>();

        for (ActivityFacility f : activityFacilities.getFacilities().values()) {
            Object zoneAttr = f.getAttributes().getAttribute(SynpopConstants.ZONE_ID);
            if (zoneAttr == null) continue;
            Id<Zone> zoneId = Id.create(zoneAttr.toString(), Zone.class);
            for (ActivityOption opt : f.getActivityOptions().values()) {
                double weight = opt.getCapacity();

                if (weight > 0) {
                    ZoneActivityKey key = new ZoneActivityKey(zoneId, opt.getType());
                    grouped.computeIfAbsent(key, k -> new HashMap<>()).put(f.getId(), weight);
                }
            }
        }
        return grouped;
    }

    private void prepareAttractivenessDataPerFacility() {
        prepareAttractionsPerBusiness();
        prepareAttractionPerHousehold();

    }

    private void prepareAttractionPerHousehold() {
        Map<String, Double> householdAttractionRates = readAttractionFromCSV(dataFolder.resolve(MOBiPlansConfig.HOUSEHOLD_ACTIVITY_ATTRACTION_CSV).toString());
            for (Household household : scenario.getHouseholds().getHouseholds().values()){
                int members = household.getMemberIds().size();
                ActivityFacility facility = activityFacilities.getFactory().createActivityFacility(createFacilityIdForHouseholdId(household.getId()), (Coord) household.getAttributes().getAttribute(SynpopConstants.COORD));
                facility.getAttributes().putAttribute(SynpopConstants.ZONE_ID, household.getAttributes().getAttribute(SynpopConstants.ZONE_ID));
                for (String activityType: householdAttractionRates.keySet()) {
                    double attraction = householdAttractionRates.get(activityType) * members;
                    ActivityOption option = activityFacilities.getFactory().createActivityOption(activityType);
                    option.setCapacity(attraction);
                    facility.addActivityOption(option);
                }
                activityFacilities.addActivityFacility(facility);
            }
    }

    private static Id<ActivityFacility> createFacilityIdForHouseholdId(Id<Household> householdId) {
        return Id.create("hh_" + householdId, ActivityFacility.class);
    }

    private Map<String, Double> readAttractionFromCSV(String filename) {
        Map<String,Double> householdAttractionRates = new HashMap<>();
        try (CSVReader reader = new CSVReader(filename,";")) {
            var line = reader.readLine();
            while (line != null) {
                String activityType = line.get("activity");
                double attractionRate = Double.parseDouble(line.get("attraction_rate"));
                householdAttractionRates.put(activityType, attractionRate);
                line = reader.readLine();
            }

        }catch (Exception e) {
            throw new RuntimeException("Error reading attraction rates from CSV: " + filename, e);
        }
        return householdAttractionRates;

    }

    private void prepareAttractionsPerBusiness() {
        Map<String, Set<Integer>> nogaCodeForActivityType = readNogaCodeToActivityTypeMapping(dataFolder.resolve(MOBiPlansConfig.NOGA_TO_ACTIVITIES_MAPPING_CSV).toString());
        for (ActivityFacility facility: activityFacilities.getFacilities().values()) {
            Object nogaCodeAttribute = facility.getAttributes().getAttribute(SynpopConstants.NOGA_CODE);
            if (nogaCodeAttribute!= null) {
            double attraction = ((Number) facility.getAttributes().getAttribute(SynpopConstants.RELATIVE_ATTRACTION)).doubleValue();
            int nogaCode = ((Number) nogaCodeAttribute).intValue();
                for (String activityType: nogaCodeForActivityType.keySet()) {
                    if (nogaCodeForActivityType.get(activityType).contains(nogaCode)) {
                        ActivityOption option = activityFacilities.getFactory().createActivityOption(activityType);
                        option.setCapacity(attraction);
                        facility.addActivityOption(option);
                    }
                }
            }
            }


    }

    private Map<String, Set<Integer>>  readNogaCodeToActivityTypeMapping(String filename) {
        Map <String, Set<Integer>> nogaCodeToActivityMapping = new HashMap<>();
        Set<String> allActivityTypes = new HashSet<>();
        allActivityTypes.addAll(SBBActivities.secondaryActivities);
        allActivityTypes.addAll(SBBActivities.primaryActivities);
        for (String activityType: allActivityTypes) {
            nogaCodeToActivityMapping.put(activityType, new HashSet<>());
        }
        try (CSVReader reader = new CSVReader(filename,";")) {
            var line = reader.readLine();
            while (line != null) {
                int nogaCode = Integer.parseInt(line.get("NOGA_Code"));
                if ("1".equals(line.get("B")))
                {
                    nogaCodeToActivityMapping.get(SBBActivities.business).add(nogaCode);
                }
                if( "1".equals(line.get("E")))
                {
                    nogaCodeToActivityMapping.get(SBBActivities.education).add(nogaCode);
                }
                if ("1".equals(line.get("S")))
                {
                    nogaCodeToActivityMapping.get(SBBActivities.shopping).add(nogaCode);
                }
                if ("1".equals(line.get("A")))
                {
                    nogaCodeToActivityMapping.get(SBBActivities.accompany).add(nogaCode);
                }
                if ("1".equals(line.get("L")))
                {
                    nogaCodeToActivityMapping.get(SBBActivities.leisure).add(nogaCode);
                }
                if("1".equals(line.get("O")))
                {
                    nogaCodeToActivityMapping.get(SBBActivities.other).add(nogaCode);
                }
                if ("1".equals(line.get("EC")))
                {
                    nogaCodeToActivityMapping.get(SBBActivities.education_continued).add(nogaCode);
                }

                line = reader.readLine();
            }
        }catch (Exception e) {
            throw new RuntimeException("Error reading NOGA to activity mapping from CSV: " + filename, e);
        }
        return nogaCodeToActivityMapping;
    }
}
