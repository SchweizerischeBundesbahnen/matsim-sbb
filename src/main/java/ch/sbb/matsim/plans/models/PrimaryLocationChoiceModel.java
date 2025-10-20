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

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PrimaryLocationChoiceModel {

    private static final double MIN_LOGSUM = -20.0;
    private final Scenario scenario;
    private final Zones zones;
    private final Matrices matrices;
    private static final double BETA_ATTRACTION = 1.0;
    private final FormulaRepository formulaRepository;
    private final ExpressionEvaluator expressionEvaluator;
    private final PrimaryLocationChoiceConfig primaryLocationChoiceConfig;
    private static final double BETA_LOGSUM = -1.0;
    private final StrataPredicateFactory strataPredicateFactory;
    private final Logger logger = LogManager.getLogger(PrimaryLocationChoiceModel.class);

    public PrimaryLocationChoiceModel(Scenario scenario) {
        this.scenario = scenario;
        this.zones = (Zones) scenario.getScenarioElement(Variables.ZONES);
        this.matrices = (Matrices) scenario.getScenarioElement(Variables.MATRICES);
        this.formulaRepository = (FormulaRepository) scenario.getScenarioElement("modeImpedanceFormulas");
        this.primaryLocationChoiceConfig = (PrimaryLocationChoiceConfig) scenario.getScenarioElement("primaryLocationChoiceConfig");
        this.strataPredicateFactory = new StrataPredicateFactory();
        this.expressionEvaluator = new ExpressionEvaluator();
    }

    private void runForActivity(PrimaryLocationChoiceConfig.ActivityConfigRecord activityConfig) {
        logger.info("Running primary location choice for activity: " + activityConfig.activityName());

        // Group strata by their shared attraction attribute. This is the core change.
        Map<String, List<Map.Entry<DecisionStrata, PrimaryLocationChoiceConfig.StrataConfigRecord>>> strataByAttraction =
                activityConfig.strataConfigs().entrySet().stream()
                        .collect(Collectors.groupingBy(entry -> entry.getValue().attractionAttributeName()));

        for (Map.Entry<String, List<Map.Entry<DecisionStrata, PrimaryLocationChoiceConfig.StrataConfigRecord>>> attractionGroup : strataByAttraction.entrySet()) {
            logger.info("Processing attraction group: " + attractionGroup.getKey());
            runForAttractionGroup(activityConfig, attractionGroup.getKey(), attractionGroup.getValue());
        }
    }

    public void run() {
        for (PrimaryLocationChoiceConfig.ActivityConfigRecord activityConfig : primaryLocationChoiceConfig.getPrimaryLocationChoiceConfigs()) {
            runForActivity(activityConfig);
        }
    }

    private void runForAttractionGroup(PrimaryLocationChoiceConfig.ActivityConfigRecord activityConfig, String attractionAttribute, List<Map.Entry<DecisionStrata, PrimaryLocationChoiceConfig.StrataConfigRecord>> strataGroup) {
        logger.info("Processing attraction group: " + attractionAttribute);

        // 1. Prepare data for each stratum and create a unified list of all unique people/zone combinations
        List<PerStrataData> perStrataDataList = new ArrayList<>();
        // This list will map directly to the rows of our combined matrix
        List<Map.Entry<Id<Zone>, PerStrataData>> matrixRowMapping = new ArrayList<>();

        for (var strataEntry : strataGroup) {
            PerStrataData data = new PerStrataData(strataEntry);
            Predicate<Person> strataPredicate = strataPredicateFactory.getPredicate(data.strata);
            logger.info("Stratum: " + data.strata.getStrataName() + " - Filtering persons...");
            // Filter persons and group them by their home zone
            Map<Id<Zone>, List<Person>> personsByZoneInStrata = scenario.getPopulation().getPersons().values().stream()
                    .filter(strataPredicate)
                    .collect(Collectors.groupingBy(p -> Id.create(p.getAttributes().getAttribute(SynpopConstants.ZONE_ID).toString(), Zone.class)));

            if (personsByZoneInStrata.isEmpty()) {
                continue; // Skip strata with no persons
            }

            data.personsByZone.putAll(personsByZoneInStrata);
            perStrataDataList.add(data);

            // For each zone in this stratum, add an entry to our global row map
            for (Id<Zone> zoneId : personsByZoneInStrata.keySet()) {
                matrixRowMapping.add(Map.entry(zoneId, data));
            }
        }

        if (perStrataDataList.isEmpty() || matrixRowMapping.isEmpty()) {
            logger.warn("No persons found for any strata in attraction group: " + attractionAttribute);
            return;
        }

        // 2. Define consistent destination zone ordering
        List<Zone> destinationZones = new ArrayList<>(zones.getZones());
        int numRows = matrixRowMapping.size();
        int numDests = destinationZones.size();

        // 3. Build the combined friction matrix and row targets directly
        double[][] combinedFrictionMatrix = new double[numRows][numDests];
        double[] combinedRowTargets = new double[numRows];

        for (int i = 0; i < numRows; i++) {
            Id<Zone> originZoneId = matrixRowMapping.get(i).getKey();
            Zone originZone = zones.getZone(originZoneId);
            PerStrataData strataData = matrixRowMapping.get(i).getValue();

            combinedRowTargets[i] = strataData.personsByZone.get(originZoneId).size();

            final Map<String, String> modeFormulas = formulaRepository.getModeFormulas(activityConfig.activityName(), strataData.strata);

            for (int j = 0; j < numDests; j++) {
                Zone destinationZone = destinationZones.get(j);
                combinedFrictionMatrix[i][j] = calculateFriction(originZone, destinationZone, strataData.strataConfig, modeFormulas);
                if (destinationZone.getId().toString().equals("311101001")) {

                }
            }
        }

        // 4. Get and scale column targets (attractions)
        double[] colTargets = destinationZones.stream()
                .mapToDouble(zone -> ((Number) zone.getAttribute(attractionAttribute)).doubleValue())
                .toArray();
        logger.info("--- IPF DIAGNOSTICS FOR ATTRACTION GROUP: " + attractionAttribute + " ---");
        logger.info("Attraction attribute name being used: '" + attractionAttribute + "'");

        double totalColTargets = Arrays.stream(colTargets).sum();
        logger.info("Sum of all column targets (attractions) found: " + totalColTargets);

        double totalRowTargets = Arrays.stream(combinedRowTargets).sum();
        logger.info("Sum of all row targets (persons): " + totalRowTargets);
        logger.info("-----------------------------------------------------------------");
        if (totalColTargets > 0) {
            double scaleFactor = totalRowTargets / totalColTargets;
            for (int j = 0; j < colTargets.length; j++) {
                colTargets[j] *= scaleFactor;
            }
        } else {
            logger.warn("Total attraction for group '" + attractionAttribute + "' is zero. All probabilities will be zero.");
        }


        // 5. Run IPF on the combined matrix
        int ipfIterations = perStrataDataList.get(0).strataConfig.ipfIterations();
        double[][] combinedProbabilityMatrix = runIPF(combinedFrictionMatrix, combinedRowTargets, colTargets, ipfIterations);


        // 6. Assign locations using the now-correctly-indexed probability matrix
        for (int i = 0; i < numRows; i++) {
            Id<Zone> originZoneId = matrixRowMapping.get(i).getKey();
            PerStrataData strataData = matrixRowMapping.get(i).getValue();
            List<? extends Person> personsInZone = strataData.personsByZone.get(originZoneId);

            Map<Id<Zone>, Double> destinationProbabilities = new HashMap<>();
            for (int j = 0; j < numDests; j++) {
                destinationProbabilities.put(destinationZones.get(j).getId(), combinedProbabilityMatrix[i][j]);
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
        }
    }

    private double calculateFriction(Zone homeZone, Zone destinationZone, PrimaryLocationChoiceConfig.StrataConfigRecord strataConfig, Map<String, String> modeFormulas) {
        var travelMetrics = getTravelAndAccessibilityMetrics(homeZone, destinationZone);
        double logsum = calculateLogsum(modeFormulas, travelMetrics);

        // Check for NaN or Infinity in logsum, which can poison the matrix
        if (Double.isNaN(logsum) || Double.isInfinite(logsum)) {
//            logger.warn("Invalid logsum " + logsum + " for OD pair " + homeZone.getId() + " -> " + destinationZone.getId());
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

    private Map<String, Double> getTravelAndAccessibilityMetrics(Zone origin, Zone destination) {
        var travelMetrics = matrices.getTravelMetrics(origin.getId(), destination.getId());
        travelMetrics.put("va_impedances", (double) matrices.getKalifMatrix(MatrixNames.KalifMatrices.VA_IMPEDANCES).get(origin.getId(), destination.getId()));
        travelMetrics.put("from_acc_car", (double) ((Integer) origin.getAttribute(ZonalAttributes.AT_CAR)));
        travelMetrics.put("to_acc_car", (double) ((Integer) destination.getAttribute(ZonalAttributes.AT_CAR)));
        travelMetrics.put("to_pc_car", (Double) destination.getAttribute(ZonalAttributes.PC_CAR));
        travelMetrics.put("from_acc_ride", (double) ((Integer) origin.getAttribute(ZonalAttributes.AT_RIDE)));
        travelMetrics.put("to_acc_ride", (double) ((Integer) destination.getAttribute(ZonalAttributes.AT_RIDE)));
        travelMetrics.put("to_pc_ride", (Double) destination.getAttribute(ZonalAttributes.PC_RIDE));
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
            return MIN_LOGSUM; // Return the capped value instead of -Infinity
        }

        double logsum = Math.log(sumOfExponentials);

        // --- THIS IS THE CRITICAL FIX ---
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