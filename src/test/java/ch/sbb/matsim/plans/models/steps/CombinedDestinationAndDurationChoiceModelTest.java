package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.analysis.skims.FloatMatrix;
import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.constants.ZonalAttributes;
import ch.sbb.matsim.plans.matrices.Matrices;
import ch.sbb.matsim.plans.matrices.MatrixNames;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.formulas.ModeImpedanceFormulaRepository;
import ch.sbb.matsim.plans.models.steps.demandstrata.DemandStrata;
import ch.sbb.matsim.plans.models.steps.demandstrata.DemandStrataRegistry;
import ch.sbb.matsim.plans.tours.Tour;
import ch.sbb.matsim.zones.AttributableZone;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CombinedDestinationAndDurationChoiceModelTest {

    @TempDir
    Path tempDir;

    private Scenario scenario;
    private MOBiPlansConfig mobiPlansConfig;
    private Zones zones;
    private Matrices dummyMatrices;
    private ModeImpedanceFormulaRepository dummyFormulaRepo;
    private DemandStrataRegistry demandStrataRegistry;

    private Person person;
    private Zone homeZone;
    private Zone shopZone;

    @BeforeEach
    void setUp() throws IOException {
        // 1. Scenario & Population Setup
        scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        person = scenario.getPopulation().getFactory().createPerson(Id.createPersonId("agent_1"));
        person.getAttributes().putAttribute(IterativeDailyPlanGenerationOrchestrator.NEEDS_DAILY_REPLANNING, true);
        person.getAttributes().putAttribute(SynpopConstants.ACTIVE_IN_MODEL, true);

        // 2. Zone Setup (Home has 0 attraction, Shop has 100 attraction)
        homeZone = new AttributableZone(Id.create("home_zone", Zone.class), null);
        homeZone.getAttributes().putAttribute(ZonalAttributes.ATTR_S, 0.0); // 0 Shopping attraction

        shopZone = new AttributableZone(Id.create("shop_zone", Zone.class), null);
        shopZone.getAttributes().putAttribute(ZonalAttributes.ATTR_S, 100.0); // 100 Shopping attraction

        person.getAttributes().putAttribute(SynpopConstants.ZONE, homeZone);
        scenario.getPopulation().addPerson(person);

        zones = new ZonesImpl(Id.create("zones", Zones.class));
        ((ZonesImpl)zones).add(homeZone);
        ((ZonesImpl)zones).add(shopZone);


        // 3. Fake CSV Files for Durations and Kalif
        createFakeCsvs();

        // 4. Config Setup
        MOBiPlansConfig.DataConfig dataConfig = new MOBiPlansConfig.DataConfig(
                List.of(), null, null, null, null, null, null, null,
                tempDir.toString(), null, null, null, null, null,null,
                null);
        MOBiPlansConfig.ModelsConfig modelsConfig = new MOBiPlansConfig.ModelsConfig(
                1, 1.0, false, "test_run", null,null,null
        );
        mobiPlansConfig = new MOBiPlansConfig(dataConfig, modelsConfig);

        // 5. Pure Java Dummies for Repositories (Replaces Mockito)
        setupDummies();
    }

    private void createFakeCsvs() throws IOException {
        Path choiceModelsDir = tempDir.resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_CHOICE_MODELS);
        Path configDir = tempDir.resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_CONFIG);
        Files.createDirectories(choiceModelsDir);
        Files.createDirectories(configDir);

        // Duration CSV (Force 1 hour / 3600 seconds)
        String durationCsv = "comment;activityType;ATTREXPR;PARAM_1;DISTRIBUTION;MAX;MIN;PARAM_2\n" +
                "Test;shopping;;1.0;constant;1;1;0.0\n";
        Files.writeString(choiceModelsDir.resolve(MOBiPlansConfig.ACTIVITY_DURATIONS_WITHOUT_SUBTOUR_CSV), durationCsv);
        Files.writeString(choiceModelsDir.resolve(MOBiPlansConfig.ACTIVITY_DURATIONS_WITH_SUBTOUR_CSV), durationCsv);

        // Kalif CSV (Simple 1-to-1 mapping)
        String kalifCsv = "activity;kalif_expression\n" +
                "shopping;1.0 * kalif_work\n";
        Files.writeString(configDir.resolve(MOBiPlansConfig.SECONDARY_ACTIVITY_CHOICE_KALIF_EXPRESSIONS_CSV), kalifCsv);
    }

    private void setupDummies() {
        demandStrataRegistry = new DemandStrataRegistry();
        demandStrataRegistry.register(new DemandStrata("default", DemandStrata.Type.SECONDARY_DESTINATION,null));

        // Dummy Formula Repository (Returns a simple travel time penalty)
        dummyFormulaRepo = new ModeImpedanceFormulaRepository() {
            @Override
            public Map<String, String> getModeFormulas(String activityType, DemandStrata strata) {
                // Changed from -0.1 to -0.01 so it evaluates to -6.0 instead of -60.0!
                return Map.of("car", "-0.01 * matrix_car_travel_times");
            }

            @Override
            public Map<String, Set<DemandStrata>> getStratasPerActivityType() {
                return Map.of();
            }
        };

        // Dummy Matrices (Provides a 10-minute / 600s travel time between zones)
        dummyMatrices = new Matrices() {
            @Override
            public Map<String, Double> getTravelAndAccessibilityMetrics(Zone origin, Zone destination) {
                return getTravelMetrics(origin.getId(), destination.getId());
            }

            @Override
            public Map<String, Double> getKalifMetrics(Id<Zone> fromZoneId, Id<Zone> toZoneId) {
                return Map.of();
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
                // Return a flat FloatMatrix populated with 0.0 for Kalif impact
                return FloatMatrix.createFloatMatrix(Set.of(homeZone.getId(), shopZone.getId()), 0.0f);
            }

            @Override
            public FloatMatrix<Id<Zone>> getTravelMetricsMatrix(MatrixNames.TravelMetricsMatrices matrixName) {
                return null;
            }

            @Override
            public Map<String, Double> getTravelMetrics(Id<Zone> oId, Id<Zone> dId) {
                return Map.of(
                        MatrixNames.TravelMetricsMatrices.CAR_TRAVEL_TIMES.name().toLowerCase(), 600.0,
                        MatrixNames.TravelMetricsMatrices.PT_TRAVEL_TIMES_TOTAL.name().toLowerCase(), 1200.0,
                        MatrixNames.TravelMetricsMatrices.CAR_NET_DISTANCE.name().toLowerCase(), 5000.0
                );
            }

        };
    }

    @Test
    void testDestinationAndDurationChoice_AssignsCorrectZone() {
        // 1. Arrange: Create a Secondary Shopping Tour
        Tour shopTour = new Tour(Tour.TourType.SECONDARY, SBBActivities.shopping);

        // FIX: Grab the activity that the Tour constructor already created!
        Activity shopAct = shopTour.getMainActivities().get(0);


        Leg shopOutLeg = PopulationUtils.getFactory().createLeg("car");
        Leg shopInLeg = PopulationUtils.getFactory().createLeg("car");

        shopTour.getMainActivities().add(shopAct);
        shopTour.setTourElements(new ArrayList<>());
        shopTour.getTourElements().add(shopOutLeg);
        shopTour.getTourElements().add(shopAct);
        shopTour.getTourElements().add(shopInLeg);

        person.getAttributes().putAttribute(SynpopConstants.TOURS, List.of(shopTour));

        // 2. Act
        CombinedDestinationAndDurationChoiceModel model = new CombinedDestinationAndDurationChoiceModel(
                scenario, mobiPlansConfig, zones, dummyMatrices, dummyFormulaRepo, demandStrataRegistry);

        // Build the caches (Kalif, Logsum, Blended TT)
        model.prepare();

        // Run the 30-combination optimization loop!
        model.runIteration(16.0, 5.0, 18.0);

        // Cleanup memory
        model.cleanup();

        // 3. Assert: Person successfully found a plan
        assertFalse((Boolean) person.getAttributes().getAttribute(IterativeDailyPlanGenerationOrchestrator.NEEDS_DAILY_REPLANNING),
                "Person should have found a valid plan within the budgets.");

        // 4. Assert: Destination & Duration correctly assigned
        Activity plannedShopAct = shopTour.getMainActivities().get(0);

        // Duration should be exactly 3600 seconds based on our fake CSV
        assertEquals(3600.0, plannedShopAct.getMaximumDuration().orElse(-1.0), 0.1);

        // Destination MUST be shopZone because homeZone had 0 attraction!
        Zone assignedZone = (Zone) plannedShopAct.getAttributes().getAttribute(CombinedDestinationAndDurationChoiceModel.ASSIGNED_ZONE);
        assertNotNull(assignedZone, "Assigned zone should not be null.");
        assertEquals(shopZone.getId(), assignedZone.getId(), "Model should be mathematically forced to pick the Shop Zone.");
    }
}