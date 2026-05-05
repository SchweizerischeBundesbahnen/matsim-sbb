package ch.sbb.matsim.projects.synpop;

import ch.sbb.matsim.config.variables.Variables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SingleTripAgentToCSVConverterTest {

    @TempDir
    Path tempDir;

    private Scenario scenario;

    @BeforeEach
    void setUp() {
        scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
    }

    @Test
    void testWriteAndReadSingleTripCSV() throws IOException {
        // 1. Setup a dummy person with exactly one trip
        PopulationFactory factory = scenario.getPopulation().getFactory();
        Person p1 = factory.createPerson(Id.createPersonId("freight_1"));
        PopulationUtils.putSubpopulation(p1, "freight_road");

        Plan plan = factory.createPlan();

        Activity startAct = factory.createActivityFromCoord("depot", new Coord(100, 200));
        startAct.setEndTime(3600); // 1 hour
        startAct.getAttributes().putAttribute(Variables.MOBiTripAttributes.PURPOSE, "delivery");
        plan.addActivity(startAct);

        Leg leg = factory.createLeg("truck");
        plan.addLeg(leg);

        Activity endAct = factory.createActivityFromCoord("store", new Coord(300, 400));
        plan.addActivity(endAct);

        p1.addPlan(plan);
        p1.setSelectedPlan(plan);
        scenario.getPopulation().addPerson(p1);

        // 2. Write to CSV
        Path csvFile = tempDir.resolve("test_freight.csv");
        SingleTripAgentToCSVConverter.writeSingleTripsAsCSV(csvFile.toString(), scenario.getPopulation());

        // 3. Read it back into a NEW scenario
        Scenario newScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        SingleTripAgentToCSVConverter.readCSVAndGetPlans(csvFile.toString(), newScenario.getPopulation());

        // 4. Assertions
        assertEquals(1, newScenario.getPopulation().getPersons().size());

        Person loadedPerson = newScenario.getPopulation().getPersons().get(Id.createPersonId("freight_1"));
        assertNotNull(loadedPerson);
        assertEquals("freight_road", PopulationUtils.getSubpopulation(loadedPerson));

        Plan loadedPlan = loadedPerson.getSelectedPlan();
        assertEquals(3, loadedPlan.getPlanElements().size());

        Activity loadedStart = (Activity) loadedPlan.getPlanElements().getFirst();
        assertEquals("depot", loadedStart.getType());
        assertEquals(100.0, loadedStart.getCoord().getX());
        assertEquals(3600.0, loadedStart.getEndTime().orElseThrow( () -> new RuntimeException("End time should be present")));

        Leg loadedLeg = (Leg) loadedPlan.getPlanElements().get(1);
        assertEquals("truck", loadedLeg.getMode());

        Activity loadedEnd = (Activity) loadedPlan.getPlanElements().get(2);
        assertEquals("store", loadedEnd.getType());
        assertEquals(400.0, loadedEnd.getCoord().getY());
    }
}