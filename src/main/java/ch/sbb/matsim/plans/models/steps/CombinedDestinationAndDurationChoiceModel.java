package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.constants.ZonalAttributes;
import ch.sbb.matsim.plans.matrices.Matrices;
import ch.sbb.matsim.plans.matrices.MatrixNames;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.formulas.ModeImpedanceFormulaRepository;
import ch.sbb.matsim.plans.models.utils.ExpressionEvaluator;
import ch.sbb.matsim.plans.synpop.SynPopUtils;
import ch.sbb.matsim.plans.tours.Tour;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

@Singleton
public class CombinedDestinationAndDurationChoiceModel {

    private static final Logger log = LogManager.getLogger(CombinedDestinationAndDurationChoiceModel.class);

    private final Scenario scenario;
    private final Zones zones;
    private final Matrices matrices;
    private final int threads;

    // Budgets from the Python code
    private final double PERFORMING_BUDGET_HOURS = 16.0;
    private final double TRAVEL_TIME_BUDGET_HOURS = 5.0;
    private final double OUT_OF_HOME_TIME_BUDGET_HOURS = 18.0;

    // Add to class constants (you might want to read these from config later)
    private static final int NUM_DURATION_PLANS = 10;
    private static final int NUM_DESTINATION_PLANS = 3;
    // Assuming a standard Beta for travel time impedance, you might need to adjust this!
    private static final double BETA_BLENDED_TT = -0.01;


    private final List<Zone> zoneList;
    private final Map<Id<Zone>, Integer> zoneIndexMap;
    private final ActivityDurationAssignmentModel durationModelNoSubtour;
    private final ActivityDurationAssignmentModel durationModelWithSubtour;
    private final MOBiPlansConfig mobiPlansConfig;
    private SecondaryKalifMatrixCache kalifMatrixCache;
    private SecondaryDestinationLogsumCache logsumCache;
    private float[][] blendedTravelTimes;

    @Inject
    public CombinedDestinationAndDurationChoiceModel(Scenario scenario, MOBiPlansConfig config, Zones zones, Matrices matrices, ModeImpedanceFormulaRepository formulaRepository) {
        this.scenario = scenario;
        this.zones = zones;
        this.mobiPlansConfig = config;
        this.matrices = matrices;
        this.threads = config.models().threads();
        Path modelFolder = Paths.get(config.data().modelBaseDir()).resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_CHOICE_MODELS);
        this.durationModelNoSubtour = new ActivityDurationAssignmentModel(modelFolder.resolve(MOBiPlansConfig.ACTIVITY_DURATIONS_WITHOUT_SUBTOUR_CSV).toString());
        this.durationModelWithSubtour = new ActivityDurationAssignmentModel(modelFolder.resolve(MOBiPlansConfig.ACTIVITY_DURATIONS_WITH_SUBTOUR_CSV).toString());
        this.zoneList = new ArrayList<>(zones.getZones());
        this.zoneIndexMap = new HashMap<>();
        this.logsumCache = new SecondaryDestinationLogsumCache(matrices, formulaRepository, new ExpressionEvaluator(), zoneList);

