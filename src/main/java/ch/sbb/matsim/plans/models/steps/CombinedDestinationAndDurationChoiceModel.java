package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.constants.ZonalAttributes;
import ch.sbb.matsim.plans.matrices.Matrices;
import ch.sbb.matsim.plans.matrices.MatrixNames;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.formulas.ModeImpedanceFormulaRepository;
import ch.sbb.matsim.plans.models.utils.ExpressionEvaluator;
import ch.sbb.matsim.plans.models.utils.StrataPredicateFactory;
import ch.sbb.matsim.plans.synpop.DecisionStrata;
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
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.population.PopulationUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Singleton
public class CombinedDestinationAndDurationChoiceModel {

    private static final Logger log = LogManager.getLogger(CombinedDestinationAndDurationChoiceModel.class);
    private static final double MAX_INTERMEDIATE_DETOUR_SECONDS = 3600.0; // 1 hour max detour for intermediate stops
    public static final String ASSIGNED_ZONE = "assigned_zone";

    private final Scenario scenario;
    private final Matrices matrices;
    private final int threads;

    private static final String[] ACTIVITY_TYPES = SBBActivities.secondaryActivities.toArray(new String[SBBActivities.secondaryActivities.size()]);
    private final Map<String, Integer> activityTypeToIndex;
    // The flattened Attraction Cache: [ActivityTypeIndex][ZoneIndex]
    private float[][] zoneAttractions;


    // Add to class constants (you might want to read these from config later)
    private static final int NUM_DURATION_PLANS = 10;
    private static final int NUM_DESTINATION_PLANS = 3;


    private final List<Zone> zoneList;
    private final Map<Id<Zone>, Integer> zoneIndexMap;
    private final ActivityDurationAssignmentModel durationModelNoSubtour;
    private final ActivityDurationAssignmentModel durationModelWithSubtour;
    private final MOBiPlansConfig mobiPlansConfig;
    private final ModeImpedanceFormulaRepository formulaRepository;
    private final StrataPredicateFactory strataPredicateFactory;
    private SecondaryKalifMatrixCache kalifMatrixCache;
    private SecondaryDestinationLogsumCache logsumCache;
    private float[][] blendedTravelTimes;

