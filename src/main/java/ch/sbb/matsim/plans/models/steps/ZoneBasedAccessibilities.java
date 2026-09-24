package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.analysis.skims.FloatMatrix;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.ZonalAttributes;
import ch.sbb.matsim.plans.matrices.Matrices;
import ch.sbb.matsim.plans.matrices.MatrixNames;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.formulas.ModeImpedanceFormulaRepository;
import ch.sbb.matsim.plans.models.steps.demandstrata.DemandStrataRegistry;
import ch.sbb.matsim.plans.models.utils.AttributeUtils;
import ch.sbb.matsim.plans.models.utils.ExpressionEvaluator;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import com.google.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

import static ch.sbb.matsim.plans.models.utils.Calculations.calculateLogsum;

@Singleton
public class ZoneBasedAccessibilities implements ModelStep{

    private static final Logger log = LogManager.getLogger(ZoneBasedAccessibilities.class);

    private final Zones zones;
    private final Matrices matrices;
    private final ModeImpedanceFormulaRepository formulaRepository;
    private final DemandStrataRegistry demandStrataRegistry;
    private final int threads;
    private final ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

    @Inject
    public ZoneBasedAccessibilities(Scenario scenario, Matrices matrices, ModeImpedanceFormulaRepository formulaRepository, DemandStrataRegistry demandStrataRegistry, MOBiPlansConfig config) {
        this.zones = (Zones) scenario.getScenarioElement(Variables.ZONES);
        this.matrices = matrices;
        this.formulaRepository = formulaRepository;
        this.demandStrataRegistry = demandStrataRegistry;
        this.threads = config.models().threads();
    }

    public void run() {
        Instant start = Instant.now();
        List<Zone> allZones = zones.getZones();
        log.info("Starting ZoneBasedAccessibilities for {} zones on {} threads...", allZones.size(), threads);
        FloatMatrix<Id<Zone>> kalifWork = matrices.getKalifMatrix(MatrixNames.KalifMatrices.KALIF_WORK);

        try (ForkJoinPool pool = new ForkJoinPool(threads)) {
            pool.submit(() -> {
                for (String accessibilityType : List.of(ZonalAttributes.ACCSIB_CAR, ZonalAttributes.ACCSIB_PT, ZonalAttributes.ACCSIB_MUL)) {
                    Instant typeStart = Instant.now();
                    log.info("  Computing accessibility '{}'...", accessibilityType);

                    Map<String, String> modeFormulas = formulaRepository.getModeFormulas("99",
                            demandStrataRegistry.getByName(accessibilityType));
                    Map<Id<Zone>, Double> calculatedAccessibilities = new ConcurrentHashMap<>();

                    allZones.parallelStream().forEach(origin -> {
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
                    log.info("  Finished accessibility '{}' in {}.", accessibilityType, Duration.between(typeStart, Instant.now()));
                }
            }).get();
        } catch (Exception e) {
            throw new RuntimeException("Parallel execution failed in ZoneBasedAccessibilities", e);
        }
        log.info("Finished ZoneBasedAccessibilities in {}.", Duration.between(start, Instant.now()));
    }

    public String getName() {
        return MOBiPlansRunner.ZONE_BASED_ACCESSIBILITIES;
    }
}
