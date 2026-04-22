package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.analysis.skims.FloatMatrix;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.ZonalAttributes;
import ch.sbb.matsim.plans.matrices.Matrices;
import ch.sbb.matsim.plans.matrices.MatrixNames;
import ch.sbb.matsim.plans.models.formulas.ModeImpedanceFormulaRepository;
import ch.sbb.matsim.plans.models.utils.AttributeUtils;
import ch.sbb.matsim.plans.models.utils.ExpressionEvaluator;
import ch.sbb.matsim.plans.synpop.DecisionStrata;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import com.google.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static ch.sbb.matsim.plans.models.utils.Calculations.calculateLogsum;

public class ZoneBasedAccessibilities implements ModelStep{
    private final Zones zones;
    private final Matrices matrices;
    private final ModeImpedanceFormulaRepository formulaRepository;

    @Inject
    public ZoneBasedAccessibilities(Scenario scenario, Matrices matrices, ModeImpedanceFormulaRepository formulaRepository ) {
        this.zones = (Zones) scenario.getScenarioElement(Variables.ZONES);
        this.matrices = matrices;
        this.formulaRepository = formulaRepository;
    }

    public void run() {
        List<Zone> allZones = zones.getZones();
        FloatMatrix<Id<Zone>> kalifWork = matrices.getKalifMatrix(MatrixNames.KalifMatrices.KALIF_WORK);
        ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

        for (String accessibilityType: List.of(ZonalAttributes.ACCSIB_CAR, ZonalAttributes.ACCSIB_PT, ZonalAttributes.ACCSIB_MUL)) {

            Map<String, String> modeFormulas = formulaRepository.getModeFormulas("99",
                    DecisionStrata.fromStrataName(accessibilityType));
            Map<Id<Zone>, Double> calculatedAccessibilities = new ConcurrentHashMap<>();

            zones.getZones().parallelStream().forEach(origin -> {
                if (origin == null) return;

                // Origin attraction
                double origPop = AttributeUtils.getAsDouble(origin, ZonalAttributes.POP_TOTAL, 0.0);
                double origFte = AttributeUtils.getAsDouble(origin, ZonalAttributes.FTE_TOTAL, 0.0);
                double zoneAttraction = origPop + 2.54 * origFte;

                double totalAccessibility = 0.0;

                for (Zone destination : allZones) {
                    if (destination == null) continue;
                    double destPop = AttributeUtils.getAsDouble(destination, ZonalAttributes.POP_TOTAL, 0.0);
                    double destFte = AttributeUtils.getAsDouble(destination, ZonalAttributes.FTE_TOTAL, 0.0);

                    double attraction = destPop + 2.54 * destFte;

                    if (attraction <= 0.0) continue;

                    Map<String, Double> travelMetrics = matrices.getTravelAndAccessibilityMetrics(origin, destination);

                    double logsum = calculateLogsum(modeFormulas, travelMetrics, expressionEvaluator);

                    double kalif = kalifWork.get(origin.getId(), destination.getId());
                    double expsum = Math.exp(logsum + 0.5 * kalif);

                    totalAccessibility += (expsum * attraction);
                }

                calculatedAccessibilities.put(origin.getId(), Math.max(totalAccessibility, zoneAttraction));
            });
            for (Zone origin : allZones) {
                Double accValue = calculatedAccessibilities.get(origin.getId());
                if (accValue != null) {
                    origin.getAttributes().putAttribute(accessibilityType, accValue);
                }
            }
        }
    }



    public String getName() {
        return MOBiPlansRunner.ZONE_BASED_ACCESSIBILITIES;
    }
}
