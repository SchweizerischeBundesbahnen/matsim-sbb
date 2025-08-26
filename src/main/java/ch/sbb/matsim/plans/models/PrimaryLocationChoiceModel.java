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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PrimaryLocationChoiceModel {

    private final Scenario scenario;
    private final Zones zones;
    private final Matrices matrices;
    // Model parameters are kept here
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
        this.primaryLocationChoiceConfig = (PrimaryLocationChoiceConfig) scenario.getScenarioElement("primaryLocationChoiceConfig"); // Store the config passed in
        this.strataPredicateFactory = new StrataPredicateFactory();
        this.expressionEvaluator = new ExpressionEvaluator();
    }

    public void run() {
        for (PrimaryLocationChoiceConfig.ActivityConfigRecord activityConfig : primaryLocationChoiceConfig.getPrimaryLocationChoiceConfigs()) {
            runForActivity(activityConfig);
        }
    }

    /**
     * Executes the location choice model for a single activity configuration record.
     *
     * @param activityConfig The configuration specifying which strata, attributes, etc., to use.
     */
    private void runForActivity(PrimaryLocationChoiceConfig.ActivityConfigRecord activityConfig) {
        logger.info("Running primary location choice for activity: " + activityConfig.activityName());
        for (Map.Entry<DecisionStrata, PrimaryLocationChoiceConfig.StrataConfigRecord> strataEntry : activityConfig.strataConfigs().entrySet()) {
            DecisionStrata strata = strataEntry.getKey();
            PrimaryLocationChoiceConfig.StrataConfigRecord strataConfig = strataEntry.getValue();
            var strataPredicate = strataPredicateFactory.getPredicate(strata);
            var personsPerZone = scenario.getPopulation().getPersons().values().stream()
                    .filter(strataPredicate)
                    .collect(Collectors.groupingBy(
                            person -> Id.create(person.getAttributes().getAttribute(SynpopConstants.ZONE_ID).toString(), Zone.class))
                    );

            if (personsPerZone.isEmpty()) {
                continue; // No persons in this strata, move to the next
            }

            final Map<String, String> modeFormulas = formulaRepository.getModeFormulas(activityConfig.activityName(), strata);
            for (var entry : personsPerZone.entrySet()) {
                Id<Zone> homeZoneId = entry.getKey();
                List<? extends Person> personsInZone = entry.getValue();
                Zone homeZone = zones.getZone(homeZoneId);

                Map<Id<Zone>, Double> destinationProbabilities = calculateDestinationProbabilities(homeZone, modeFormulas, strataConfig);

                if (destinationProbabilities.isEmpty()) {
                    logger.warn("No valid destinations found for zone: " + homeZoneId + " in strata: " + strata);
                    continue;
                }

                for (var person : personsInZone) {
                    Id<Zone> chosenZoneId = ChoiceUtils.selectFromProbabilities(destinationProbabilities, SynPopUtils.getRandom(person.getId(), getClass().getName()));
                    if (chosenZoneId != null) {
                        // The result attribute is now correctly read from the config
                        person.getAttributes().putAttribute(activityConfig.personResultAttributeName(), chosenZoneId.toString());
                    }
                }
            }
        }
    }

    /**
     * Calculates probabilities, now guided by the detailed StrataConfigRecord.
     */
    private Map<Id<Zone>, Double> calculateDestinationProbabilities(Zone homeZone, Map<String, String> modeFormulas, PrimaryLocationChoiceConfig.StrataConfigRecord strataConfig) {
        Map<Id<Zone>, Double> destinationUtilities = new HashMap<>();
        double totalUtilityExp = 0.0;

        for (Zone destinationZone : zones.getZones()) {
            double attraction = ((Number) destinationZone.getAttribute(strataConfig.attractionAttributeName())).doubleValue();
            if (attraction <= 0) continue;

            var travelMetrics = getTravelAndAccessibilityMetrics(homeZone, destinationZone);
            double logsum = calculateLogsum(modeFormulas, travelMetrics);

            double kalif_ASC = matrices.getKalifMatrix(strataConfig.kalifMatrixName()).get(homeZone.getId(), destinationZone.getId());
            double matrixFactor = strataConfig.getMatrixFactor();
            double utility = (kalif_ASC * matrixFactor) + BETA_ATTRACTION * Math.log(attraction) + BETA_LOGSUM * logsum;
            destinationUtilities.put(destinationZone.getId(), utility);
            totalUtilityExp += Math.exp(utility);
        }

        if (totalUtilityExp == 0.0) return Map.of();
        Map<Id<Zone>, Double> destinationProbabilities = new HashMap<>();
        for (Map.Entry<Id<Zone>, Double> utilEntry : destinationUtilities.entrySet()) {
            destinationProbabilities.put(utilEntry.getKey(), Math.exp(utilEntry.getValue()) / totalUtilityExp);
        }
        return destinationProbabilities;
    }

    /**
     * Gathers all necessary travel time and accessibility metrics for an OD pair.
     */
    private Map<String, Double> getTravelAndAccessibilityMetrics(Zone origin, Zone destination) {
        var travelMetrics = matrices.getTravelMetrics(origin.getId(), destination.getId());
        travelMetrics.put("va_impedances", Double.valueOf(matrices.getKalifMatrix(MatrixNames.KalifMatrices.VA_IMPEDANCES).get(origin.getId(), destination.getId())));
        travelMetrics.put("from_acc_car", Double.valueOf((Integer) origin.getAttribute(ZonalAttributes.AT_CAR)));
        travelMetrics.put("to_acc_car", Double.valueOf((Integer) destination.getAttribute(ZonalAttributes.AT_CAR)));
        travelMetrics.put("to_pc_car", (Double) destination.getAttribute(ZonalAttributes.PC_CAR));
        travelMetrics.put("from_acc_ride", Double.valueOf((Integer) origin.getAttribute(ZonalAttributes.AT_RIDE)));
        travelMetrics.put("to_acc_ride", Double.valueOf((Integer) destination.getAttribute(ZonalAttributes.AT_RIDE)));
        travelMetrics.put("to_pc_ride", (Double) destination.getAttribute(ZonalAttributes.PC_RIDE));
        return travelMetrics;
    }

    /**
     * Calculates the logsum for a given OD pair.
     */
    private double calculateLogsum(Map<String, String> modeFormulas, Map<String, Double> travelMetrics) {
        double sumOfExponentials = 0.0;
        for (String formula : modeFormulas.values()) {
            if (formula.trim().equals("-9999")) continue;
            //System.out.println("Evaluating formula: " + formula + " with metrics: " + travelMetrics);
            double modeUtility = expressionEvaluator.evaluate(formula, travelMetrics);
            sumOfExponentials += Math.exp(modeUtility);
        }
        return sumOfExponentials == 0.0 ? -Double.MAX_VALUE : Math.log(sumOfExponentials);
    }
}