        for (int i = 0; i < zoneList.size(); i++) {
            zoneIndexMap.put(zoneList.get(i).getId(), i);
        }
    }
    public void prepare() {
        log.info("Starting Combined Destination and Duration Choice Model...");
        Instant start = Instant.now();
        log.info("Building Blended Travel Time Matrix for {} zones...", zoneList.size());
        this.blendedTravelTimes = buildBlendedTravelTimeMatrix();
        log.info("Finished building Blended Travel Time Matrix in {}.", Duration.between(start, Instant.now()));
        String secondaryKalifCsvPath = Paths.get(mobiPlansConfig.data().modelBaseDir())
                .resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_CONFIG)
                .resolve(MOBiPlansConfig.SECONDARY_ACTIVITY_CHOICE_KALIF_EXPRESSIONS_CSV)
                .toString();
        this.kalifMatrixCache = new SecondaryKalifMatrixCache(secondaryKalifCsvPath, matrices, zoneList, zoneIndexMap);
    }

    public void runIteration(double performingBudgetHours, double travelTimeBudgetHours, double outOfHomeTimeBudgetHours) {
        Instant start = Instant.now();

        ForkJoinPool customPool = new ForkJoinPool(threads);
        try {
            customPool.submit(() ->
                    scenario.getPopulation().getPersons().values().parallelStream()
                            .filter(person -> new PopulationSampler.ActivePersonPredicate().test(person))
                            .forEach(this::processPerson)
            ).get();
        } catch (Exception e) {
            throw new RuntimeException("Parallel execution failed", e);
        } finally {
            customPool.shutdown();
        }

        log.info("Finished Combined Destination and Duration Choice Model in {}.", Duration.between(start, Instant.now()));
    }

    private void processPerson(Person person) {
        // 1. Get the Home Zone
        Zone homeZone = (Zone) person.getAttributes().getAttribute(SynpopConstants.ZONE);
        if (homeZone == null) return; // Cannot schedule without a home zone

        // 2. Get the Primary Locations (assigned in PrimaryLocationChoiceModel)
        Zone workZone = (Zone) person.getAttributes().getAttribute(SynpopConstants.WORK_ZONE);
        Zone schoolZone = (Zone) person.getAttributes().getAttribute(SynpopConstants.SCHOOL_ZONE);

        List<Tour> tours = (List<Tour>) person.getAttributes().getAttribute(SynpopConstants.TOURS);
        if (tours == null || tours.isEmpty()) return;

        // 3. Flatten the schedule into a list of activities that need locations & durations
        List<ActivityToSchedule> activitiesToSchedule = new ArrayList<>();

        for (Tour tour : tours) {
            boolean hasSubtour = !tour.getSubtourActivities().isEmpty();

            if (tour.getTourType() == Tour.TourType.PRIMARY) {
                Zone primaryZone = SBBActivities.work.equals(tour.getMainActivityType()) ? workZone : schoolZone;
                if (primaryZone == null) continue;

                // Outbound Stops (Home -> Primary)
                tour.getInboundStopActivities().forEach(act ->
                        activitiesToSchedule.add(new ActivityToSchedule(act.getType(), homeZone, primaryZone, null, act, false))
                );

                // MAIN ACTIVITY (Primary) - Needs duration, but destination is FIXED
                activitiesToSchedule.add(new ActivityToSchedule(tour.getMainActivityType(), homeZone, homeZone, primaryZone, null, hasSubtour));

                // Subtour Stops (Primary -> Primary)
                tour.getSubtourActivities().forEach(act ->
                        activitiesToSchedule.add(new ActivityToSchedule(act.getType(), primaryZone, primaryZone, null, act, false))
                );

                // Inbound Stops (Primary -> Home)
                tour.getOutboundStopActivities().forEach(act ->
                        activitiesToSchedule.add(new ActivityToSchedule(act.getType(), primaryZone, homeZone, null, act, false))
                );

            } else if (tour.getTourType() == Tour.TourType.SECONDARY) {
                // Secondary Main Activity - Needs BOTH duration and destination
                activitiesToSchedule.add(new ActivityToSchedule(tour.getMainActivityType(), homeZone, homeZone, null, null, hasSubtour));

                // Secondary Stops
                tour.getInboundStopActivities().forEach(act ->
                        activitiesToSchedule.add(new ActivityToSchedule(act.getType(), homeZone, homeZone, null, act, false))
                );
                tour.getOutboundStopActivities().forEach(act ->
                        activitiesToSchedule.add(new ActivityToSchedule(act.getType(), homeZone, homeZone, null, act, false))
                );
            }
        }
        if (activitiesToSchedule.isEmpty()) return;
        Random random = SynPopUtils.getRandom(person.getId(), getClass().getName());
        // 4a. Build 10 Duration Plans
        double[][] durationGuesses = new double[NUM_DURATION_PLANS][activitiesToSchedule.size()];
        double[] totalPerformingTimes = new double[NUM_DURATION_PLANS];

        for (int p = 0; p < NUM_DURATION_PLANS; p++) {
            double totalTime = 0.0;
            for (int a = 0; a < activitiesToSchedule.size(); a++) {
                ActivityToSchedule act = activitiesToSchedule.get(a);
                // Pick the correct duration model based on whether a subtour exists
                ActivityDurationAssignmentModel durModel = act.hasSubtour ? durationModelWithSubtour : durationModelNoSubtour;
                double dur = durModel.getDurationInSeconds(person, act.activityType, random);
                durationGuesses[p][a] = dur;
                totalTime += dur;
            }
            totalPerformingTimes[p] = totalTime / 3600.0; // Store total in HOURS
        }
        // 4b. Build 3 Destination Plans
        Zone[][] destinationGuesses = new Zone[NUM_DESTINATION_PLANS][activitiesToSchedule.size()];
        double[] totalTravelTimes = new double[NUM_DESTINATION_PLANS];

        for (int p = 0; p < NUM_DESTINATION_PLANS; p++) {
            double totalTT = 0.0;
            for (int a = 0; a < activitiesToSchedule.size(); a++) {
                ActivityToSchedule act = activitiesToSchedule.get(a);

                // NEW: Bypass choice model if the destination is already known!
                Zone chosenZone;
                if (act.fixedZone != null) {
                    chosenZone = act.fixedZone;
                } else {
                    chosenZone = drawDestinationZone(act, blendedTravelTimes, random);
                }
                destinationGuesses[p][a] = chosenZone;

                // Add to the total travel time for this plan (Rubberbanding Detour approximation)
                int oIdx = zoneIndexMap.get(act.originAnchor.getId());
                int cIdx = zoneIndexMap.get(chosenZone.getId());
                int tIdx = zoneIndexMap.get(act.targetAnchor.getId());

                totalTT += blendedTravelTimes[oIdx][cIdx] + blendedTravelTimes[cIdx][tIdx];
            }
            totalTravelTimes[p] = totalTT / 3600.0; // Store total in HOURS
        }


        // TODO: 5. Run the 200-iteration budget optimization loop

        // TODO: 6. Apply winning choices back to the `activitiesToSchedule` and `person`

    }





