package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.constants.ZonalAttributes;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.steps.csvmodels.IntegerSelectionModelFromCSV;

import ch.sbb.matsim.plans.tours.Tour;
import ch.sbb.matsim.zones.AttributableZone;
import ch.sbb.matsim.zones.Zone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TourBuildingModelTest {

    @Mock
    private MOBiPlansConfig configMock;
    @Mock
    private MOBiPlansConfig.DataConfig dataMock;
    @Mock
    private MOBiPlansConfig.ModelsConfig modelsMock;

    private Scenario scenario;
    private MockedConstruction<IntegerSelectionModelFromCSV> intModelMock;
    private MockedConstruction<SecondaryActivityTypeAssignmentModel> secModelMock;

    @BeforeEach
    void setUp() {
        // 1. Setup MATSim Scenario
        scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        // 2. Setup MOBiPlansConfig Mock
        when(configMock.data()).thenReturn(dataMock);
        when(configMock.models()).thenReturn(modelsMock);
        when(dataMock.modelBaseDir()).thenReturn("dummy/path");
        when(modelsMock.threads()).thenReturn(1);
        when(modelsMock.write_analysis()).thenReturn(false); // Skip file writing in unit test

        // 3. Intercept 'new' calls for CSV models to avoid needing actual files
        intModelMock = Mockito.mockConstruction(IntegerSelectionModelFromCSV.class, (mock, context) -> {
            // Tell every mocked integer model to always return '1' for tours/stops
            when(mock.calculate(any(Person.class))).thenReturn(1);
        });

        secModelMock = Mockito.mockConstruction(SecondaryActivityTypeAssignmentModel.class, (mock, context) -> {
            // Tell the secondary activity model to always return "shopping"
            when(mock.assignActivityType(any(Person.class), any(Random.class))).thenReturn("shopping");
        });
    }

    @AfterEach
    void tearDown() {
        // Important: Close the mocked constructions to prevent memory leaks in test suite
        intModelMock.close();
        secModelMock.close();
    }

    @Test
    void testRunBuildsToursCorrectly() {
        // 1. Arrange: Create a dummy person with all necessary attributes
        PopulationFactory factory = scenario.getPopulation().getFactory();
        Person person = factory.createPerson(Id.createPersonId("p1"));

        // Add attributes needed to trigger all tour types
        person.getAttributes().putAttribute(SynpopConstants.LEVEL_OF_EMPLOYMENT, 1);
        person.getAttributes().putAttribute(SynpopConstants.CURRENT_EDU, 1);
        person.getAttributes().putAttribute(SynpopConstants.SCHOOL_ZONE, "school_zone_123");
        person.getAttributes().putAttribute(SynpopConstants.CAR_AVAILABLE, true);
        person.getAttributes().putAttribute(SynpopConstants.IS_WORKING_FROM_HOME, false);
        person.getAttributes().putAttribute(SynpopConstants.ACTIVE_IN_MODEL,true);

        // Make sure the person is considered "Active" by PopulationSampler.ActivePersonPredicate
        // NOTE: Adjust this attribute if your ActivePersonPredicate looks for something else!
        person.getAttributes().putAttribute("active", true);

        // Add a dummy zone to test CAR_AVAILABLE logic
        Zone dummyZone = new AttributableZone(Id.create("z1", Zone.class),null);
        dummyZone.getAttributes().putAttribute(ZonalAttributes.ACC_MUL_SC, 1.5);
        person.getAttributes().putAttribute("zone", dummyZone);

        scenario.getPopulation().addPerson(person);

        // 2. Act
        TourBuildingModel model = new TourBuildingModel(scenario, configMock);
        model.runIteration();

        // 3. Assert: Check counts stored in attributes
        assertEquals(1, person.getAttributes().getAttribute(SynpopConstants.NO_WORK_TOURS));
        assertEquals(1, person.getAttributes().getAttribute(SynpopConstants.NO_EDUCATION_TOURS));
        assertEquals(1, person.getAttributes().getAttribute(SynpopConstants.NO_BUSINESS_TOURS));
        assertEquals(1, person.getAttributes().getAttribute(SynpopConstants.NO_OTHER_TOURS));

        assertEquals(2, person.getAttributes().getAttribute(SynpopConstants.NO_PRIM_TOURS));
        assertEquals(4, person.getAttributes().getAttribute(SynpopConstants.NO_TOT_TOURS));

        // 4. Assert: Check the generated tours
        @SuppressWarnings("unchecked")
        List<Tour> tours = (List<Tour>) person.getAttributes().getAttribute(SynpopConstants.TOURS);

        assertNotNull(tours, "Tours list should be attached to the person.");
        assertEquals(4, tours.size(), "Person should have exactly 4 tours generated.");

        // Check Work Tour (Index 0)
        Tour workTour = tours.get(0);
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
        // We mocked the IntegerSelectionModel to return 1 for everything, so there should be exactly 1 stop per leg
        assertEquals(1, tour.getInboundStopActivities().size());
        assertEquals(1, tour.getOutboundStopActivities().size());
        assertEquals(1, tour.getSubtourActivities().size());

        // We mocked the Secondary Activity model to always return "shopping"
        assertEquals("shopping", tour.getInboundStopActivities().get(0).getType());
        assertEquals("shopping", tour.getOutboundStopActivities().get(0).getType());
        assertEquals("shopping", tour.getSubtourActivities().get(0).getType());
    }
}