    @Inject
    public CombinedDestinationAndDurationChoiceModel(Scenario scenario, MOBiPlansConfig config, Zones zones, Matrices matrices, ModeImpedanceFormulaRepository formulaRepository, StrataPredicateFactory strataPredicateFactory) {
        this.scenario = scenario;
        this.mobiPlansConfig = config;
        this.matrices = matrices;
        this.threads = config.models().threads();
        Path modelFolder = Paths.get(config.data().modelBaseDir()).resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_CHOICE_MODELS);
        this.durationModelNoSubtour = new ActivityDurationAssignmentModel(modelFolder.resolve(MOBiPlansConfig.ACTIVITY_DURATIONS_WITHOUT_SUBTOUR_CSV).toString());
        this.durationModelWithSubtour = new ActivityDurationAssignmentModel(modelFolder.resolve(MOBiPlansConfig.ACTIVITY_DURATIONS_WITH_SUBTOUR_CSV).toString());
        this.zoneList = new ArrayList<>(zones.getZones());
        this.zoneIndexMap = new HashMap<>();
        this.formulaRepository = formulaRepository;
        this.strataPredicateFactory = strataPredicateFactory;
        this.activityTypeToIndex = new HashMap<>();
        for (int i = 0; i < ACTIVITY_TYPES.length; i++) {
            activityTypeToIndex.put(ACTIVITY_TYPES[i], i);
        }

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
        this.logsumCache = new SecondaryDestinationLogsumCache(matrices, formulaRepository, new ExpressionEvaluator(), zoneList);
        this.logsumCache.precomputeAll(SBBActivities.secondaryActivities,DecisionStrata.getSecondaryDestinationStratas());
        log.info("Pre-caching Zonal Attractions for fast lookups...");
        this.zoneAttractions = new float[ACTIVITY_TYPES.length][zoneList.size()];
        for (int aIdx = 0; aIdx < ACTIVITY_TYPES.length; aIdx++) {
            String attrName = getAttractionAttributeName(ACTIVITY_TYPES[aIdx]);
            for (int zIdx = 0; zIdx < zoneList.size(); zIdx++) {
                this.zoneAttractions[aIdx][zIdx] = (float) getZoneDouble(zoneList.get(zIdx), attrName, 0.0);
            }
        }

    }

    public void runIteration(double perfBudgetHours, double travelBudgetHours, double oohBudgetHours) {

        // 1. Collect exactly who needs to be processed to get the total count
        List<Person> personsToProcess = scenario.getPopulation().getPersons().values().stream()
                .filter(p -> Boolean.TRUE.equals(p.getAttributes().getAttribute(IterativeDailyPlanGenerationOrchestrator.NEEDS_DAILY_REPLANNING)))
                .collect(Collectors.toList());

        int totalPersons = personsToProcess.size();
        if (totalPersons == 0) return;

        log.info("Starting Destination & Duration Choice loop for {} agents...", totalPersons);

        // 2. Set up thread-safe tracking
        AtomicInteger progressCounter = new AtomicInteger();
        // Print an update every 5% (ensure it's at least 1 to avoid divide-by-zero)
        int reportInterval = Math.max(1, totalPersons / 20);

        try (ForkJoinPool customPool = new ForkJoinPool(threads)){
            customPool.submit(() ->
                    personsToProcess.parallelStream().forEach(person -> {

                        // Run the actual heavy lifting
                        processPerson(person, perfBudgetHours, travelBudgetHours, oohBudgetHours);

                        // Safely increment and log progress
                        int completed = progressCounter.incrementAndGet();
                        if (completed % reportInterval == 0 || completed == totalPersons) {
                            int percentage = (int) Math.round(((double) completed / totalPersons) * 100);
                            log.info("  ... {}% complete ({} / {} persons processed).",
                                    percentage, completed, totalPersons);
                        }
                    })
            ).get();
        } catch (Exception e) {
            throw new RuntimeException("Parallel execution failed", e);
        }
    }

    record DurationPlans(double[][] guesses, double[] totalTimes) { }
    record DestinationPlans(Zone[][] guesses, double[] totalTimes) { }
    record PlanSelection(int durIdx, int destIdx, boolean isValid) { }

    private void processPerson(Person person, double perfBudgetHours, double travelBudgetHours, double oohBudgetHours) {

        // Setup & Validation
        Random random = SynPopUtils.getRandom(person.getId(), getClass().getName() + oohBudgetHours);

        Zone homeZone = (Zone) person.getAttributes().getAttribute(SynpopConstants.ZONE);
        if (homeZone == null) return;

        List<Tour> tours = (List<Tour>) person.getAttributes().getAttribute(SynpopConstants.TOURS);
        if (tours == null || tours.isEmpty()) {
            person.getAttributes().putAttribute(IterativeDailyPlanGenerationOrchestrator.NEEDS_DAILY_REPLANNING, false);
            return;
        }

        Zone workZone = (Zone) person.getAttributes().getAttribute(SynpopConstants.WORK_ZONE);
        Zone eduZone = (Zone) person.getAttributes().getAttribute(SynpopConstants.SCHOOL_ZONE);

        int totalActivities = countTotalActivities(tours);

        // Step 1: Build Duration Plans
        DurationPlans durPlans = buildDurationPlans(person, tours, totalActivities, random);

        // Step 2: Build Destination Plans
        DestinationPlans destPlans = buildDestinationPlans(person, tours, totalActivities, homeZone, workZone, eduZone, travelBudgetHours, random);

        // Step 3: Budget Optimization Loop
        PlanSelection selection = selectBestPlan(durPlans.totalTimes, destPlans.totalTimes, perfBudgetHours, travelBudgetHours, oohBudgetHours, random);

        // Step 4: Apply Winning Plan
        applyWinningPlan(person, tours, destPlans.guesses, selection.destIdx, durPlans.guesses, selection.durIdx, selection.isValid);
    }


    private int countTotalActivities(List<Tour> tours) {
        int total = 0;
        for (Tour tour : tours) {
            total += tour.getOutboundStopActivities().size() + tour.getInboundStopActivities().size() + tour.getSubtourActivities().size();
            total += tour.getSubtourActivities().isEmpty() ? 1 : 2;
        }
        return total;
    }

    private DurationPlans buildDurationPlans(Person person, List<Tour> tours, int totalActivities, Random random) {
        double[][] durationGuesses = new double[NUM_DURATION_PLANS][totalActivities];
        double[] totalPerformingTimes = new double[NUM_DURATION_PLANS];

        for (int p = 0; p < NUM_DURATION_PLANS; p++) {
            double totalDur = 0.0;
            int actIdx = 0;

            for (Tour tour : tours) {
                boolean hasSubtour = !tour.getSubtourActivities().isEmpty();

                // Outbound
                for (var act : tour.getOutboundStopActivities()) {
                    double dur = durationModelNoSubtour.getDurationInSeconds(person, act.getType(), random);
                    durationGuesses[p][actIdx++] = dur; totalDur += dur;
                }

                // Main Activity Part 1
                ActivityDurationAssignmentModel mainDurModel = hasSubtour ? durationModelWithSubtour : durationModelNoSubtour;
                double mainDur1 = mainDurModel.getDurationInSeconds(person, tour.getMainActivityType(), random);
                durationGuesses[p][actIdx++] = mainDur1; totalDur += mainDur1;

                // Subtours & Main Activity Part 2
                if (hasSubtour) {
                    for (var act : tour.getSubtourActivities()) {
                        double dur = durationModelNoSubtour.getDurationInSeconds(person, act.getType(), random);
                        durationGuesses[p][actIdx++] = dur; totalDur += dur;
                    }
                    double mainDur2 = durationModelWithSubtour.getDurationInSeconds(person, tour.getMainActivityType(), random);
                    durationGuesses[p][actIdx++] = mainDur2; totalDur += mainDur2;
                }

                // Inbound
                for (var act : tour.getInboundStopActivities()) {
                    double dur = durationModelNoSubtour.getDurationInSeconds(person, act.getType(), random);
                    durationGuesses[p][actIdx++] = dur; totalDur += dur;
                }
            }
            totalPerformingTimes[p] = totalDur / 3600.0;
        }
        return new DurationPlans(durationGuesses, totalPerformingTimes);
    }

    private DestinationPlans buildDestinationPlans(Person person, List<Tour> tours, int totalActivities, Zone homeZone, Zone workZone, Zone eduZone, double travelBudgetHours, Random random) {
        Zone[][] destinationGuesses = new Zone[NUM_DESTINATION_PLANS][totalActivities];
        double[] totalTravelTimes = new double[NUM_DESTINATION_PLANS];

        for (int p = 0; p < NUM_DESTINATION_PLANS; p++) {
            double totalTT = 0.0;
            int actIdx = 0;

            for (Tour tour : tours) {
                // A. HIERARCHICAL STEP
                Zone mainZone;
                if (tour.getTourType() == Tour.TourType.PRIMARY) {
                    mainZone = SBBActivities.work.equals(tour.getMainActivityType()) ? workZone : eduZone;
                } else {
                    if (SBBActivities.education.equals(tour.getMainActivityType()) && eduZone != null) {
                        mainZone = eduZone;
                    }
                    else
                        {
                        mainZone = drawDestinationZone(person, tour.getMainActivityType(), homeZone, homeZone, travelBudgetHours, random);
                    }
                }

                // B. CHRONOLOGICAL STEP
                Zone currentPathZone = homeZone;

                // 1. Outbound Stops
                for (var act : tour.getOutboundStopActivities()) {
                    Zone stopZone;
                    if (act.getType().equals(SBBActivities.education) && eduZone != null) {
                        stopZone = eduZone;
                    } else {
                    stopZone = drawDestinationZone(person, act.getType(), currentPathZone, mainZone, travelBudgetHours, random);
                    }
                    destinationGuesses[p][actIdx++] = stopZone;
                    totalTT += blendedTravelTimes[zoneIndexMap.get(currentPathZone.getId())][zoneIndexMap.get(stopZone.getId())];
                    currentPathZone = stopZone;
                }

                // 2. Main Activity Part 1
                destinationGuesses[p][actIdx++] = mainZone;
                totalTT += blendedTravelTimes[zoneIndexMap.get(currentPathZone.getId())][zoneIndexMap.get(mainZone.getId())];
                currentPathZone = mainZone;

                // 3. Subtours
                if (!tour.getSubtourActivities().isEmpty()) {
                    for (var act : tour.getSubtourActivities()) {
                        Zone subZone;
                        if (act.getType().equals(SBBActivities.education) && eduZone != null) {
                            subZone = eduZone;
                        } else {
                        subZone= drawDestinationZone(person, act.getType(), mainZone, mainZone, travelBudgetHours, random);
                        }
                        destinationGuesses[p][actIdx++] = subZone;
                        totalTT += blendedTravelTimes[zoneIndexMap.get(currentPathZone.getId())][zoneIndexMap.get(subZone.getId())];
                        currentPathZone = subZone;
                    }
                    // Return to Main Activity Part 2
                    destinationGuesses[p][actIdx++] = mainZone;
                    totalTT += blendedTravelTimes[zoneIndexMap.get(currentPathZone.getId())][zoneIndexMap.get(mainZone.getId())];
                    currentPathZone = mainZone;
                }

                // 4. Inbound Stops
                for (var act : tour.getInboundStopActivities()) {
                    Zone stopZone;
                    if (act.getType().equals(SBBActivities.education)  && eduZone != null) {
                          stopZone = eduZone;
                    } else {
                     stopZone = drawDestinationZone(person, act.getType(), currentPathZone, homeZone, travelBudgetHours, random);
                    }
                    destinationGuesses[p][actIdx++] = stopZone;
                    totalTT += blendedTravelTimes[zoneIndexMap.get(currentPathZone.getId())][zoneIndexMap.get(stopZone.getId())];
                    currentPathZone = stopZone;
                }

                // 5. Finally, return Home
                totalTT += blendedTravelTimes[zoneIndexMap.get(currentPathZone.getId())][zoneIndexMap.get(homeZone.getId())];
            }
            totalTravelTimes[p] = totalTT / 3600.0;
        }
        return new DestinationPlans(destinationGuesses, totalTravelTimes);
    }

    private PlanSelection selectBestPlan(double[] totalPerformingTimes, double[] totalTravelTimes, double perfBudgetHours, double travelBudgetHours, double oohBudgetHours, Random random) {
        // Duration Evaluation
        // 4. Budget Optimization Loop (Zero-Allocation Version)

        int[] validDurIndices = new int[NUM_DURATION_PLANS];
        int validDurCount = 0;
        int bestInvalidDurIdx = 0;
        double minDurTime = Double.MAX_VALUE;

        for (int p = 0; p < NUM_DURATION_PLANS; p++) {
            if (totalPerformingTimes[p] <= perfBudgetHours) {
                validDurIndices[validDurCount++] = p;
            }
            if (totalPerformingTimes[p] < minDurTime) {
                minDurTime = totalPerformingTimes[p];
                bestInvalidDurIdx = p;
            }
        }
        if (validDurCount == 0) validDurIndices[validDurCount++] = bestInvalidDurIdx;

        int[] validDestIndices = new int[NUM_DESTINATION_PLANS];
        int validDestCount = 0;
        int bestInvalidDestIdx = 0;
        double minDestTime = Double.MAX_VALUE;

        for (int p = 0; p < NUM_DESTINATION_PLANS; p++) {
            if (totalTravelTimes[p] <= travelBudgetHours) {
                validDestIndices[validDestCount++] = p;
            }
            if (totalTravelTimes[p] < minDestTime) {
                minDestTime = totalTravelTimes[p];
                bestInvalidDestIdx = p;
            }
        }
        if (validDestCount == 0) validDestIndices[validDestCount++] = bestInvalidDestIdx;

        // --- The Zero-Allocation Search ---
        int winningDurIdx = -1;
        int winningDestIdx = -1;
        double bestOohTime = Double.MAX_VALUE;
        boolean foundValidPlan = false;

        // Total combinations to check
        int numCombos = validDurCount * validDestCount;

        // Pick a random starting point for the search to mimic shuffling
        int startOffset = random.nextInt(numCombos);

        for (int i = 0; i < numCombos; i++) {
            // Modular arithmetic to cycle through all combinations starting from the random offset
            int comboIndex = (startOffset + i) % numCombos;

            // Map the 1D index back to the 2D grid
            int durIdx = validDurIndices[comboIndex / validDestCount];
            int destIdx = validDestIndices[comboIndex % validDestCount];

            double oohTime = totalPerformingTimes[durIdx] + totalTravelTimes[destIdx];

            if (oohTime <= oohBudgetHours) {
                winningDurIdx = durIdx;
                winningDestIdx = destIdx;
                bestOohTime = oohTime;
                foundValidPlan = true;
                break; // Found one!
            }
            if (oohTime < bestOohTime) {
                winningDurIdx = durIdx;
                winningDestIdx = destIdx;
                bestOohTime = oohTime;
            }
        }

        // Fallback
        if (winningDurIdx == -1) {
            winningDurIdx = bestInvalidDurIdx;
            winningDestIdx = bestInvalidDestIdx;
        }

        return new PlanSelection(winningDurIdx, winningDestIdx, foundValidPlan);
    }
    private static void applyWinningPlan(Person person, List<Tour> tours, Zone[][] destinationGuesses, int winningDestIdx, double[][] durationGuesses, int winningDurIdx, boolean foundValidPlan) {
        int actIdx = 0; // Track position in the winning arrays

        for (Tour tour : tours) {
            boolean hasSubtour = !tour.getSubtourActivities().isEmpty();

            // 1. Outbound Stops
            for (var act : tour.getOutboundStopActivities()) {
                Zone finalZone = destinationGuesses[winningDestIdx][actIdx];
                double finalDurationSeconds = durationGuesses[winningDurIdx][actIdx];
                actIdx++;

                act.setMaximumDuration(finalDurationSeconds);
                act.getAttributes().putAttribute(ASSIGNED_ZONE, finalZone);
            }

            // 2. Main Activity Part 1
            Zone mainZone1 = destinationGuesses[winningDestIdx][actIdx];
            double mainDur1 = durationGuesses[winningDurIdx][actIdx];
            actIdx++;

            tour.getMainActivities().get(0).getAttributes().putAttribute(ASSIGNED_ZONE, mainZone1);
            tour.getMainActivities().get(0).setMaximumDuration(mainDur1);


            if (hasSubtour) {
                // 3. Subtours
                for (var act : tour.getSubtourActivities()) {
                    Zone finalZone = destinationGuesses[winningDestIdx][actIdx];
                    double finalDurationSeconds = durationGuesses[winningDurIdx][actIdx];
                    actIdx++;
                    act.setMaximumDuration(finalDurationSeconds);
                    act.getAttributes().putAttribute(ASSIGNED_ZONE, finalZone);
                }

                // 4. Main Activity Part 2 (After Subtour)
                double mainDur2 = durationGuesses[winningDurIdx][actIdx];
                actIdx++;
                Activity mainActPart2 = PopulationUtils.getFactory().createActivityFromCoord(tour.getMainActivityType(), null);
                mainActPart2.setMaximumDuration(mainDur2);
                tour.getMainActivities().add(mainActPart2);
            }

            // 5. Inbound Stops
            for (var act : tour.getInboundStopActivities()) {
                Zone finalZone = destinationGuesses[winningDestIdx][actIdx];
                double finalDurationSeconds = durationGuesses[winningDurIdx][actIdx];
                actIdx++;

                act.setMaximumDuration(finalDurationSeconds);
                act.getAttributes().putAttribute(ASSIGNED_ZONE, finalZone);
            }
        }

        // If the plan fits within budgets, this agent is finished!
        if (foundValidPlan) {
            person.getAttributes().putAttribute(IterativeDailyPlanGenerationOrchestrator.NEEDS_DAILY_REPLANNING, false);
        }
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
private Zone drawDestinationZone(Person person, String activityType, Zone originAnchor, Zone targetAnchor, double travelBudgetHours, Random random) {

    int oIdx = zoneIndexMap.get(originAnchor.getId());
    int tIdx = zoneIndexMap.get(targetAnchor.getId());

    // Determine the attraction attribute name based on the activity type
    String attrName = getAttractionAttributeName(activityType);
    // Strata resolution
    DecisionStrata strata = getStrataForPerson(person);
    float[][] kalifMatrix = this.kalifMatrixCache.getKalifMatrixForActivity(activityType);
    float[][] logsumMatrix = logsumCache.getOrComputeLogsumMatrix(activityType, strata);
    // Get the pre-cached attraction array for this specific activity type
    int actTypeIdx = activityTypeToIndex.getOrDefault(activityType, activityTypeToIndex.get(SBBActivities.other)); // Fallback to 'other' (index 4) if unknown
    float[] attractions = zoneAttractions[actTypeIdx];
    boolean isIntermediateStop = !originAnchor.getId().equals(targetAnchor.getId());
    double directTT = blendedTravelTimes[oIdx][tIdx];

    double[] utilities = new double[zoneList.size()];
    double maxUtility = Double.NEGATIVE_INFINITY;

    // THE BLAZING FAST INNER LOOP: 100% Primitive arrays, no method calls!
    for (int zIdx = 0; zIdx < zoneList.size(); zIdx++) {
        float attraction = attractions[zIdx]; // O(1) array lookup!

        if (attraction <= 0.0f) {
            utilities[zIdx] = Double.NEGATIVE_INFINITY;
            continue;
        }

        double detourTT = blendedTravelTimes[oIdx][zIdx] + blendedTravelTimes[zIdx][tIdx];

        // Time Ellipse / Bounding Box
        if (isIntermediateStop) {
            if (detourTT > directTT + MAX_INTERMEDIATE_DETOUR_SECONDS) {
                utilities[zIdx] = Double.NEGATIVE_INFINITY; continue;
            }
        } else {
            if (detourTT > travelBudgetHours * 3600.0) {
                utilities[zIdx] = Double.NEGATIVE_INFINITY; continue;
            }
        }

        double logsumDetour = logsumMatrix[oIdx][zIdx] + logsumMatrix[zIdx][tIdx];
        if (logsumDetour <= -20.0) {
            utilities[zIdx] = Double.NEGATIVE_INFINITY; continue;
        }

        double kalif = kalifMatrix != null ? kalifMatrix[oIdx][zIdx] : 0.0;

        // Math.log is the only heavy calculation left, and it's heavily filtered by the Ellipse!
        double u = Math.log(attraction) + logsumDetour + kalif;

        utilities[zIdx] = u;
        if (u > maxUtility) maxUtility = u;
    }

    if (maxUtility == Double.NEGATIVE_INFINITY) return originAnchor;

    double sumExp = 0.0;
    double[] probabilities = new double[zoneList.size()];
    for (int zIdx = 0; zIdx < zoneList.size(); zIdx++) {
        if (utilities[zIdx] > Double.NEGATIVE_INFINITY) {
            probabilities[zIdx] = Math.exp(utilities[zIdx] - maxUtility);
            sumExp += probabilities[zIdx];
        }
    }

    double r = random.nextDouble() * sumExp;
    double cumulative = 0.0;
    for (int zIdx = 0; zIdx < zoneList.size(); zIdx++) {
        if (probabilities[zIdx] > 0) {
            cumulative += probabilities[zIdx];
            if (r <= cumulative) return zoneList.get(zIdx);
        }
    }
    return originAnchor;// Fallback
}

private String getAttractionAttributeName(String activityType) {
    switch (activityType) {
        case SBBActivities.accompany: return ZonalAttributes.ATTR_A;
        case SBBActivities.business: return ZonalAttributes.ATTR_B;
        case SBBActivities.other: return ZonalAttributes.ATTR_O;
        case SBBActivities.education_continued: return ZonalAttributes.ATTR_EC;
        case SBBActivities.leisure: return ZonalAttributes.ATTR_L;
        case SBBActivities.shopping: return ZonalAttributes.ATTR_S;
        default: return ZonalAttributes.ATTR_O; // Fallback
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
        // Nullify the heavy matrices
        this.blendedTravelTimes = null;
        this.kalifMatrixCache = null;
        this.logsumCache.clearCache();
        this.zoneAttractions = null;

    }

private DecisionStrata getStrataForPerson(Person person) {
    for (DecisionStrata strata: DecisionStrata.getSecondaryDestinationStratas()) {
        if (strataPredicateFactory.getPredicate(strata).test(person)) {
            return strata;
        }
    }
    return null;
}
}
