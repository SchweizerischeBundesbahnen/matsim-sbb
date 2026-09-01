package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.constants.ZonalAttributes;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.zones.AttributableZone;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacility;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CalcZonalAttributesTest {

    @TempDir
    Path tempDir;

    private Scenario scenario;
    private MOBiPlansConfig config;
    private Zone testZone;

    @BeforeEach
    void setUp() throws IOException {
        scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        // 1. Setup Zones container (since it's a custom scenario element)
        ZonesImpl zones = new ZonesImpl(Id.create("test_zones", Zones.class));
        scenario.addScenarioElement(Variables.ZONES, zones);

        // Create a Test Zone
        testZone = new AttributableZone(Id.create("1234", Zone.class), null); // Simulating a Swiss zone
        testZone.getAttributes().putAttribute(ZonalAttributes.AREA_LAND, 100.0); // 100 sqm
        zones.add(testZone);

        // 2. Setup Persons
        // Person 1: 10 years old, Primary Pupil, Unemployed
        Person child = scenario.getPopulation().getFactory().createPerson(Id.createPersonId("child"));
        child.getAttributes().putAttribute(SynpopConstants.ZONE_ID, "1234");
        child.getAttributes().putAttribute(SynpopConstants.AGE, 10);
        child.getAttributes().putAttribute(SynpopConstants.CURRENT_EDU, 2); // Primary
        child.getAttributes().putAttribute(SynpopConstants.LEVEL_OF_EMPLOYMENT, 0.0);
        scenario.getPopulation().addPerson(child);

        // Person 2: 30 years old, Not in education, Employed Full-Time
        Person adult = scenario.getPopulation().getFactory().createPerson(Id.createPersonId("adult"));
        adult.getAttributes().putAttribute(SynpopConstants.ZONE_ID, "1234");
        adult.getAttributes().putAttribute(SynpopConstants.AGE, 30);
        adult.getAttributes().putAttribute(SynpopConstants.CURRENT_EDU, 0); // None
        adult.getAttributes().putAttribute(SynpopConstants.LEVEL_OF_EMPLOYMENT, 100.0);
        scenario.getPopulation().addPerson(adult);

        // 3. Setup Businesses (Facilities)
        ActivityFacility business = scenario.getActivityFacilities().getFactory().createActivityFacility(Id.create("biz1", ActivityFacility.class), new Coord(0,0));
        business.getAttributes().putAttribute(SynpopConstants.ZONE_ID, "1234");
        business.getAttributes().putAttribute(SynpopConstants.JOBS_ENDO, 10.0);
        business.getAttributes().putAttribute(SynpopConstants.FTE_ENDO, 8.0); // 8 Full-Time Equivalents
        scenario.getActivityFacilities().addActivityFacility(business);

        // 4. Create Config & Fake CSVs
        createFakeDataFiles();

        MOBiPlansConfig.DataConfig dataConfig = new MOBiPlansConfig.DataConfig(
                List.of(), null, null, null, null, null, null, null,
                tempDir.toString(), null, null, null, null, null,null,
                null);
        MOBiPlansConfig.ModelsConfig modelsConfig = new MOBiPlansConfig.ModelsConfig(
                1, 1.0, false, "test_run", null,null,null
        );
        config = new MOBiPlansConfig(dataConfig, modelsConfig);
    }

    private void createFakeDataFiles() throws IOException {
        Path dataDir = tempDir.resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_DATA);
        Files.createDirectories(dataDir);

        // A. Attraction Rates CSV
        // We will define rules: Shopping Attraction = 2.0 * pop + 5.0 * fte
        String attrRates = "attribute;Attr_S;Attr_L\n" +
                ZonalAttributes.POP_TOTAL + ";2.0;1.0\n" +
                ZonalAttributes.FTE_TOTAL + ";5.0;3.0\n";
        Files.writeString(dataDir.resolve(MOBiPlansConfig.ATTRACTION_RATES), attrRates);

        // B. Special Cases / 2017 Baselines CSV
        // Provide baseline densities and access times for our test zone
        String specialCases = """
                zone_id;density_2017;at_car_2017;at_ride_2017;pc_car_2017;pc_ride_2017
                1234;0.05;300;150;2.0;1.0
                """; // Baseline density: 0.05
        Files.writeString(dataDir.resolve(MOBiPlansConfig.SPECIAL_CASES_ATIMES_PCOSTS), specialCases);
    }

    @Test
    void testCalcZonalAttributes_CalculatesEverythingCorrectly() {
        CalcZonalAttributes model = new CalcZonalAttributes(config, scenario);
        model.run();

        // --- 1. Assert Aggregates ---
        // Pop total = 2 (child + adult)
        assertEquals(2L, testZone.getAttributes().getAttribute(ZonalAttributes.POP_TOTAL));
        // Pop empl = 1 (adult)
        assertEquals(1L, testZone.getAttributes().getAttribute(ZonalAttributes.POP_EMPL));

        // Age groups
        assertEquals(1L, testZone.getAttributes().getAttribute(ZonalAttributes.POP_0017));
        assertEquals(1L, testZone.getAttributes().getAttribute(ZonalAttributes.POP_2544));
        assertEquals(0L, testZone.getAttributes().getAttribute(ZonalAttributes.POP_6574));

        // Education
        assertEquals(1L, testZone.getAttributes().getAttribute(ZonalAttributes.POP_PUP_1)); // Child
        assertEquals(0L, testZone.getAttributes().getAttribute(ZonalAttributes.POP_STUD_3));

        // Jobs & FTE
        assertEquals(10.0, (Double) testZone.getAttributes().getAttribute(ZonalAttributes.JOBS_TOTAL), 0.01);
        assertEquals(8.0, (Double) testZone.getAttributes().getAttribute(ZonalAttributes.FTE_TOTAL), 0.01);

        // Density = (pop + fte) / area = (2 + 8) / 100 = 0.1
        assertEquals(0.1, (Double) testZone.getAttributes().getAttribute(ZonalAttributes.DENSITY), 0.001);

        // --- 2. Assert Attractions ---
        // Shopping = 2.0 * pop(2) + 5.0 * fte(8) = 4 + 40 = 44.0
        assertNotNull(testZone.getAttributes().getAttribute("Attr_S"));
        assertEquals(44.0, (Double) testZone.getAttributes().getAttribute("Attr_S"), 0.01);

        // Leisure = 1.0 * pop(2) + 3.0 * fte(8) = 2 + 24 = 26.0
        assertNotNull(testZone.getAttributes().getAttribute("Attr_L"));
        assertEquals(26.0, (Double) testZone.getAttributes().getAttribute("Attr_L"), 0.01);

        // Car Access Time: Math.max(60 * 0.1 / 5600, 45.0) -> 45.0.
        // Max vs 2017 baseline (300) -> 300.0.
        // (Since 300 > 45, it uses the 2017 baseline as the floor)
        assertEquals(300.0, ((Number) testZone.getAttributes().getAttribute(ZonalAttributes.AT_CAR)).doubleValue(), 0.1);

        // Ride Access Time: atRide2017 (150) + atCar (300) - atCar2017 (300) = 150.0
        assertEquals(150.0, ((Number) testZone.getAttributes().getAttribute(ZonalAttributes.AT_RIDE)).doubleValue(), 0.1);

        // Parking Cost (Pre-change density = 0.1 / 1.414 = 0.07. Since 0.07 < 629, theoretical = 0.0)
        // Uses 2017 baseline floor -> 2.0
        assertEquals(2.0, ((Number) testZone.getAttributes().getAttribute(ZonalAttributes.PC_CAR)).doubleValue(), 0.01);

        // Ride Parking Cost: 2 * (pcCar / 2.0) - pcRide2017 = 2.0 - 1.0 = 1.0
        assertEquals(1.0, ((Number) testZone.getAttributes().getAttribute(ZonalAttributes.PC_RIDE)).doubleValue(), 0.01);
    }
}