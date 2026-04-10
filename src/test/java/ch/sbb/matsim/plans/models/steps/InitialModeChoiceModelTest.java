package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.analysis.skims.FloatMatrix;
import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.matrices.Matrices;
import ch.sbb.matsim.plans.matrices.MatrixNames;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.formulas.ModeImpedanceFormulaRepository;
import ch.sbb.matsim.plans.models.utils.ExpressionEvaluator;
import ch.sbb.matsim.plans.models.utils.StrataPredicateFactory;
import ch.sbb.matsim.plans.synpop.DecisionStrata;
import ch.sbb.matsim.plans.tours.Tour;
import ch.sbb.matsim.zones.AttributableZone;
import ch.sbb.matsim.zones.Zone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InitialModeChoiceModelTest {

    private Scenario scenario;
    private PopulationFactory popFactory;
    private Zone homeZone, stopZone, workZone, subZone;

    private MOBiPlansConfig mobiPlansConfig;
    private Matrices dummyMatrices;
    private ModeImpedanceFormulaRepository dummyFormulaRepo;
    private StrataPredicateFactory dummyStrataFactory;
    private ExpressionEvaluator dummyEvaluator;

    @BeforeEach
    void setUp() {
        scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        popFactory = scenario.getPopulation().getFactory();

        // 1. Thread Config
        MOBiPlansConfig.DataConfig dataConfig = new MOBiPlansConfig.DataConfig(
                List.of(), null, null, null, null, null, null, null,
                "dummy/path", null, null, null, null, null
        );
        MOBiPlansConfig.ModelsConfig modelsConfig = new MOBiPlansConfig.ModelsConfig(
                1, 1.0, false, "test_run", null
        );
        mobiPlansConfig = new MOBiPlansConfig(dataConfig, modelsConfig);

        // 2. Create Zones
        homeZone = new AttributableZone(Id.create("home", Zone.class), null);
        stopZone = new AttributableZone(Id.create("stop", Zone.class), null);
        workZone = new AttributableZone(Id.create("work", Zone.class), null);
        subZone = new AttributableZone(Id.create("sub", Zone.class), null);

        // 3. Setup Distances so we can test the "Longest Leg" sorting
        List<Id<Zone>> zoneIds = List.of(homeZone.getId(), stopZone.getId(), workZone.getId(), subZone.getId());
        float[][] distances = new float[4][4];

        // Outbound: Home(0) -> Stop(1) -> Work(2). Max Main Leg = 5000m
        distances[0][1] = 2000f;
        distances[1][2] = 5000f;
        // Subtour: Work(2) -> Sub(3) -> Work(2). Max Subtour Leg = 1500m
        distances[2][3] = 1500f;
        distances[3][2] = 1000f;
        // Inbound: Work(2) -> Home(0)
        distances[2][0] = 4000f;

        FloatMatrix<Id<Zone>> distanceMatrix = FloatMatrix.createFloatMatrix(zoneIds, distances);

        // 4. Pure Java Matrices Stub
        dummyMatrices = new Matrices() {
            @Override
            public FloatMatrix<Id<Zone>> getTravelMetricsMatrix(MatrixNames.TravelMetricsMatrices name) {
                if (MatrixNames.TravelMetricsMatrices.CAR_NET_DISTANCE.equals(name)) {
                    return distanceMatrix;
                }
                return null;
            }

            @Override
            public FloatMatrix<Id<Zone>> getMatrix(String matrixName) {
                return null;
            }

            @Override
            public void addMatrix(String matrixName, FloatMatrix<Id<Zone>> matrix) {

            }

            @Override
            public void addMatrices(Map<String, FloatMatrix<Id<Zone>>> matrices) {

            }

            @Override
            public void removeMatrix(String matrixName) {

            }

            @Override
            public FloatMatrix<Id<Zone>> getKalifMatrix(MatrixNames.KalifMatrices matrixName) {
                return null;
            }

            @Override
            public Map<String, Double> getTravelMetrics(Id<Zone> fromZoneId, Id<Zone> toZoneId) {
                return Map.of();
            }

            @Override
            public Map<String, Double> getTravelAndAccessibilityMetrics(Zone origin, Zone destination) {
                return Collections.emptyMap(); // Not needed for the direct formula evaluator stub
            }

            @Override
            public Map<String, Double> getKalifMetrics(Id<Zone> fromZoneId, Id<Zone> toZoneId) {
                return Map.of();
            }
        };

        // 5. Setup Formulas and Strata Stubs
        DecisionStrata dummyStrata = DecisionStrata.PRIMLOC_EMPL_NOABO_CAR;

        dummyStrataFactory = new StrataPredicateFactory() {
            @Override
            public Predicate<Person> getPredicate(DecisionStrata strata) {
                return p -> true; // Applies to everyone in this test
            }
        };

        dummyFormulaRepo = new ModeImpedanceFormulaRepository() {
            @Override
            public Map<String, Set<DecisionStrata>> getStratasPerActivityType() {
                return Map.of(
                        SBBActivities.work, Set.of(dummyStrata),
                        SBBActivities.leisure, Set.of(dummyStrata)
                );
            }

            @Override
            public Map<String, String> getModeFormulas(String activityType, DecisionStrata strata) {
                if (SBBActivities.work.equals(activityType)) {
                    return Map.of(SBBModes.CAR, "car_work_util", SBBModes.PT, "pt_work_util");
                } else if (SBBActivities.leisure.equals(activityType)) {
                    return Map.of(SBBModes.CAR, "car_sub_util", SBBModes.PT, "pt_sub_util", SBBModes.WALK_MAIN_MAINMODE, "walk_sub_util");
                }
                return Collections.emptyMap();
            }
        };

        // 6. Setup Expression Evaluator Stub
        dummyEvaluator = new ExpressionEvaluator() {
            @Override
            public double evaluate(String formulaString, Map<String, Double> variableData) {
                switch (formulaString) {
                    case "car_work_util": return 10.0;
                    case "pt_work_util": return 0.0;
                    case "car_sub_util": return 10.0;
                    case "pt_sub_util": return 20.0;
                    case "walk_sub_util": return 0.0;
                    default: return Double.NEGATIVE_INFINITY;
                }
            }
        };
    }

    private Tour buildTestTour() {
        Tour tour = new Tour(Tour.TourType.PRIMARY, SBBActivities.work);

        // Add Outbound Stop
        Activity stop1 = popFactory.createActivityFromCoord("shopping", null);
        stop1.getAttributes().putAttribute("assigned_zone", stopZone);
        tour.getOutboundStopActivities().add(stop1);

        // Set Main Activity Part 1
        tour.getMainActivities().get(0).getAttributes().putAttribute("assigned_zone", workZone);

        // Add Subtour Stop
        Activity subStop = popFactory.createActivityFromCoord("leisure", null);
        subStop.getAttributes().putAttribute("assigned_zone", subZone);
        tour.getSubtourActivities().add(subStop);

        // Set Main Activity Part 2
        Activity mainWork2 = popFactory.createActivityFromCoord(SBBActivities.work, null);
        mainWork2.getAttributes().putAttribute("assigned_zone", workZone);
        tour.getMainActivities().add(mainWork2);

        return tour;
    }

    @Test
    void testModeChoice_WithCarAvailable() {
        // 1. Setup Person with Car
        Person p1 = popFactory.createPerson(Id.createPersonId("p1_with_car"));
        p1.getAttributes().putAttribute(SynpopConstants.ZONE, homeZone);
        p1.getAttributes().putAttribute(SynpopConstants.CAR_AVAILABLE, true); // CAR IS AVAILABLE
        p1.getAttributes().putAttribute(SynpopConstants.ACTIVE_IN_MODEL, true);

        Tour tour = buildTestTour();
        p1.getAttributes().putAttribute(SynpopConstants.TOURS, List.of(tour));
        scenario.getPopulation().addPerson(p1);

        // 2. Run Model
        InitialModeChoiceModel model = new InitialModeChoiceModel(dummyMatrices, scenario, mobiPlansConfig, dummyFormulaRepo, dummyStrataFactory, dummyEvaluator);
        model.run();

        // 3. Assertions
        // Because Car was available, it evaluated CAR (10) vs PT (0) for work. CAR wins.
        assertEquals(SBBModes.CAR, tour.getMaintourMode());

        // For the subtour, PT (20) beats CAR (10). Because main mode was CAR, CAR was in the choice set, but PT still won on utility.
        assertEquals(SBBModes.PT, tour.getSubtourMode());
    }

    @Test
    void testModeChoice_NoCarAvailable_ExcludesChainBasedModesFromSubtours() {
        // 1. Setup Person without Car
        Person p2 = popFactory.createPerson(Id.createPersonId("p2_no_car"));
        p2.getAttributes().putAttribute(SynpopConstants.ZONE, homeZone);
        p2.getAttributes().putAttribute(SynpopConstants.CAR_AVAILABLE, false); // NO CAR
        p2.getAttributes().putAttribute(SynpopConstants.ACTIVE_IN_MODEL, true);

        Tour tour = buildTestTour();
        p2.getAttributes().putAttribute(SynpopConstants.TOURS, List.of(tour));
        scenario.getPopulation().addPerson(p2);

        // 2. Run Model
        InitialModeChoiceModel model = new InitialModeChoiceModel(dummyMatrices, scenario, mobiPlansConfig, dummyFormulaRepo, dummyStrataFactory, dummyEvaluator);
        model.run();

        // 3. Assertions
        // Because Car was not available, the model only evaluated PT (0.0). PT wins by default.
        assertEquals(SBBModes.PT, tour.getMaintourMode());

        // For the subtour, even though evaluator returns 10.0 for CAR and 20.0 for PT,
        // the chain-based logic should completely remove CAR from the choice set.
        assertEquals(SBBModes.PT, tour.getSubtourMode());
    }
}