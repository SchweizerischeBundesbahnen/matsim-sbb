package ch.sbb.matsim.plans.models.steps.starttimechoice;

import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.steps.IterativeDailyPlanGenerationOrchestrator;
import ch.sbb.matsim.plans.tours.Tour;
import ch.sbb.matsim.zones.AttributableZone;
import ch.sbb.matsim.zones.Zone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StartTimeChoiceModelTest {

    @TempDir
    Path tempDir;

    private MOBiPlansConfig mobiPlansConfig;


    private Scenario scenario;
    private Person person;

    @BeforeEach
    void setUp() throws IOException {
        // 1. Prepare MATSim Scenario
        scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        person = scenario.getPopulation().getFactory().createPerson(Id.createPersonId("agent_1"));
        person.getAttributes().putAttribute(IterativeDailyPlanGenerationOrchestrator.NEEDS_DAILY_REPLANNING, true);
        person.getAttributes().putAttribute(SynpopConstants.ACTIVE_IN_MODEL, true); // Needed for the ActivePersonPredicate

        Zone homeZone = new AttributableZone(Id.create("home_zone", Zone.class),null);
        person.getAttributes().putAttribute(SynpopConstants.ZONE, homeZone);

        scenario.getPopulation().addPerson(person);

        // 2. Setup the Configuration and Temp CSV File
        MOBiPlansConfig.DataConfig dataConfig = new MOBiPlansConfig.DataConfig(
                List.of(), // synpop
                null, // populationFile
                null, // householdsFile
                null, // zonesFile
                null, // facilitiesFile
                null, // carSkimsFile
                null, // ptSkimsFile
                null, // kalifMatricesFile
                tempDir.toString(), // modelBaseDir
                null, // outputBaseDir
                null, // sff_attributes
                null, // school_enr_apprentices
                null, // foreign_attractions
                null,
                null,// zone_informations
                null);
        MOBiPlansConfig.ModelsConfig modelsConfig = new MOBiPlansConfig.ModelsConfig(
                1, // threads
                1.0, // sampleSize
                false, // write_analysis
                "test_run", // run_id
                null,null,null
        );
        this.mobiPlansConfig = new MOBiPlansConfig(dataConfig, modelsConfig);



        // Create the expected subdirectories for the CSV parser
        Path choiceModelsDir = tempDir.resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_CHOICE_MODELS);
        Files.createDirectories(choiceModelsDir);

        Path csvFile = choiceModelsDir.resolve(MOBiPlansConfig.TIME_OF_DAY_CHOICE_CSV);
        // A minimal CSV ensuring Work is FORCED into the morning (0-43199s)
        // and Shopping is FORCED into the afternoon (43200-86399s)
        String csvContent = """
                comment;activity;attribute_expression;exp_beta;TIMESERIESNO;0_43199;43200_86399
                Work;work;;1.0;1;1.0;0.0
                Shop;shopping;;1.0;2;0.0;1.0
                """;
        Files.writeString(csvFile, csvContent);
    }

    @Test
    void testStartTimeChoiceModel_SortsAndSchedulesCorrectly() {
        // 1. Create a Work Tour (8 hours duration, 30 min travel each way)
        Tour workTour = new Tour(Tour.TourType.PRIMARY, SBBActivities.work);


        Activity workAct = PopulationUtils.getFactory().createActivityFromCoord(SBBActivities.work, null);
        workAct.setMaximumDuration(8 * 3600); // 8 hours
        Leg workOutLeg = PopulationUtils.getFactory().createLeg("car");
        workOutLeg.setTravelTime(1800); // 30 mins
        Leg workInLeg = PopulationUtils.getFactory().createLeg("car");
        workInLeg.setTravelTime(1800); // 30 mins
        Activity backHomeAct1 = PopulationUtils.getFactory().createActivityFromCoord(SBBActivities.home, null);

        workTour.getMainActivities().add(workAct);
        workTour.setTourElements(new ArrayList<>());
        workTour.getTourElements().add(workOutLeg);
        workTour.getTourElements().add(workAct);
        workTour.getTourElements().add(workInLeg);
        workTour.getTourElements().add(backHomeAct1);


        // 2. Create a Shopping Tour (1 hour duration, 15 min travel each way)
        Tour shopTour = new Tour(Tour.TourType.SECONDARY, SBBActivities.shopping);

        Activity shopAct = PopulationUtils.getFactory().createActivityFromCoord(SBBActivities.shopping, null);
        shopAct.setMaximumDuration(3600); // 1 hour
        Leg shopOutLeg = PopulationUtils.getFactory().createLeg("walk");
        shopOutLeg.setTravelTime(900); // 15 mins
        Leg shopInLeg = PopulationUtils.getFactory().createLeg("walk");
        shopInLeg.setTravelTime(900); // 15 mins
        Activity backHomeAct2 = PopulationUtils.getFactory().createActivityFromCoord(SBBActivities.home, null);
        shopTour.getMainActivities().add(shopAct);
        shopTour.setTourElements(new ArrayList<>());
        shopTour.getTourElements().add(shopOutLeg);
        shopTour.getTourElements().add(shopAct);
        shopTour.getTourElements().add(shopInLeg);
        shopTour.getTourElements().add(backHomeAct2);

        // 3. Add to person in REVERSE priority order (Shopping first, Work second) to test the sorting logic
        List<Tour> tours = new ArrayList<>(List.of(shopTour, workTour));
        person.getAttributes().putAttribute(SynpopConstants.TOURS, tours);

        // 4. Run the Model!
        StartTimeChoiceModel model = new StartTimeChoiceModel(scenario, mobiPlansConfig);
        model.run();

        // 5. Assertions


        // The person should have a single finalized MATSim Plan
        assertEquals(1, person.getPlans().size());
        Plan plan = person.getSelectedPlan();

        // Sequence should be exactly: Home, Leg, Work, Leg, Home, Leg, Shop, Leg, Home
        // (Because the Priority Sorter guarantees Work is scheduled first!)
        assertEquals(9, plan.getPlanElements().size());

        Activity firstHome = (Activity) plan.getPlanElements().get(0);
        Activity work = (Activity) plan.getPlanElements().get(2);
        Leg returnFromWork = (Leg) plan.getPlanElements().get(3);
        Activity midDayHome = (Activity) plan.getPlanElements().get(4);
        Activity shop = (Activity) plan.getPlanElements().get(6);
        Activity finalHome = (Activity) plan.getPlanElements().get(8);

        // Check Types
        assertEquals(SBBActivities.home, firstHome.getType());
        assertEquals(SBBActivities.work, work.getType());
        assertEquals(SBBActivities.home, midDayHome.getType());
        assertEquals(SBBActivities.shopping, shop.getType());
        assertEquals(SBBActivities.home, finalHome.getType());

        // Check Timing Continuity (No overlaps or gaps)
        assertEquals(firstHome.getEndTime().orElse(0), work.getStartTime().orElse(0) - 1800.0);
        assertEquals(work.getEndTime().orElse(0), returnFromWork.getDepartureTime().orElse(work.getEndTime().seconds()));
        assertEquals(midDayHome.getStartTime().orElse(0), work.getEndTime().orElse(0)+ 1800.0);

        // Check that durations were properly erased from the final MATSim activities!
        assertTrue(work.getMaximumDuration().isUndefined());
        assertTrue(shop.getMaximumDuration().isUndefined());
        assertTrue(finalHome.getEndTime().isUndefined(), "Final home activity should be open-ended.");
    }
}