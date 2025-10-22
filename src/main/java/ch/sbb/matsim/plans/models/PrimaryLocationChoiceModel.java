package ch.sbb.matsim.plans.models;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.constants.ZonalAttributes;
import ch.sbb.matsim.plans.matrices.Matrices;
import ch.sbb.matsim.plans.matrices.MatrixNames;
import ch.sbb.matsim.plans.models.config.PrimaryLocationChoiceConfig;
import ch.sbb.matsim.plans.models.formulas.FormulaRepository;
import ch.sbb.matsim.plans.models.utils.ChoiceUtils;
import ch.sbb.matsim.plans.models.utils.ExpressionEvaluator;
import ch.sbb.matsim.plans.models.utils.StrataPredicateFactory;
import ch.sbb.matsim.plans.synpop.DecisionStrata;
import ch.sbb.matsim.plans.synpop.SynPopUtils;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.facilities.ActivityFacility;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PrimaryLocationChoiceModel {

    private static final double MIN_LOGSUM = -20.0;
    private static final double BETA_ATTRACTION = 1.0;
    private static final double BETA_LOGSUM = -1.0;
    private final Scenario scenario;
    private final Zones zones;
    private final Matrices matrices;
    private final FormulaRepository formulaRepository;
    private final ExpressionEvaluator expressionEvaluator;
    private final PrimaryLocationChoiceConfig primaryLocationChoiceConfig;
    private final StrataPredicateFactory strataPredicateFactory;
    private final Logger logger = LogManager.getLogger(PrimaryLocationChoiceModel.class);
    private final ForkJoinPool customThreadPool;

    public PrimaryLocationChoiceModel(Scenario scenario, int numThreads) {
        this.scenario = scenario;
        this.zones = (Zones) scenario.getScenarioElement(Variables.ZONES);
        this.matrices = (Matrices) scenario.getScenarioElement(Variables.MATRICES);
        this.formulaRepository = (FormulaRepository) scenario.getScenarioElement("modeImpedanceFormulas");
        this.primaryLocationChoiceConfig = (PrimaryLocationChoiceConfig) scenario.getScenarioElement("primaryLocationChoiceConfig");
        this.strataPredicateFactory = new StrataPredicateFactory();
        this.expressionEvaluator = new ExpressionEvaluator();
        this.customThreadPool = new ForkJoinPool(numThreads);
    }

    private static Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        throw new IllegalArgumentException("Cannot convert value to Double: " + value + " (" + value.getClass() + ")");
    }

    public void run() {
        try {
            logger.info("Starting Primary Location Choice Model...");
            Instant startRun = Instant.now();
            for (PrimaryLocationChoiceConfig.ActivityConfigRecord activityConfig : primaryLocationChoiceConfig.getPrimaryLocationChoiceConfigs()) {
                runForActivity(activityConfig);
            }
            logger.info("Primary Location Choice Model finished in {}.", Duration.between(startRun, Instant.now()));
        } finally {
            customThreadPool.shutdown();
        }
    }

    private void runForActivity(PrimaryLocationChoiceConfig.ActivityConfigRecord activityConfig) {
        logger.info("===== Processing activity: '{}' =====", activityConfig.activityName());
        Instant startAttraction = Instant.now();

        // Group strata by their shared attraction attribute.
        Map<String, List<Map.Entry<DecisionStrata, PrimaryLocationChoiceConfig.StrataConfigRecord>>> strataByAttraction =
                activityConfig.strataConfigs().entrySet().stream()
                        .collect(Collectors.groupingBy(entry -> entry.getValue().attractionAttributeName()));

        for (Map.Entry<String, List<Map.Entry<DecisionStrata, PrimaryLocationChoiceConfig.StrataConfigRecord>>> attractionGroup : strataByAttraction.entrySet()) {
            runForAttractionGroup(activityConfig, attractionGroup.getKey(), attractionGroup.getValue());
        }
        logger.info("===== Finished activity '{}' in {} =====", activityConfig.activityName(), Duration.between(startAttraction, Instant.now()));
    }

    private double calculateFriction(Zone homeZone, Zone destinationZone, PrimaryLocationChoiceConfig.StrataConfigRecord strataConfig, Map<String, String> modeFormulas) {
        var travelMetrics = getTravelAndAccessibilityMetrics(homeZone, destinationZone);
        double logsum = calculateLogsum(modeFormulas, travelMetrics);
        if (Double.isNaN(logsum) || Double.isInfinite(logsum)) {
            return -99999; // Return a large negative number
        }

        double kalif_ASC = matrices.getKalifMatrix(strataConfig.kalifMatrixName()).get(homeZone.getId(), destinationZone.getId());
        double matrixFactor = strataConfig.getMatrixFactor();

        return (kalif_ASC * matrixFactor) + BETA_LOGSUM * logsum;
    }

    /**
     * Performs Iterative Proportional Fitting with improved numerical stability.
     * It normalizes utilities before exponentiation to prevent underflow.
     */
    private double[][] runIPF(double[][] frictionMatrix, double[] rowTargets, double[] colTargets, int maxIterations) {
        int numOrigins = frictionMatrix.length;
        if (numOrigins == 0) return new double[0][0];
        int numDests = frictionMatrix[0].length;
        double[][] trips = new double[numOrigins][numDests];

        // ====================================================================
        // STEP 1: Initialize trips matrix using the numerically stable method
        // ====================================================================
        for (int i = 0; i < numOrigins; i++) {
            // Find the maximum value in the current row for normalization
            double maxFriction = -Double.MAX_VALUE;
            for (int j = 0; j < numDests; j++) {
                if (frictionMatrix[i][j] > maxFriction) {
                    maxFriction = frictionMatrix[i][j];
                }
            }

            // If a row is full of -Infinity (unreachable), maxFriction will stay very low.
            // In this case, the exp will correctly be zero for all cells.
            if (Double.isInfinite(maxFriction)) {
                // All destinations are unreachable for this origin, leave the row as zeros.
                continue;
            }

            // Exponentiate using the normalized friction value
            for (int j = 0; j < numDests; j++) {
                trips[i][j] = Math.exp(frictionMatrix[i][j] - maxFriction);
            }
        }

        // Diagnostic Check: After this step, the trips matrix should NOT be all zeros
        // if there is at least one valid utility.
        double initialTripsSum = Arrays.stream(trips).flatMapToDouble(Arrays::stream).sum();
        if (initialTripsSum == 0.0 && Arrays.stream(rowTargets).sum() > 0) {
            logger.warn("IPF DIAGNOSTIC: The entire trips matrix is zero after exponentiation. " +
                    "This implies all utilities were extremely negative. Final probabilities will be zero.");
        }


        // ====================================================================
        // STEP 2: Run the IPF scaling loops (this part is unchanged)
        // ====================================================================
        for (int iter = 0; iter < maxIterations; iter++) {
            // Column scaling
            for (int j = 0; j < numDests; j++) {
                double colSum = 0;
                for (int i = 0; i < numOrigins; i++) {
                    colSum += trips[i][j];
                }
                if (colSum > 0) {
                    double factor = colTargets[j] / colSum;
                    for (int i = 0; i < numOrigins; i++) {
                        trips[i][j] *= factor;
                    }
                }
            }

            // Row scaling
            for (int i = 0; i < numOrigins; i++) {
                double rowSum = 0;
                for (int j = 0; j < numDests; j++) {
                    rowSum += trips[i][j];
                }
                if (rowSum > 0) {
                    double factor = rowTargets[i] / rowSum;
                    for (int j = 0; j < numDests; j++) {
                        trips[i][j] *= factor;
                    }
                }
            }
        }

        // ====================================================================
        // STEP 3: Final normalization to probabilities (this part is unchanged)
        // ====================================================================
        double[][] probabilities = new double[numOrigins][numDests];
        for (int i = 0; i < numOrigins; i++) {
            double rowSum = 0;
            for (int j = 0; j < numDests; j++) {
                rowSum += trips[i][j];
            }
            if (rowSum > 0) {
                for (int j = 0; j < numDests; j++) {
                    probabilities[i][j] = trips[i][j] / rowSum;
                }
            }
        }
        return probabilities;
    }

    private void runForAttractionGroup(PrimaryLocationChoiceConfig.ActivityConfigRecord activityConfig, String attractionAttribute, List<Map.Entry<DecisionStrata, PrimaryLocationChoiceConfig.StrataConfigRecord>> strataGroup) {
        logger.info("--- Starting attraction group '{}' with {} strata. ---", attractionAttribute, strataGroup.size());
        Instant startGroup = Instant.now();

        // 1. Prepare data for each stratum and create a unified list of all unique people/zone combinations
        logger.info("[Step 1/6] Filtering population and grouping by home zone for {} strata...", strataGroup.size());
        Instant startFilter = Instant.now();
        List<PerStrataData> perStrataDataList = new ArrayList<>();
        List<Map.Entry<Id<Zone>, PerStrataData>> matrixRowMapping = new ArrayList<>();
        int totalPersonsInGroup = 0;

        for (var strataEntry : strataGroup) {
            PerStrataData data = new PerStrataData(strataEntry);
            Predicate<Person> strataPredicate = strataPredicateFactory.getPredicate(data.strata);

            Map<Id<Zone>, List<Person>> personsByZoneInStrata = scenario.getPopulation().getPersons().values().stream()
                    .filter(strataPredicate)
                    .collect(Collectors.groupingBy(p -> Id.create(p.getAttributes().getAttribute(SynpopConstants.ZONE_ID).toString(), Zone.class)));

            if (personsByZoneInStrata.isEmpty()) continue;

            int personCount = personsByZoneInStrata.values().stream().mapToInt(List::size).sum();
            totalPersonsInGroup += personCount;
            logger.debug("  - Found {} persons in {} active origin zones for strata '{}'", personCount, personsByZoneInStrata.size(), data.strata.getStrataName());


            data.personsByZone.putAll(personsByZoneInStrata);
            perStrataDataList.add(data);

            for (Id<Zone> zoneId : personsByZoneInStrata.keySet()) {
                matrixRowMapping.add(Map.entry(zoneId, data));
            }
        }
        logger.info("[Step 1/6] Finished filtering. Found {} total persons in group. Took {}.", totalPersonsInGroup, Duration.between(startFilter, Instant.now()));


        if (perStrataDataList.isEmpty() || matrixRowMapping.isEmpty()) {
            logger.warn("No persons found for any strata in attraction group: {}. Skipping.", attractionAttribute);
            return;
        }

        // 2. Define consistent destination zone ordering
        List<Zone> allDestinationZones = new ArrayList<>(zones.getZones());
        List<Zone> validDestinationZones = allDestinationZones.stream()
                .filter(zone -> ((Number) zone.getAttribute(attractionAttribute)).doubleValue() > 0)
                .collect(Collectors.toList());

        if (validDestinationZones.isEmpty()) {
            logger.warn("No destination zones with attraction > 0 found for attribute '{}'. Cannot assign locations for this group.", attractionAttribute);
            return; // Exit early if there are no valid destinations at all
        }
        int numRows = matrixRowMapping.size();
        int numDests = validDestinationZones.size();
        logger.info("[Step 2/6] Matrix dimensions defined: {} origins x {} destinations.", numRows, numDests);

        // 3. Build the combined friction matrix and row targets directly
        logger.info("[Step 3/6] Building initial utility matrix...");
        Instant startMatrixBuild = Instant.now();
        double[][] combinedFrictionMatrix = new double[numRows][numDests];
        double[] combinedRowTargets = new double[numRows];
        final AtomicInteger progressCounter = new AtomicInteger();
        final int totalRows = numRows;
        final int reportInterval = Math.max(1, totalRows / 20); // Report progress every 10%
        Runnable matrixBuildTask = () -> IntStream.range(0, numRows).parallel().forEach(i -> {

            Id<Zone> originZoneId = matrixRowMapping.get(i).getKey();
            Zone originZone = zones.getZone(originZoneId);
            PerStrataData strataData = matrixRowMapping.get(i).getValue();
            combinedRowTargets[i] = strataData.personsByZone.get(originZoneId).size();
            final Map<String, String> modeFormulas = formulaRepository.getModeFormulas(activityConfig.activityName(), strataData.strata);
            for (int j = 0; j < numDests; j++) {
                Zone destinationZone = validDestinationZones.get(j);
                combinedFrictionMatrix[i][j] = calculateFriction(originZone, destinationZone, strataData.strataConfig, modeFormulas);
            }
            int completedRows = progressCounter.incrementAndGet();
            if (completedRows % reportInterval == 0 || completedRows == totalRows) {
                int percentage = (int) Math.round(((double) completedRows / totalRows) * 100);
                logger.info("  ... Utility matrix build is {}% complete (processed {} / {} rows).", percentage, completedRows, totalRows);
            }
        });
        try {
            customThreadPool.submit(matrixBuildTask).get();

        } catch (InterruptedException | ExecutionException e) {
            logger.error("Parallel matrix build task failed!", e);
            throw new RuntimeException("Matrix build failed.", e);
        }

        logger.info("[Step 3/6] Finished building utility matrix. Took {}.", Duration.between(startMatrixBuild, Instant.now()));


        logger.info("[Step 4/6] Preparing IPF targets...");
        double[] colTargets = validDestinationZones.stream()
                .mapToDouble(zone -> ((Number) zone.getAttribute(attractionAttribute)).doubleValue())
                .toArray();

        double totalColTargets = Arrays.stream(colTargets).sum();
        double totalRowTargets = Arrays.stream(combinedRowTargets).sum();
        logger.debug("  - Total Row Targets (Persons): {}", totalRowTargets);
        logger.debug("  - Total Column Targets (Attractions): {}", totalColTargets);

        if (totalColTargets > 0) {
            double scaleFactor = totalRowTargets / totalColTargets;
            for (int j = 0; j < colTargets.length; j++) {
                colTargets[j] *= scaleFactor;
            }
        } else {
            logger.warn("Total attraction for group '{}' is zero. All choice probabilities will be zero.", attractionAttribute);
        }
        logger.info("[Step 4/6] Finished preparing IPF targets.");

        // 5. Run IPF on the combined matrix
        int ipfIterations = perStrataDataList.get(0).strataConfig.ipfIterations();
        logger.info("[Step 5/6] Running IPF with {} iterations...", ipfIterations);
        Instant startIpf = Instant.now();
        double[][] combinedProbabilityMatrix = runIPF(combinedFrictionMatrix, combinedRowTargets, colTargets, ipfIterations);
        logger.info("[Step 5/6] Finished IPF. Took {}.", Duration.between(startIpf, Instant.now()));
        logger.info("[Step 6/6] Assigning location zones to {} persons...", (int) totalRowTargets);
        Instant startAssign = Instant.now();


        Runnable assignZoneTask = () -> IntStream.range(0, numRows).parallel().forEach(i -> {
            Id<Zone> originZoneId = matrixRowMapping.get(i).getKey();
            PerStrataData strataData = matrixRowMapping.get(i).getValue();
            List<? extends Person> personsInZone = strataData.personsByZone.get(originZoneId);

            Map<Id<Zone>, Double> destinationProbabilities = new HashMap<>();
            for (int j = 0; j < numDests; j++) {
                destinationProbabilities.put(validDestinationZones.get(j).getId(), combinedProbabilityMatrix[i][j]);
            }

            double probSum = destinationProbabilities.values().stream().mapToDouble(d -> d).sum();
            if (Math.abs(probSum - 1.0) > 1e-6 && totalRowTargets > 0) {
                logger.warn("Probabilities do not sum to 1 (" + probSum + ") for origin zone " + originZoneId + " in strata " + strataData.strata);
            }

            for (Person person : personsInZone) {
                Id<Zone> chosenZoneId = ChoiceUtils.selectFromProbabilities(destinationProbabilities, SynPopUtils.getRandom(person.getId(), getClass().getName()));
                if (chosenZoneId != null) {
                    person.getAttributes().putAttribute(activityConfig.personResultAttributeName(), chosenZoneId.toString());
                } else if (probSum > 0) {
                    logger.warn("Could not choose a destination for person " + person.getId() + " despite valid probabilities.");
                }
            }
        });
        try {
            customThreadPool.submit(assignZoneTask).get();

        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Assignment to zones failed", e);
        }
        logger.info("[Step 6/7] Finished assigning locations. Took {}.", Duration.between(startAssign, Instant.now()));

        logger.info("[Step 7/7] Assigning zones to {} facilities...", (int) totalRowTargets);
        Instant startAllocate = Instant.now();
        Map<Id<Zone>, List<Person>> personsByActivityZone = scenario.getPopulation().getPersons().values().stream()
                .filter(p -> p.getAttributes().getAttribute(activityConfig.personResultAttributeName()) != null)
                .collect(Collectors.groupingBy(p -> Id.create(p.getAttributes().getAttribute(activityConfig.personResultAttributeName()).toString(), Zone.class)));
        var activityFacilityByZone = scenario.getActivityFacilities().getFacilities().values().stream()
                .collect(Collectors
                        .groupingBy(activityFacility ->
                                Id.create(String.valueOf(activityFacility.getAttributes().getAttribute(Variables.ZONE_ID))
                                        , Zone.class)));
        Runnable facilityAllocation = () -> personsByActivityZone.forEach((zoneId, people) -> {
            var facilitiesInZone = activityFacilityByZone.get(zoneId);
            if (facilitiesInZone == null || facilitiesInZone.isEmpty()) {
                throw new RuntimeException("No facilities found in zone " + zoneId + " for activity allocation.");
            }
            Map<Id<ActivityFacility>, Double> facilityProbabilities = new HashMap<>();
            facilitiesInZone.forEach(facility -> {
                double weight = ((Number) facility.getAttributes().getAttribute(SynpopConstants.FTE_ENDO)).doubleValue();
                facilityProbabilities.put(facility.getId(), weight);
            });
            for (Person person : people) {
                var activityFacilityId = ChoiceUtils.selectFromProbabilities(facilityProbabilities, SynPopUtils.getRandom(person.getId(), getClass().getName() + "_facility"));
                if (activityFacilityId != null) {
                    person.getAttributes().putAttribute(activityConfig.activityName() + "_facility", activityFacilityId.toString());
                } else {
                    logger.warn("Could not choose a facility for person " + person.getId() + " despite valid probabilities.");
                }
            }

        });
        try {
            customThreadPool.submit(facilityAllocation).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Facility assignment failed", e);
        }
        logger.info("[Step 7/7] Finished assigning facilities. Took {}.", Duration.between(startAllocate, Instant.now()));
        logger.info("--- Finished attraction group '{}' in {} ---", attractionAttribute, Duration.between(startAllocate, Instant.now()));
    }

    private Map<String, Double> getTravelAndAccessibilityMetrics(Zone origin, Zone destination) {
        var travelMetrics = matrices.getTravelMetrics(origin.getId(), destination.getId());

        travelMetrics.put("va_impedances", toDouble(matrices
                .getKalifMatrix(MatrixNames.KalifMatrices.VA_IMPEDANCES)
                .get(origin.getId(), destination.getId())));

        travelMetrics.put("from_acc_car", toDouble(origin.getAttribute(ZonalAttributes.AT_CAR)));
        travelMetrics.put("to_acc_car", toDouble(destination.getAttribute(ZonalAttributes.AT_CAR)));
        travelMetrics.put("to_pc_car", toDouble(destination.getAttribute(ZonalAttributes.PC_CAR)));

        travelMetrics.put("from_acc_ride", toDouble(origin.getAttribute(ZonalAttributes.AT_RIDE)));
        travelMetrics.put("to_acc_ride", toDouble(destination.getAttribute(ZonalAttributes.AT_RIDE)));
        travelMetrics.put("to_pc_ride", toDouble(destination.getAttribute(ZonalAttributes.PC_RIDE)));

        return travelMetrics;
    }

    private double calculateLogsum(Map<String, String> modeFormulas, Map<String, Double> travelMetrics) {
        double sumOfExponentials = 0.0;
        for (String formula : modeFormulas.values()) {
            if (formula.trim().equals("-9999")) continue;
            double modeUtility = expressionEvaluator.evaluate(formula, travelMetrics);

            // Add a check here to prevent individual utilities from being too negative,
            // which is good practice but the main fix is below.
            if (modeUtility < -50) { // An arbitrary but safe floor
                continue; // exp(-50) is effectively zero, skip adding it.
            }

            sumOfExponentials += Math.exp(modeUtility);
        }

        if (sumOfExponentials == 0.0) {
            return MIN_LOGSUM;
        }

        double logsum = Math.log(sumOfExponentials);

        // Ensure the final logsum does not fall below the defined floor.
        return Math.max(logsum, MIN_LOGSUM);
    }

    // Helper class to hold data for each stratum within a group
    private static class PerStrataData {
        final DecisionStrata strata;
        final PrimaryLocationChoiceConfig.StrataConfigRecord strataConfig;
        final List<Person> persons;
        final Map<Id<Zone>, List<Person>> personsByZone;

        PerStrataData(Map.Entry<DecisionStrata, PrimaryLocationChoiceConfig.StrataConfigRecord> strataEntry) {
            this.strata = strataEntry.getKey();
            this.strataConfig = strataEntry.getValue();
            this.persons = new ArrayList<>();
            this.personsByZone = new HashMap<>();
        }
    }
}