/**
 * Translates the Python `get_tt_matrix` logic into a fast Java 2D array.
 */
private float[][] buildBlendedTravelTimeMatrix() {
    int numZones = zoneList.size();
    float[][] blendedTT = new float[numZones][numZones];

    zoneList.parallelStream().forEach(origin -> {
        int oIdx = zoneIndexMap.get(origin.getId());
        double atCarOrigin = getZoneDouble(origin, ZonalAttributes.AT_CAR, 0.0);

        for (Zone destination : zoneList) {
            int dIdx = zoneIndexMap.get(destination.getId());
            double atCarDest = getZoneDouble(destination, ZonalAttributes.AT_CAR, 0.0);

            // Fetch all metrics between these two zones
            var metrics = matrices.getTravelMetrics(origin.getId(), destination.getId());

            // --- 1. Calculate Car Travel Time ---
            // Python: car_travel_times_sym * 60 + at_car(O) + at_car(D) + 60 * pc_car * 2 * 0.047 / 0.125
            double pcCar = getZoneDouble(destination, ZonalAttributes.PC_CAR, 0.0);
            double carTT = getMetric(metrics, MatrixNames.TravelMetricsMatrices.CAR_TRAVEL_TIMES.name().toLowerCase(), 0.0)
                    + atCarOrigin + atCarDest
                    + 60.0 * pcCar * 2.0 * 0.047 / 0.125;

            // --- 2. Calculate PT Travel Time ---
            // Python: 60 * (train + bus + access + egress + transfers * 5)
            double ptTT = getMetric(metrics, MatrixNames.TravelMetricsMatrices.PT_TRAVEL_TIMES_TOTAL.name().toLowerCase(), 0.0)
                    + getMetric(metrics, MatrixNames.TravelMetricsMatrices.PT_ACCESS_TIME.name().toLowerCase(), 0.0)
                    + getMetric(metrics, MatrixNames.TravelMetricsMatrices.PT_EGRESS_TIME.name().toLowerCase(), 0.0)
                    + getMetric(metrics, MatrixNames.TravelMetricsMatrices.PT_TRANSFERS.name().toLowerCase(), 0.0) * 5.0 * 60.0;

            // --- 3. Blending Logic (np.where nesting) ---
            double blended;
            if (carTT > 2.0 * ptTT) {
                blended = ptTT;
            } else if (ptTT > 2.0 * carTT) {
                blended = carTT;
            } else {
                blended = (carTT + ptTT) / 2.0;
            }

            // --- 4. Short Distance Penalty ---
            double distance = getMetric(metrics, MatrixNames.TravelMetricsMatrices.CAR_NET_DISTANCE.name().toLowerCase(), 0.0);
            if (distance < 1500) {
                blended = distance / 1.66667; //assumed speed of 1.66667 m/s (6 km/h) for very short trips
            }

            // --- 5. Final Scaling ---
            blendedTT[oIdx][dIdx] = (float) (1.2 * blended);
        }
    });

    return blendedTT;
}
private Zone drawDestinationZone(ActivityToSchedule act, float[][] blendedTravelTimes, Random random) {
    int oIdx = zoneIndexMap.get(act.originAnchor.getId());
    int tIdx = zoneIndexMap.get(act.targetAnchor.getId());

    // Get the pre-calculated Kalif matrix for this specific activity type
    float[][] kalifMatrix = this.kalifMatrixCache.getKalifMatrixForActivity(act.activityType);

    // Determine the attraction attribute name based on the activity type
    String attrName = getAttractionAttributeName(act.activityType);

    double[] utilities = new double[zoneList.size()];
    double maxUtility = Double.NEGATIVE_INFINITY;

    // 1. Calculate utilities for all zones
    for (int zIdx = 0; zIdx < zoneList.size(); zIdx++) {
        Zone candidateZone = zoneList.get(zIdx);
        double attraction = getZoneDouble(candidateZone, attrName, 0.0);

        if (attraction <= 0) {
            utilities[zIdx] = Double.NEGATIVE_INFINITY;
            continue;
        }

        // Rubberbanding Detour: Time from Origin -> Candidate -> Target
        double detourTT = blendedTravelTimes[oIdx][zIdx] + blendedTravelTimes[zIdx][tIdx];

        // Utility = ln(Attraction) + Beta * Detour + Kalif(Origin -> Candidate)
        double kalif = kalifMatrix != null ? kalifMatrix[oIdx][zIdx] : 0.0;
        double u = Math.log(attraction) + (BETA_BLENDED_TT * detourTT) + kalif;

        utilities[zIdx] = u;
        if (u > maxUtility) maxUtility = u;
    }

    if (maxUtility == Double.NEGATIVE_INFINITY) {
        log.warn("No valid destination found for activity {}", act.activityType);
        return act.originAnchor; // Fallback so we don't crash
    }

    // 2. Exponentiate and normalize (using the LogSumExp trick for stability)
    double sumExp = 0.0;
    double[] probabilities = new double[zoneList.size()];
    for (int zIdx = 0; zIdx < zoneList.size(); zIdx++) {
        if (utilities[zIdx] > Double.NEGATIVE_INFINITY) {
            probabilities[zIdx] = Math.exp(utilities[zIdx] - maxUtility);
            sumExp += probabilities[zIdx];
        }
    }

    // 3. Roulette wheel selection
    double r = random.nextDouble() * sumExp;
    double cumulative = 0.0;
    for (int zIdx = 0; zIdx < zoneList.size(); zIdx++) {
        if (probabilities[zIdx] > 0) {
            cumulative += probabilities[zIdx];
            if (r <= cumulative) {
                return zoneList.get(zIdx);
            }
        }
    }

    return act.originAnchor; // Fallback
}

