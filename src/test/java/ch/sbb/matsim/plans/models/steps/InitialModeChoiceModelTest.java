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
import org.junit.jupiter.api.extension.ExtendWith;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitialModeChoiceModelTest {

    @Mock private Matrices matricesMock;
    @Mock private MOBiPlansConfig configMock;
    @Mock private MOBiPlansConfig.ModelsConfig modelsConfigMock;
    @Mock private ModeImpedanceFormulaRepository formulaRepoMock;
    @Mock private StrataPredicateFactory strataFactoryMock;
    @Mock private ExpressionEvaluator evaluatorMock;
    @Mock private FloatMatrix<Id<Zone>> distanceMatrixMock;

    private Scenario scenario;
    private PopulationFactory popFactory;
    private Zone homeZone, stopZone, workZone, subZone;

    @BeforeEach
    void setUp() {
        scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        popFactory = scenario.getPopulation().getFactory();

        // 1. Thread Config
        when(configMock.models()).thenReturn(modelsConfigMock);
        when(modelsConfigMock.threads()).thenReturn(1);

        // 2. Matrices Setup
        when(matricesMock.getTravelMetricsMatrix(MatrixNames.TravelMetricsMatrices.CAR_NET_DISTANCE)).thenReturn(distanceMatrixMock);
        lenient().when(matricesMock.getTravelAndAccessibilityMetrics(any(), any())).thenReturn(Collections.emptyMap());

        // 3. Create Zones
        homeZone = new AttributableZone(Id.create("home", Zone.class),null);
        stopZone = new AttributableZone(Id.create("stop", Zone.class), null);
        workZone = new AttributableZone(Id.create("work", Zone.class), null);
        subZone = new AttributableZone(Id.create("sub", Zone.class),  null);

        // 4. Mock the Distances so we can test the "Longest Leg" sorting
        // Outbound: Home -> Stop (2000m) -> Work (5000m). Max Main Leg = 5000m
        lenient().when(distanceMatrixMock.get(homeZone.getId(), stopZone.getId())).thenReturn(2000f);
        lenient().when(distanceMatrixMock.get(stopZone.getId(), workZone.getId())).thenReturn(5000f);

        // Subtour: Work -> Sub (1500m) -> Work (1000m). Max Subtour Leg = 1500m
        lenient().when(distanceMatrixMock.get(workZone.getId(), subZone.getId())).thenReturn(1500f);
        lenient().when(distanceMatrixMock.get(subZone.getId(), workZone.getId())).thenReturn(1000f);

        // Inbound: Work -> Home (4000m)
        lenient().when(distanceMatrixMock.get(workZone.getId(), homeZone.getId())).thenReturn(4000f);

        // 5. Setup Formulas and Strata
        DecisionStrata dummyStrata = DecisionStrata.PRIMLOC_EMPL_NOABO_CAR; // The actual strata doesn't matter for this test since we mock the predicate to always return true
        lenient().when(formulaRepoMock.getStratasPerActivityType()).thenReturn(
                Map.of(
                        SBBActivities.work, Set.of(dummyStrata),
                        SBBActivities.leisure, Set.of(dummyStrata)
                )
        );
        lenient().when(strataFactoryMock.getPredicate(dummyStrata)).thenReturn(p -> true); // Applies to everyone

        // Mock the formulas that will be returned
        Map<String, String> workFormulas = Map.of(SBBModes.CAR, "car_work_util", SBBModes.PT, "pt_work_util");
        Map<String, String> subFormulas = Map.of(SBBModes.CAR, "car_sub_util", SBBModes.PT, "pt_sub_util", SBBModes.WALK_MAIN_MAINMODE, "walk_sub_util");

        lenient().when(formulaRepoMock.getModeFormulas(SBBActivities.work, dummyStrata)).thenReturn(workFormulas);
        lenient().when(formulaRepoMock.getModeFormulas("leisure", dummyStrata)).thenReturn(subFormulas);

        // Setup the Evaluator to return fixed utilities based on the formula string
        // Work: CAR is highly preferred (10.0 vs 0.0)
        lenient().when(evaluatorMock.evaluate(eq("car_work_util"), any())).thenReturn(10.0);
        lenient().when(evaluatorMock.evaluate(eq("pt_work_util"), any())).thenReturn(0.0);

        // Subtour: PT is highly preferred over car/walk (20.0 vs 10.0 vs 0.0)
        lenient().when(evaluatorMock.evaluate(eq("car_sub_util"), any())).thenReturn(10.0);
        lenient().when(evaluatorMock.evaluate(eq("pt_sub_util"), any())).thenReturn(20.0);
        lenient().when(evaluatorMock.evaluate(eq("walk_sub_util"), any())).thenReturn(0.0);
    }

    private Tour buildTestTour() {
        Tour tour = new Tour(Tour.TourType.PRIMARY, SBBActivities.work);

        // Add Outbound Stop
        Activity stop1 = popFactory.createActivityFromCoord("shopping", null);
        stop1.getAttributes().putAttribute(CombinedDestinationAndDurationChoiceModel.ASSIGNED_ZONE, stopZone);
        tour.getOutboundStopActivities().add(stop1);

        // Set Main Activity Part 1
        tour.getMainActivities().get(0).getAttributes().putAttribute(CombinedDestinationAndDurationChoiceModel.ASSIGNED_ZONE, workZone);

        // Add Subtour Stop
        Activity subStop = popFactory.createActivityFromCoord("leisure", null);
        subStop.getAttributes().putAttribute(CombinedDestinationAndDurationChoiceModel.ASSIGNED_ZONE, subZone);
        tour.getSubtourActivities().add(subStop);

        // Set Main Activity Part 2
        Activity mainWork2 = popFactory.createActivityFromCoord(SBBActivities.work, null);
        mainWork2.getAttributes().putAttribute(CombinedDestinationAndDurationChoiceModel.ASSIGNED_ZONE, workZone);
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
        InitialModeChoiceModel model = new InitialModeChoiceModel(matricesMock, scenario, configMock, formulaRepoMock, strataFactoryMock, evaluatorMock);
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
        InitialModeChoiceModel model = new InitialModeChoiceModel(matricesMock, scenario, configMock, formulaRepoMock, strataFactoryMock, evaluatorMock);
        model.run();

        // 3. Assertions
        // Because Car was not available, the model only evaluated PT (0.0). PT wins by default.
        assertEquals(SBBModes.PT, tour.getMaintourMode());

        // For the subtour, even though evaluatorMock returns 10.0 for CAR and 20.0 for PT,
        // the chain-based logic should completely remove CAR from the choice set.
        assertEquals(SBBModes.PT, tour.getSubtourMode());
    }
}