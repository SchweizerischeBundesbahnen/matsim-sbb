package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.matrices.Matrices;
import ch.sbb.matsim.plans.matrices.MatrixNames;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.config.PrimaryLocationChoiceConfig;
import ch.sbb.matsim.plans.models.formulas.ModeImpedanceFormulaRepository;
import ch.sbb.matsim.plans.models.utils.ChoiceUtils;
import ch.sbb.matsim.plans.models.utils.ExpressionEvaluator;
import ch.sbb.matsim.plans.models.utils.StrataPredicateFactory;
import ch.sbb.matsim.plans.models.utils.WriteDataRelationAsMatrix;
import ch.sbb.matsim.plans.models.utils.analysis.AnalyzeDistanceDistribution;
import ch.sbb.matsim.plans.synpop.DecisionStrata;
import ch.sbb.matsim.plans.synpop.SynPopUtils;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import jakarta.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.facilities.ActivityFacility;

import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static ch.sbb.matsim.plans.models.utils.Calculations.calculateLogsum;

public class PrimaryLocationChoiceModel implements ModelStep{

    private static final double BETA_ATTRACTION = 1.0;
    private static final double BETA_LOGSUM = 1.0;
    private final Scenario scenario;
    private final Zones zones;
    private final Matrices matrices;
    private final ModeImpedanceFormulaRepository formulaRepository;
    private final ExpressionEvaluator expressionEvaluator;
    private final PrimaryLocationChoiceConfig primaryLocationChoiceConfig;
    private final StrataPredicateFactory strataPredicateFactory;
    private static final Logger logger = LogManager.getLogger(PrimaryLocationChoiceModel.class);
    private final ForkJoinPool customThreadPool;
    private final boolean writeDetailedAnalysis;
    private final String outputDir;
    private final MobilityToolOwnershipModel mobilityToolOwnershipModel;


