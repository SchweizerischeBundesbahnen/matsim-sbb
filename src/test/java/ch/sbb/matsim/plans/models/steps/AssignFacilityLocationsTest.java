package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.synpop.SynPopUtils;
import ch.sbb.matsim.zones.AttributableZone;
import ch.sbb.matsim.zones.Zone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacility;
import org.matsim.households.Household;
import org.matsim.households.HouseholdImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AssignFacilityLocationsTest {

    @TempDir
    Path tempDir;

    private Scenario scenario;
    private Person person;
    private MOBiPlansConfig mobiPlansConfig;

    @BeforeEach
    void setUp() throws IOException {
        scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        // 1. Create Zone (with a dummy Envelope so centroid fallback works)
        Zone testZone = new AttributableZone(Id.create("z1", Zone.class), null);
        // MATSim tests usually mock the Geometry or use a standard Coord if Envelope is too hard to mock.
        // For simplicity in this test, we assume the zone centroid logic relies on a valid Geometry.

        // 2. Create Household
        Id<Household> hhId = Id.create("hh1", Household.class);
        Household household = new HouseholdImpl(hhId);

        household.getMemberIds().add(Id.createPersonId("p1"));
        household.getAttributes().putAttribute(SynpopConstants.COORD, new Coord(100, 100));
        household.getAttributes().putAttribute(SynpopConstants.ZONE_ID, "z1");
        scenario.getHouseholds().getHouseholds().put(hhId, household);

        // 3. Create Person with a Plan
        person = scenario.getPopulation().getFactory().createPerson(Id.createPersonId("p1"));
        person.getAttributes().putAttribute(SynpopConstants.HOUSEHOLD, household);
        person.getAttributes().putAttribute(SynpopConstants.ACTIVE_IN_MODEL, true);
        SynPopUtils.createFacilitiesForHouseholds(scenario);
        Plan plan = scenario.getPopulation().getFactory().createPlan();

        // Home Activity
        Activity homeAct = scenario.getPopulation().getFactory().createActivityFromCoord(SBBActivities.home, null);
        plan.addActivity(homeAct);

        // Work Activity (Primary, mapped to fixed facility)
        Activity workAct = scenario.getPopulation().getFactory().createActivityFromCoord(SBBActivities.work, null);
        Id<ActivityFacility> workFacId = Id.create("work_fac_1", ActivityFacility.class);
        ActivityFacility workFacility = scenario.getActivityFacilities().getFactory().createActivityFacility(workFacId, new Coord(200, 200));
        person.getAttributes().putAttribute(SynpopConstants.WORK_FACILITY, workFacility);

        plan.addActivity(workAct);

        // Shopping Activity (Secondary, mapped to Zone to be rolled on Roulette)
        Activity shopAct = scenario.getPopulation().getFactory().createActivityFromCoord(SBBActivities.shopping, null);
        shopAct.getAttributes().putAttribute(CombinedDestinationAndDurationChoiceModel.ASSIGNED_ZONE, testZone);
        plan.addActivity(shopAct);

        person.addPlan(plan);
        person.setSelectedPlan(plan);
        scenario.getPopulation().addPerson(person);

        // 4. Create Pre-Existing Facilities in Scenario
        scenario.getActivityFacilities().addActivityFacility(workFacility);

        ActivityFacility shopFacility = scenario.getActivityFacilities().getFactory().createActivityFacility(Id.create("shop_fac_1", ActivityFacility.class), new Coord(300, 300));
        shopFacility.getAttributes().putAttribute(SynpopConstants.ZONE_ID, "z1");
        shopFacility.getAttributes().putAttribute(SynpopConstants.NOGA_CODE, 4700); // Supermarkets
        shopFacility.getAttributes().putAttribute(SynpopConstants.RELATIVE_ATTRACTION, 50.0);
        scenario.getActivityFacilities().addActivityFacility(shopFacility);

        // 5. Create Fake CSVs
        createFakeCsvs();

        // 6. Config Setup
        MOBiPlansConfig.DataConfig dataConfig = new MOBiPlansConfig.DataConfig(
                List.of(), null, null, null, null, null, null, null,
                tempDir.toString(), null, null, null, null, null
        );
        MOBiPlansConfig.ModelsConfig modelsConfig = new MOBiPlansConfig.ModelsConfig(
                1, 1.0, false, "test_run", null,null,null
        );
        mobiPlansConfig = new MOBiPlansConfig(dataConfig, modelsConfig);
    }

    private void createFakeCsvs() throws IOException {
        Path dataDir = tempDir.resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_DATA);
        Files.createDirectories(dataDir);

        // Household Attraction CSV
        String hhCsv = """
                activity;attraction_rate
                leisure;2.5
                accompany;1.0
                """;
        Files.writeString(dataDir.resolve(MOBiPlansConfig.HOUSEHOLD_ACTIVITY_ATTRACTION_CSV), hhCsv);

        // NOGA Mapping CSV
        // NOGA_Code;B;E;S;A;L;O;EC
        String nogaCsv = """
                NOGA_Code;B;E;S;A;L;O;EC
                4700;0;0;1;0;0;0;0
                """; // 4700 is Shopping (S=1)
        Files.writeString(dataDir.resolve(MOBiPlansConfig.NOGA_TO_ACTIVITIES_MAPPING_CSV), nogaCsv);
    }

    @Test
    void testFacilityAssignment_AssignsCoordinatesCorrectly() {
        AssignFacilityLocations model = new AssignFacilityLocations(scenario, mobiPlansConfig);
        model.run();

        Plan plan = person.getSelectedPlan();
        Activity homeAct = (Activity) plan.getPlanElements().get(0);
        Activity workAct = (Activity) plan.getPlanElements().get(1);
        Activity shopAct = (Activity) plan.getPlanElements().get(2);

        // 1. Assert Home Activity (Should use Household Coords via the generated hh_ facility)
        assertNotNull(homeAct.getCoord());
        assertEquals(100.0, homeAct.getCoord().getX());
        assertEquals(Id.create("hh_hh1", ActivityFacility.class), homeAct.getFacilityId());

        // 2. Assert Work Activity (Should use explicitly assigned facility)
        assertNotNull(workAct.getCoord());
        assertEquals(200.0, workAct.getCoord().getX());
        assertEquals(Id.create("work_fac_1", ActivityFacility.class), workAct.getFacilityId());

        // 3. Assert Shopping Activity (Should use Roulette Wheel to pick shop_fac_1)
        assertNotNull(shopAct.getCoord());
        assertEquals(300.0, shopAct.getCoord().getX());
        assertEquals(Id.create("shop_fac_1", ActivityFacility.class), shopAct.getFacilityId());

        // 4. Ensure temporary assigned_zone attribute is scrubbed
        assertNull(shopAct.getAttributes().getAttribute(CombinedDestinationAndDurationChoiceModel.ASSIGNED_ZONE));
    }
}