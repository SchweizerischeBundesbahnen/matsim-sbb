package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.utils.ChoiceUtils;
import ch.sbb.matsim.plans.synpop.SynPopUtils;
import ch.sbb.matsim.zones.Zone;
import jakarta.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
    private Map<ZoneActivityKey, Map<Id<ActivityFacility>, Double>> facilityWeightsByZoneAndType;
    private Map<Id<Zone>, List<Id<ActivityFacility>>> facilityIdsByZone;


    @Inject
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
        groupFacilities();

        log.info("Assigning exact facility coordinates to Secondary Activities...");
        assignFacilitiesToPopulation();
    }
    
    
    private void assignFacilitiesToPopulation() {
        try (ForkJoinPool customPool = new ForkJoinPool(numberOfThreads)) {
            customPool.submit(() ->
                    scenario.getPopulation().getPersons().values().parallelStream()
                            .filter(p -> new PopulationSampler.ActivePersonPredicate().test(p))
                            .filter((person) -> person.getSelectedPlan() != null)
                            .forEach(person -> processPerson(person))
            ).get();
        } catch (Exception e) {
            throw new RuntimeException("Parallel facility assignment failed", e);
        }
    }

    private void processPerson(Person person) {
        Random random = SynPopUtils.getRandom(person.getId(), getName());
        Plan plan = person.getSelectedPlan();
        for (PlanElement pe : plan.getPlanElements()) {
            if (pe instanceof Activity act) {

                // home, work and education are handled differently
                if (act.getType().equals(SBBActivities.home)) {
                    Household household = ((Household) person.getAttributes().getAttribute(SynpopConstants.HOUSEHOLD));
                    ActivityFacility activityFacility = (ActivityFacility) household.getAttributes().getAttribute(SynpopConstants.FACILITY);
                    assignFacilityToActivity(act, activityFacility);
                }
                else if (act.getType().equals(SBBActivities.work)) {
                    ActivityFacility workFacility = (ActivityFacility) person.getAttributes().getAttribute(SynpopConstants.WORK_FACILITY);
                    if (workFacility != null) {
                        assignFacilityToActivity(act, workFacility);
                    }
                    else {
                        log.error("Person {} has a work activity but no assigned work facility. Skipping facility assignment for this activity.", person.getId());
                    }
                }
                else if (act.getType().equals(SBBActivities.education)) {
                    ActivityFacility educationFacility = (ActivityFacility) person.getAttributes().getAttribute(SynpopConstants.EDUCATION_FACILITY);
                    if (educationFacility != null){
                         assignFacilityToActivity(act, educationFacility);
                    }
                    else {
                        log.error("Person {} has an education activity but no assigned education facility. Skipping facility assignment for this activity.", person.getId());
                    }

                }
                else {

                // Retrieve the temporary Zone object we attached in the Destination Choice step
                Zone assignedZone = (Zone) act.getAttributes().getAttribute(CombinedDestinationAndDurationChoiceModel.ASSIGNED_ZONE);

                if (assignedZone != null) {
                    ZoneActivityKey lookupKey = new ZoneActivityKey(assignedZone.getId(), act.getType());
                    // A single, clean lookup!
                    Map<Id<ActivityFacility>, Double> options = facilityWeightsByZoneAndType.getOrDefault(lookupKey, Collections.emptyMap());
                    if (!options.isEmpty()) {
                        // Roll the roulette wheel!
                        Id<ActivityFacility> chosenFacId = ChoiceUtils.selectFromProbabilities(options, random);
                        assignFacilityToActivity(act, scenario.getActivityFacilities().getFacilities().get(chosenFacId));
                    } else {
                        //Fallback: Use a leisure facility, these include all households
                        Map<Id<ActivityFacility>, Double> alternativeOptions = facilityWeightsByZoneAndType.getOrDefault(new ZoneActivityKey(assignedZone.getId(), SBBActivities.leisure), Collections.emptyMap());
                        if (!alternativeOptions.isEmpty()) {
                            // Roll the roulette wheel!
                            Id<ActivityFacility> chosenFacId = ChoiceUtils.selectFromProbabilities(alternativeOptions, random);
                            assignFacilityToActivity(act, scenario.getActivityFacilities().getFacilities().get(chosenFacId));
                        }
                        else {
                            // Fallback 2: Use any facility in the zone
                            List<Id<ActivityFacility>> facilitiesInZone = facilityIdsByZone.get(assignedZone.getId());
                            if (facilitiesInZone == null || facilitiesInZone.isEmpty()) {
                                // Fallback 3: should not happen
                                act.setCoord(MGC.coordinate2Coord(assignedZone.getEnvelope().centre()));
                                log.warn("Fallback: No facilities for '{}' in Zone {}. Using zone centroid.", lookupKey.activityType, lookupKey.zoneId);
                            } else{
                                int draw = random.nextInt(facilitiesInZone.size());
                                Id<ActivityFacility> chosenFacId = facilitiesInZone.get(draw);
                                assignFacilityToActivity(act, scenario.getActivityFacilities().getFacilities().get(chosenFacId));
                            }

                        }
                    }

                }
            }
            }
        }
    }

    private void assignFacilityToActivity(Activity act, ActivityFacility facility) {
        act.setFacilityId(facility.getId());
        act.setCoord(facility.getCoord());

    }



    private record ZoneActivityKey(Id<Zone> zoneId, String activityType) {}
    /*
        * Groups facilities by their zone and activity type, and sums up the weights (capacities) for each group.
     */
    private void groupFacilities() {
        this.facilityWeightsByZoneAndType = new HashMap<>();
        this.facilityIdsByZone = new HashMap<>();
        for (ActivityFacility f : activityFacilities.getFacilities().values()) {
            Object zoneAttr = f.getAttributes().getAttribute(SynpopConstants.ZONE_ID);
            if (zoneAttr == null) continue;
            Id<Zone> zoneId = Id.create(zoneAttr.toString(), Zone.class);
            this.facilityIdsByZone.computeIfAbsent(zoneId,_ -> new ArrayList<>()).add(f.getId());
            for (ActivityOption opt : f.getActivityOptions().values()) {
                double weight = opt.getCapacity();

                if (weight > 0) {
                    ZoneActivityKey key = new ZoneActivityKey(zoneId, opt.getType());
                    facilityWeightsByZoneAndType.computeIfAbsent(key, _ -> new HashMap<>()).put(f.getId(), weight);
                }
            }
        }
    }

    private void prepareAttractivenessDataPerFacility() {
        prepareAttractionsPerBusiness();
        prepareAttractionPerHousehold();

    }

    private void prepareAttractionPerHousehold() {
        Map<String, Double> householdAttractionRates = readAttractionFromCSV(dataFolder.resolve(MOBiPlansConfig.HOUSEHOLD_ACTIVITY_ATTRACTION_CSV).toString());
            for (Household household : scenario.getHouseholds().getHouseholds().values()){
                int members = household.getMemberIds().size();
                ActivityFacility facility = (ActivityFacility) household.getAttributes().getAttribute(SynpopConstants.FACILITY);
                facility.getAttributes().putAttribute(SynpopConstants.ZONE_ID, household.getAttributes().getAttribute(SynpopConstants.ZONE_ID));
                for (String activityType: householdAttractionRates.keySet()) {
                    double attraction = householdAttractionRates.get(activityType) * members;
                    ActivityOption option = activityFacilities.getFactory().createActivityOption(activityType);
                    option.setCapacity(attraction);
                    facility.addActivityOption(option);
                }
            }
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
