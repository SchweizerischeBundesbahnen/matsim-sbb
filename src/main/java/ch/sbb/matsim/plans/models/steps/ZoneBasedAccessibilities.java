package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.analysis.skims.FloatMatrix;
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
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.DoubleAdder;

import static ch.sbb.matsim.plans.models.utils.Calculations.calculateLogsum;

public class ZoneBasedAccessibilities implements ModelStep{
    private final String name = MOBiPlansRunner.ZONE_BASED_ACCESSIBILITIES;
    private final static Logger logger = LogManager.getLogger(ZoneBasedAccessibilities.class);
    private final MOBiPlansConfig config;
    private final Scenario scenario;
    private final Zones zones;
    private final Matrices matrices;
    private final ModeImpedanceFormulaRepository formulaRepository;

    @Inject
    public ZoneBasedAccessibilities(MOBiPlansConfig config, Scenario scenario, Matrices matrices, ModeImpedanceFormulaRepository formulaRepository ) {
        this.config = config;
        this.scenario = scenario;
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

            zones.getZones().parallelStream().forEach(origin -> {
                if (origin == null) return;

                // origin attraction (pop_total + 2.54 * fte_total)
                Number oPop = (Number) origin.getAttribute("pop_total");
                Number oFte = (Number) origin.getAttribute("fte_total");
                double origPop = oPop != null ? oPop.doubleValue() : 0.0;
                double origFte = oFte != null ? oFte.doubleValue() : 0.0;
                double zoneAttraction = origPop + 2.54 * origFte;

                DoubleAdder acc = new DoubleAdder();

                for (Zone destination : allZones) {
                    if (destination == null) continue;

                    Number dPop = (Number) destination.getAttribute("pop_total");
                    Number dFte = (Number) destination.getAttribute("fte_total");
                    double destPop = dPop != null ? dPop.doubleValue() : 0.0;
                    double destFte = dFte != null ? dFte.doubleValue() : 0.0;
                    double attraction = destPop + 2.54 * destFte;

                    Map<String, Double> travelMetrics = matrices.getTravelAndAccessibilityMetrics(origin, destination);

                    double logsum = calculateLogsum(modeFormulas, travelMetrics, expressionEvaluator);

                    double kalif = kalifWork.get(origin.getId(), destination.getId());
                    double expsum = Math.exp(logsum + 0.5 * kalif);

                    acc.add(expsum * attraction);
                };
                double totalAccessibility = acc.sum();
                origin.getAttributes().putAttribute(accessibilityType, Math.max(totalAccessibility, zoneAttraction));
            });
        };
    }



    public String getName() {
        return name;
    }
}
