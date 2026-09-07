package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.constants.ZonalAttributes;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.tours.Tour;
import ch.sbb.matsim.zones.AttributableZone;
import ch.sbb.matsim.zones.Zone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TourBuildingModelTest {

    @TempDir
    Path tempDir;

    private MOBiPlansConfig mobiPlansConfig;
    private Scenario scenario;
    private Person person;

    @BeforeEach
    void setUp() throws IOException {
        // 1. Setup MATSim Scenario
        scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        PopulationFactory factory = scenario.getPopulation().getFactory();
        person = factory.createPerson(Id.createPersonId("p1"));

        // 2. Setup Person Attributes to trigger all branches
        person.getAttributes().putAttribute(SynpopConstants.LEVEL_OF_EMPLOYMENT, 1);
        person.getAttributes().putAttribute(SynpopConstants.CURRENT_EDU, 1);
        person.getAttributes().putAttribute(SynpopConstants.SCHOOL_ZONE, "school_zone_123");
        person.getAttributes().putAttribute(SynpopConstants.CAR_AVAILABLE, true);
        person.getAttributes().putAttribute(SynpopConstants.IS_WORKING_FROM_HOME, false);
        person.getAttributes().putAttribute(SynpopConstants.ACTIVE_IN_MODEL, true);
        person.getAttributes().putAttribute(IterativeDailyPlanGenerationOrchestrator.NEEDS_DAILY_REPLANNING, true);

        // Add a dummy zone
        Zone dummyZone = new AttributableZone(Id.create("z1", Zone.class), null);
        dummyZone.getAttributes().putAttribute(ZonalAttributes.ACC_MUL_SC, 1.5);
        person.getAttributes().putAttribute(SynpopConstants.ZONE, dummyZone);

        scenario.getPopulation().addPerson(person);

        // 3. Create the fake CSV files in the temp directory
        createFakeCsvModels();

        // 4. Setup MOBiPlansConfig
        MOBiPlansConfig.DataConfig dataConfig = new MOBiPlansConfig.DataConfig(
                List.of(), null, null, null, null, null, null, null,
                tempDir.toString(), // modelBaseDir points to tempDir
                null, null, null, null, null,null,
                null);
        MOBiPlansConfig.ModelsConfig modelsConfig = new MOBiPlansConfig.ModelsConfig(
                1, 1.0, false, "test_run", null,null,null
        );
        this.mobiPlansConfig = new MOBiPlansConfig(dataConfig, modelsConfig);
    }

    /**
     * Generates minimal, deterministic CSVs so the Model loads them natively.
     */
    private void createFakeCsvModels() throws IOException {
        Path choiceModelsDir = tempDir.resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_CHOICE_MODELS);
        Files.createDirectories(choiceModelsDir);

        // 1. Fake Integer Selection Model (Forces probability=1.0 for the value '1')
        // Since we want exactly 1 tour and 1 stop for everything, we just map all 12 model files to this same content!
        String integerCsvContent = """
                type;attribute;values;lambda;beta_0;beta_1
                constant;;1;1.0;0.0;100.0
                """; // Beta 100 for value 1 forces ~100% probability

        List<String> integerFiles = List.of(
                MOBiPlansConfig.TOUR_FREQUENCY_WORK_MODEL_CSV,
                MOBiPlansConfig.TOUR_FREQUENCY_EDUCATION_MODEL_CSV,
                MOBiPlansConfig.TOUR_FREQUENCY_OTHER_MODEL_CSV,
                MOBiPlansConfig.TOUR_FREQUENCY_BUSINESS_MODEL_CSV,
                MOBiPlansConfig.NUMBER_OF_STOPS_INBOUND_WORK_CSV,
                MOBiPlansConfig.NUMBER_OF_STOPS_OUTBOUND_WORK_CSV,
                MOBiPlansConfig.NUMBER_OF_STOPS_INBOUND_EDUCATION_CSV,
                MOBiPlansConfig.NUMBER_OF_STOPS_OUTBOUND_EDUCATION_CSV,
                MOBiPlansConfig.NUMBER_OF_STOPS_BUSINESS_CSV,
                MOBiPlansConfig.NUMBER_OF_STOPS_OTHER_CSV,
                MOBiPlansConfig.SUBTOUR_FREQUENCY_WORK_CSV,
                MOBiPlansConfig.SUBTOUR_FREQUENCY_EDUCATION_CSV
        );

        for (String fileName : integerFiles) {
            Files.writeString(choiceModelsDir.resolve(fileName), integerCsvContent);
        }

        // 2. Fake Secondary Activity Assignment Model (Forces 100% "shopping")
        // Header exactly as expected by the SecondaryActivityTypeAssignmentModel class
        String secondaryCsvContent = """
                comment;filter_expression;accompany;business;education;education_continued;leisure;other;shopping
                ForceShopping;;0.0;0.0;0.0;0.0;0.0;0.0;1.0
                """;
        Files.writeString(choiceModelsDir.resolve(MOBiPlansConfig.SECONDARY_ACTIVITY_TYPE_ASSIGNMENT_CSV), secondaryCsvContent);
    }

    @Test
    void testRunBuildsToursCorrectly() {
        // 1. Act
        TourBuildingModel model = new TourBuildingModel(scenario, mobiPlansConfig);
        model.runIteration(0);

        // 2. Assert: Check counts stored in attributes
        assertEquals(1, person.getAttributes().getAttribute(SynpopConstants.NO_WORK_TOURS));
        assertEquals(1, person.getAttributes().getAttribute(SynpopConstants.NO_EDUCATION_TOURS));
        assertEquals(1, person.getAttributes().getAttribute(SynpopConstants.NO_BUSINESS_TOURS));
        assertEquals(1, person.getAttributes().getAttribute(SynpopConstants.NO_OTHER_TOURS));

        assertEquals(2, person.getAttributes().getAttribute(SynpopConstants.NO_PRIM_TOURS));
        assertEquals(4, person.getAttributes().getAttribute(SynpopConstants.NO_TOT_TOURS));

        // 3. Assert: Check the generated tours
        @SuppressWarnings("unchecked")
        List<Tour> tours = (List<Tour>) person.getAttributes().getAttribute(SynpopConstants.TOURS);

        assertNotNull(tours, "Tours list should be attached to the person.");
        assertEquals(4, tours.size(), "Person should have exactly 4 tours generated.");

        // Check Work Tour (Index 0)
        Tour workTour = tours.getFirst();
        assertEquals(Tour.TourType.PRIMARY, workTour.getTourType());
        assertEquals(SBBActivities.work, workTour.getMainActivityType());
        assertStopsGenerated(workTour);

        // Check Education Tour (Index 1)
        Tour eduTour = tours.get(1);
        assertEquals(Tour.TourType.PRIMARY, eduTour.getTourType());
        assertEquals(SBBActivities.education, eduTour.getMainActivityType());
        assertStopsGenerated(eduTour);

        // Check Business Tour (Index 2)
        Tour businessTour = tours.get(2);
        assertEquals(Tour.TourType.SECONDARY, businessTour.getTourType());
        assertEquals(SBBActivities.business, businessTour.getMainActivityType());
        assertEquals(1, businessTour.getInboundStopActivities().size());
        assertEquals(0, businessTour.getSubtourActivities().size(), "Business tours should have 0 subtours.");

        // Check Other Tour (Index 3)
        Tour otherTour = tours.get(3);
        assertEquals(Tour.TourType.SECONDARY, otherTour.getTourType());
        assertEquals("shopping", otherTour.getMainActivityType(), "Main activity should be assigned by secondary model.");
        assertEquals(1, otherTour.getOutboundStopActivities().size());
    }

    private void assertStopsGenerated(Tour tour) {
        assertEquals(1, tour.getInboundStopActivities().size());
        assertEquals(1, tour.getOutboundStopActivities().size());
        assertEquals(1, tour.getSubtourActivities().size());

        assertEquals("shopping", tour.getInboundStopActivities().getFirst().getType());
        assertEquals("shopping", tour.getOutboundStopActivities().getFirst().getType());
        assertEquals("shopping", tour.getSubtourActivities().getFirst().getType());
    }
}