    @Inject
    public PrimaryLocationChoiceModel(Scenario scenario, MOBiPlansConfig moBiPlansConfig, Zones zones, Matrices matrices, ModeImpedanceFormulaRepository formulaRepository, MobilityToolOwnershipModel mobilityToolOwnershipModel, StrataPredicateFactory strataPredicateFactory ) {
        this.scenario = scenario;
        this.zones =  zones;
        this.matrices = matrices;
        this.formulaRepository = formulaRepository;
        this.primaryLocationChoiceConfig = PrimaryLocationChoiceConfig.readFromYamlFile(Paths.get(moBiPlansConfig.data().modelBaseDir()).resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_CONFIG).resolve(MOBiPlansConfig.PRIMARY_LOCATION_CHOICE_CONFIG_YAML).toString());
        this.strataPredicateFactory = strataPredicateFactory;
        this.expressionEvaluator = new ExpressionEvaluator();
        this.writeDetailedAnalysis = moBiPlansConfig.models().write_analysis();
        this.customThreadPool = new ForkJoinPool(moBiPlansConfig.models().threads());
        this.outputDir = moBiPlansConfig.data().outputBaseDir();
        this.mobilityToolOwnershipModel = mobilityToolOwnershipModel;

    }

    @Override
    public void run() {
        try {
            logger.info("Starting Primary Location Choice Model...");
            Instant startRun = Instant.now();
            for (PrimaryLocationChoiceConfig.ActivityConfigRecord activityConfig : primaryLocationChoiceConfig.getPrimaryLocationChoiceConfigs()) {
                runForActivity(activityConfig);
            }
            mobilityToolOwnershipModel.rerunForStudentsAfterPrimaryLocationChoice();
            logger.info("Primary Location Choice Model finished in {}.", Duration.between(startRun, Instant.now()));

        } finally {
            customThreadPool.shutdown();
        }
        if (writeDetailedAnalysis) {
            WriteDataRelationAsMatrix.computeAndWriteMatrix(zones, "amr_id", scenario, "zone_id", "work_zone_no", outputDir + "/work_zones_amr_matrix.csv");
            WriteDataRelationAsMatrix.computeAndWriteMatrix(zones, "amgr_id", scenario, "zone_id", "work_zone_no", outputDir + "/work_zones_amgr_matrix.csv");
            WriteDataRelationAsMatrix.computeAndWriteMatrix(zones, "amgr_id", scenario, "zone_id", "school_zone_no", outputDir + "/edu_zones_amgr_matrix.csv");
            WriteDataRelationAsMatrix.computeAndWriteMatrix(zones, "amr_id", scenario, "zone_id", "school_zone_no", outputDir + "/edu_zones_amr_matrix.csv");
            AnalyzeDistanceDistribution.writeDistanceDistribution(outputDir + "/distance_distribution_long_term_location_choice.csv", scenario.getPopulation());
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

    private double calculateUtility(Zone homeZone, Zone destinationZone, PrimaryLocationChoiceConfig.StrataConfigRecord strataConfig, Map<String, String> modeFormulas) {
        // Get the attraction value for the destination
        double attraction = ((Number) destinationZone.getAttribute(strataConfig.attractionAttributeName())).doubleValue();
        if (attraction <= 0) {
            return -Double.MAX_VALUE; // Use a very negative number to represent zero probability
        }

        var travelMetrics = matrices.getTravelAndAccessibilityMetrics(homeZone, destinationZone);
        double logsum = calculateLogsum(modeFormulas, travelMetrics, expressionEvaluator);
        if (Double.isNaN(logsum) || Double.isInfinite(logsum)) {
            return -Double.MAX_VALUE;
        }

        double kalif_ASC = matrices.getKalifMatrix(strataConfig.kalifMatrixName()).get(homeZone.getId(), destinationZone.getId());
        double matrixFactor = strataConfig.getMatrixFactor();

        return (kalif_ASC * matrixFactor) + BETA_ATTRACTION * Math.log(attraction) + BETA_LOGSUM * logsum;
    }

    // package-private for unit testing
    static double[][] runIPF(double[][] utilityMatrix, double[] rowTargets, double[] colTargets, int maxIterations) {
        int numOrigins = utilityMatrix.length;
        if (numOrigins == 0) return new double[0][0];
        int numDests = utilityMatrix[0].length;

        // --- Step 1: Initialization ---
        // Make copies of the original targets, as the algorithm will modify them.
        double[] colTargetsCurrent = Arrays.copyOf(colTargets, numDests);
        double[] rowTargetsCurrent = Arrays.copyOf(rowTargets, numOrigins);
        final double[] colTargetsOriginal = Arrays.copyOf(colTargets, numDests);
        final double[] rowTargetsOriginal = Arrays.copyOf(rowTargets, numOrigins);

        // Scale the initial column targets to match the total production.
        double totalRowTarget = Arrays.stream(rowTargetsOriginal).sum();
        double totalColTarget = Arrays.stream(colTargetsOriginal).sum();
        if (totalColTarget > 0) {
            double scaleFactor = totalRowTarget / totalColTarget;
            for (int j = 0; j < numDests; j++) {
                colTargetsCurrent[j] *= scaleFactor;
            }
        } else {
            logger.warn("Sum of column targets is zero. Cannot perform IPF.");
            return new double[numOrigins][numDests];
        }

        // Pre-calculate the exp(utility) matrix once.
        double[][] expUtilityMatrix = new double[numOrigins][numDests];
        for(int i=0; i<numOrigins; i++) {
            for(int j=0; j<numDests; j++) {
                // Handle large negative utilities to prevent underflow to 0.0, using the stable softmax trick
                if(utilityMatrix[i][j] == -Double.MAX_VALUE) {
                    expUtilityMatrix[i][j] = 0.0;
                } else {
                    expUtilityMatrix[i][j] = Math.exp(utilityMatrix[i][j]);
                }
            }
        }


        // --- Step 2: The Main Balancing Loop (Furness Method) ---
        for (int iter = 0; iter < maxIterations; iter++) {
            // --- This block is equivalent to the Python code's main loop body ---

            // 1. Calculate the trip matrix based on current balancing factors
            double[][] trips = new double[numOrigins][numDests];
            double[] computedRowSums = new double[numOrigins];
            double[] computedColSums = new double[numDests];

            for(int i = 0; i < numOrigins; i++) {
                double expUtilSumForRow = 0.0;
                for (int j = 0; j < numDests; j++) {
                    expUtilSumForRow += expUtilityMatrix[i][j] * colTargetsCurrent[j] / colTargetsOriginal[j];
                }

                if (expUtilSumForRow <= 0) expUtilSumForRow = 1.0; // Guard against division by zero

                for (int j = 0; j < numDests; j++) {
                    trips[i][j] = rowTargetsCurrent[i] * (expUtilityMatrix[i][j] * colTargetsCurrent[j] / colTargetsOriginal[j]) / expUtilSumForRow;
                }
            }

            // 2. Sum the columns and rows of the calculated trip matrix
            for(int i = 0; i < numOrigins; i++) {
                for(int j = 0; j < numDests; j++) {
                    computedColSums[j] += trips[i][j];
                    computedRowSums[i] += trips[i][j];
                }
            }

            // 3. Update the column and row balancing factors for the NEXT iteration
            for(int j=0; j<numDests; j++) {
                if(computedColSums[j] == 0) computedColSums[j] = 1.0; // Guard
                colTargetsCurrent[j] = colTargetsCurrent[j] * (colTargetsOriginal[j] / computedColSums[j]);
            }
            for(int i=0; i<numOrigins; i++) {
                if(computedRowSums[i] == 0) computedRowSums[i] = 1.0; // Guard
                rowTargetsCurrent[i] = rowTargetsCurrent[i] * (rowTargetsOriginal[i] / computedRowSums[i]);
            }
        }

        // --- Step 3: Final Probability Calculation ---
        // This is the final step from the Python `calculate_IPF` function.
        double[][] probabilities = new double[numOrigins][numDests];
        for (int i = 0; i < numOrigins; i++) {
            double rowSum = 0;
            for (int j = 0; j < numDests; j++) {
                probabilities[i][j] = expUtilityMatrix[i][j] * colTargetsCurrent[j] / colTargetsOriginal[j];
                rowSum += probabilities[i][j];
            }

            if (rowSum > 0) {
                for (int j = 0; j < numDests; j++) {
                    probabilities[i][j] /= rowSum;
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
            logger.info("  - Found {} persons in {} active origin zones for strata '{}'", personCount, personsByZoneInStrata.size(), data.strata.getStrataName());


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
                .filter(zone -> zone.getAttribute(attractionAttribute) instanceof Number n && n.doubleValue() > 0)
                .toList();

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
        double[][] combinedUtilityMatrix = new double[numRows][numDests];
        double[] combinedRowTargets = new double[numRows];
        final AtomicInteger progressCounter = new AtomicInteger();
        final int totalRows = numRows;
        final int reportInterval = Math.max(1, totalRows / 20);

        Runnable matrixBuildTask = () -> IntStream.range(0, numRows).parallel().forEach(i -> {
            Id<Zone> originZoneId = matrixRowMapping.get(i).getKey();
            Zone originZone = zones.getZone(originZoneId);
            PerStrataData strataData = matrixRowMapping.get(i).getValue();
            combinedRowTargets[i] = strataData.personsByZone.get(originZoneId).size();
            final Map<String, String> modeFormulas = formulaRepository.getModeFormulas(activityConfig.activityName(), strataData.strata);

            for (int j = 0; j < numDests; j++) {
                Zone destinationZone = validDestinationZones.get(j);
                combinedUtilityMatrix[i][j] = calculateUtility(originZone, destinationZone, strataData.strataConfig, modeFormulas);
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
        int ipfIterations = perStrataDataList.getFirst().strataConfig.ipfIterations();
        logger.info("[Step 5/6] Running IPF with {} iterations...", ipfIterations);
        Instant startIpf = Instant.now();
        double[][] combinedProbabilityMatrix = runIPF(combinedUtilityMatrix, combinedRowTargets, colTargets, ipfIterations);
        logger.info("[Step 5/6] Finished IPF. Took {}.", Duration.between(startIpf, Instant.now()));

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
        Runnable facilityAllocation = getFacilityAllocation(activityConfig, personsByActivityZone, activityFacilityByZone);
        try {
            customThreadPool.submit(facilityAllocation).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Facility assignment failed", e);
        }

        logger.info("[Step 7/7] Finished assigning facilities. Took {}.", Duration.between(startAllocate, Instant.now()));
        logger.info("--- Finished attraction group '{}' in {} ---", attractionAttribute, Duration.between(startGroup, Instant.now()));
    }

    private Runnable getFacilityAllocation(PrimaryLocationChoiceConfig.ActivityConfigRecord activityConfig, Map<Id<Zone>, List<Person>> personsByActivityZone, Map<Id<Zone>, ? extends List<? extends ActivityFacility>> activityFacilityByZone) {
        String work_or_school_marker = activityConfig.personResultAttributeName().split("_")[0];
        final String distanceName = "network_distance_"+ work_or_school_marker;
        final String zonalAttributeName = work_or_school_marker+"_zone";
        return () -> personsByActivityZone.forEach((zoneId, people) -> {
            var facilitiesInZone = activityFacilityByZone.get(zoneId);
            if (facilitiesInZone == null || facilitiesInZone.isEmpty()) {
                throw new RuntimeException("No facilities found in zone " + zoneId + " for activity allocation.");
            }
            Map<Id<ActivityFacility>, Double> facilityProbabilities = new HashMap<>();
            facilitiesInZone.forEach(facility -> {
                double weight = ((Number) facility.getAttributes().getAttribute(SynpopConstants.FTE_ENDO)).doubleValue();
                if (weight<=1 )
                    weight = 1.0;
                facilityProbabilities.put(facility.getId(), weight);
            });
            if (facilitiesInZone.isEmpty()) {
                logger.warn("No facilities with valid weights found in zone " + zoneId + " for activity allocation. People in this zone will not be assigned a facility.");
            }
            for (Person person : people) {
                var activityFacilityId = ChoiceUtils.selectFromProbabilities(facilityProbabilities, SynPopUtils.getRandom(person.getId(), getClass().getName() + "_facility"));
                if (activityFacilityId != null) {
                    person.getAttributes().putAttribute(activityConfig.activityName() + "_facility", scenario.getActivityFacilities().getFacilities().get(activityFacilityId));
                } else {
                    logger.warn("Could not choose a facility for person " + person.getId() + " despite valid probabilities. Zone ID: " + zoneId);
                }
                Zone homeZone = (Zone) person.getAttributes().getAttribute(SynpopConstants.ZONE);
                var travelMetrics = matrices.getTravelMetrics(homeZone.getId(), zoneId).get(MatrixNames.TravelMetricsMatrices.CAR_NET_DISTANCE.name().toLowerCase());
                // Add the distance to the chosen zone and the zone as an attribute for later analysis and models
                person.getAttributes().putAttribute(distanceName, travelMetrics);
                person.getAttributes().putAttribute(zonalAttributeName,zones.getZone(zoneId));
            }

        });
    }

    @Override
    public String getName() {
        return MOBiPlansRunner.PRIMARY_LOCATION_CHOICE;
    }

    // Helper class to hold data for each stratum within a group
    public static class PerStrataData {
        public final DecisionStrata strata;
        final PrimaryLocationChoiceConfig.StrataConfigRecord strataConfig;
        final List<Person> persons;
        final Map<Id<Zone>, List<Person>> personsByZone;

        public PerStrataData(Map.Entry<DecisionStrata, PrimaryLocationChoiceConfig.StrataConfigRecord> strataEntry) {
            this.strata = strataEntry.getKey();
            this.strataConfig = strataEntry.getValue();
            this.persons = new ArrayList<>();
            this.personsByZone = new HashMap<>();
        }
    }
}