private String getAttractionAttributeName(String activityType) {
    switch (activityType) {
        case "accompany": return "Attr_A";
        case "business": return "Attr_B";
        case "other": return "Attr_O";
        case "education_continued": return "Attr_EC";
        case "leisure": return "Attr_L";
        case "shopping": return "Attr_S";
        default: return "Attr_O"; // Fallback
    }
}

private double getZoneDouble(Zone zone, String attributeName, double defaultValue) {
    Object val = zone.getAttributes().getAttribute(attributeName);
    if (val instanceof Number) return ((Number) val).doubleValue();
    if (val instanceof String) {
        try {
            return Double.parseDouble((String) val);
        } catch (NumberFormatException ignored) {
        }
    }
    return defaultValue;
}

private double getMetric(Map<String, Double> metrics, String key, double defaultValue) {
    return metrics != null ? metrics.getOrDefault(key, defaultValue) : defaultValue;
}
    public void cleanup() {
        // Nullify the heavy matrices so the Garbage Collector can reclaim 15+ GB of RAM
        this.blendedTravelTimes = null;
        this.kalifMatrixCache = null;
        this.logsumCache.clearCache();
        }

/**
 * Helper class to keep track of activities that need locations and durations.
 * Maps perfectly to the Python logic of Origin, Target, and ActivityType.
 */
private static class ActivityToSchedule {
    final String activityType;
    final Zone originAnchor;
    final Zone targetAnchor;
    final Zone fixedZone;
    final org.matsim.api.core.v01.population.Activity matsimActivityRef;
    final boolean hasSubtour;

    // Variables to hold the optimized choices later
    Zone chosenZone;
    double chosenDuration;

    ActivityToSchedule(String activityType, Zone originAnchor, Zone targetAnchor, Zone fixedZone,
                       org.matsim.api.core.v01.population.Activity matsimActivityRef, boolean hasSubtour) {
        this.activityType = activityType;
        this.originAnchor = originAnchor;
        this.targetAnchor = targetAnchor;
        this.fixedZone = fixedZone;
        this.matsimActivityRef = matsimActivityRef;
        this.hasSubtour = hasSubtour;
    }
}
}