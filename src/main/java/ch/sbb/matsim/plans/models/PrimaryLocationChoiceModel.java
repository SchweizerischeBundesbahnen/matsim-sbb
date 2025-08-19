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
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PrimaryLocationChoiceModel {

    private final Scenario scenario;
    private final Set<DecisionStrata> longTermStrataWork = Set.of(
            DecisionStrata.PRIMLOC_EMPL_NOABO_CAR,
            DecisionStrata.PRIMLOC_EMPL_GA_CAR,
            DecisionStrata.PRIMLOC_EMPL_VA_CAR,
            DecisionStrata.PRIMLOC_EMPL_NOABO_NOCAR,
            DecisionStrata.PRIMLOC_EMPL_GA_NOCAR,
            DecisionStrata.PRIMLOC_EMPL_VA_NOCAR
    );
    private final Zones zones;
    private static final double BETA_ATTRACTION = 1.0;
    private static final double BETA_LOGSUM = -1.0; // The negative sign matches the Python code (log(attr) - logsum)
    private final Matrices matrices;
    private final FormulaRepository formulaRepository;
    private final ExpressionEvaluator expressionEvaluator;
    private final PrimaryLocationChoiceConfig primaryLocationChoiceConfig;

    public PrimaryLocationChoiceModel(Scenario scenario) {
        this.scenario = scenario;
        this.zones = (Zones) scenario.getScenarioElement(Variables.ZONES);
        this.matrices = (Matrices) scenario.getScenarioElement(Variables.MATRICES);
        this.formulaRepository = (FormulaRepository) scenario.getScenarioElement("modeImpedanceFormulas");
        this.expressionEvaluator = new ExpressionEvaluator();
        this.primaryLocationChoiceConfig = (PrimaryLocationChoiceConfig) scenario.getScenarioElement("primaryLocationChoiceConfig");
    }

    public void run() {
        StrataPredicateFactory strataPredicateFactory = new StrataPredicateFactory();
        //1 sort persons by strata
        for (DecisionStrata strata : longTermStrataWork) {
            var strataPredicate = strataPredicateFactory.getPredicate(strata);
            //2 sort persons by zone
            var personsPerZone = scenario.getPopulation().getPersons().values().stream()
                    .filter(strataPredicate)
                    .collect(Collectors.groupingBy(
                            person -> Id.create(person.getAttributes().getAttribute(SynpopConstants.ZONE_ID).toString(), Zone.class))
                    );
            final Map<String, String> modeFormulas = formulaRepository.getModeFormulas("work", strata);
            //3 for each strata and zone, calculate the primary location choice probabilities
            for (var entry : personsPerZone.entrySet()) {
                Id<Zone> zoneId = entry.getKey();
                var personsInZone = entry.getValue();
                Zone homeZone = zones.getZone(zoneId);

                Map<Id<Zone>, Double> destinationUtilities = new HashMap<>();
                double totalUtilityExp = 0.0;

                for (Zone destinationZone : zones.getZones()) {
                    var travelMetrics = matrices.getTravelMetrics(homeZone.getId(), destinationZone.getId());
                    travelMetrics.put("from_acc_car", (Double) homeZone.getAttribute(ZonalAttributes.AT_CAR));
                    travelMetrics.put("to_acc_car", (Double) destinationZone.getAttribute(ZonalAttributes.AT_CAR));
                    travelMetrics.put("to_pc_car", (Double) destinationZone.getAttribute(ZonalAttributes.PC_CAR));
                    travelMetrics.put("from_acc_ride", (Double) homeZone.getAttribute(ZonalAttributes.AT_RIDE));
                    travelMetrics.put("to_acc_ride", (Double) destinationZone.getAttribute(ZonalAttributes.AT_RIDE));
                    travelMetrics.put("to_pc_ride", (Double) destinationZone.getAttribute(ZonalAttributes.PC_RIDE));

                    //calc zone-to-zone probabilities based on demand strata function
                    double logsum = calculateLogsum(modeFormulas, travelMetrics);
                    double kalif_ASC = matrices.getKalifMatrix(MatrixNames.KalifMatrices.KALIF_WORK).get(homeZone.getId(), destinationZone.getId());
                    double attraction = (Double) destinationZone.getAttribute(ZonalAttributes.JOBS_TOTAL);
                    // Skip zones with no attraction, as log(0) is undefined.
                    if (attraction <= 0) {
                        continue;
                    }
                    double utility = kalif_ASC + BETA_ATTRACTION * Math.log(attraction) + BETA_LOGSUM * logsum;
                    destinationUtilities.put(destinationZone.getId(), utility);
                    totalUtilityExp += Math.exp(utility);
                }
                // If no valid destinations were found, skip this group
                if (totalUtilityExp == 0.0) {
                    System.out.println("No valid destinations found for zone: " + zoneId + " in strata: " + strata);
                    continue;
                }
                // --- Step 3c: Convert utilities to probabilities (Softmax) ---
                Map<Id<Zone>, Double> destinationProbabilities = new HashMap<>();
                for (Map.Entry<Id<Zone>, Double> utilEntry : destinationUtilities.entrySet()) {
                    double probability = Math.exp(utilEntry.getValue()) / totalUtilityExp;
                    destinationProbabilities.put(utilEntry.getKey(), probability);
                }


                //4 for each person in this zone, select a primary location based on the probabilities
                for (var person : personsInZone) {
                    Id<Zone> chosenWorkZoneId = ChoiceUtils.selectFromProbabilities(destinationProbabilities);

                    // 5. Assign the selected primary location to the person
                    // This involves selecting a specific facility within the chosen zone, which is the next step.
                    // For now, we can store the chosen zone ID as a placeholder.
                    if (chosenWorkZoneId != null) {
                        person.getAttributes().putAttribute("work_zone_no", chosenWorkZoneId.toString());
                    }
                }
            }
            //5 assign the selected primary location to the person as a facility
        }

    }

    /**
     * Calculates the logsum for a given OD pair by evaluating all mode utilities.
     * The logsum represents the combined "utility of travel" considering all available modes.
     * Logsum = log( exp(U_car) + exp(U_pt) + ... )
     */
    private double calculateLogsum(Map<String, String> modeFormulas, Map<String, Double> travelMetrics) {
        double sumOfExponentials = 0.0;

        for (String formula : modeFormulas.values()) {
            // Your CSV uses -9999 for unavailable modes. We must handle this.
            if (formula.trim().equals("-9999")) {
                continue;
            }
            double modeUtility = expressionEvaluator.evaluate(formula, travelMetrics);
            sumOfExponentials += Math.exp(modeUtility);
        }

        // If no modes were available, the logsum is effectively negative infinity.
        // Return a very small number to avoid Math.log(0).
        if (sumOfExponentials == 0.0) {
            return -Double.MAX_VALUE;
        }

        return Math.log(sumOfExponentials);
    }


}
