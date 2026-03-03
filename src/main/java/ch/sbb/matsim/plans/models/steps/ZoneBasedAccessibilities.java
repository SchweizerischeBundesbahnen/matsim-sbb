package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.ZonalAttributes;
import ch.sbb.matsim.plans.matrices.Matrices;
import ch.sbb.matsim.plans.matrices.MatrixNames;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.formulas.ModeImpedanceFormulaRepository;
import ch.sbb.matsim.plans.models.utils.ExpressionEvaluator;
import ch.sbb.matsim.plans.synpop.DecisionStrata;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import com.google.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class ZoneBasedAccessibilities implements ModelStep{
    private final String name = MOBiPlansRunner.ZONE_BASED_ACCESSIBILITIES;
    private final static Logger logger = LogManager.getLogger(ZoneBasedAccessibilities.class);
    private final MOBiPlansConfig config;
    private final Scenario scenario;
    private final Zones zones;
    private final Matrices matrices;
    private final ModeImpedanceFormulaRepository formulaRepository;
    private final ExpressionEvaluator expressionEvaluator;
    private static final double MIN_LOGSUM = -20.0;

    @Inject
    public ZoneBasedAccessibilities(MOBiPlansConfig config, Scenario scenario, Matrices matrices, ModeImpedanceFormulaRepository formulaRepository ) {
        this.config = config;
        this.scenario = scenario;
        this.zones = (Zones) scenario.getScenarioElement(Variables.ZONES);
        this.matrices = matrices;
        this.formulaRepository = formulaRepository;
        this.expressionEvaluator = new ExpressionEvaluator();

    }

    public void run() {
        List.of(ZonalAttributes.ACCSIB_CAR, ZonalAttributes.ACCSIB_PT, ZonalAttributes.ACCSIB_MUL).stream().forEach(accessibilityType -> {
            zones.getZones().parallelStream().forEach(origin -> {
                AtomicReference<Double> zoneAccessibility = new AtomicReference<>(0.0);
                zones.getZones().parallelStream().forEach(destination -> {
                    double attraction = destination != null ? ((Number) destination.getAttribute("pop_total")).doubleValue() : 0 + 2.54 * (destination != null ? ((Number) destination.getAttribute("fte_total")).doubleValue() : 0);
                    Map<String, Double> travelMetrics = getTravelAndAccessibilityMetrics(origin, destination);
                    double logsum = calculateLogsum(formulaRepository.getModeFormulas("99", DecisionStrata.fromStrataName(accessibilityType)),  travelMetrics);
                    double expsum = Math.exp(logsum + 0.5 * matrices
                            .getKalifMatrix(MatrixNames.KalifMatrices.KALIF_WORK)
                            .get(origin.getId(), destination.getId()));
                    zoneAccessibility.updateAndGet(v -> new Double((double) (v + expsum * attraction)));
                });
                origin.getAttributes().putAttribute(accessibilityType, zoneAccessibility.get());
            });
        });
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
            double modeUtility = 0.0;
            try {
                modeUtility = expressionEvaluator.evaluate(formula, travelMetrics);
            } catch (Exception e) {
                continue;
            }


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

    private static Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        throw new IllegalArgumentException("Cannot convert value to Double: " + value + " (" + value.getClass() + ")");
    }


    public String getName() {
        return name;
    }
}
