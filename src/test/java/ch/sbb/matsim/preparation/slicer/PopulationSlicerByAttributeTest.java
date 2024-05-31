package ch.sbb.matsim.preparation.slicer;

import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PopulationSlicerByAttributeTest {

    private static Population getPopulation() {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Population population = scenario.getPopulation();
        var fac = population.getFactory();
        for (int i = 0; i < 1000; i++) {
            Person p = fac.createPerson(Id.createPersonId(i));
            p.getAttributes().putAttribute(PopulationSlicerByAttribute.SLICE, i % 200);
            population.addPerson(p);
        }
        return population;
    }

    @Test
    public void testPopulationSlicingByAttribute() {
        Population population = getPopulation();
        PopulationSlicerByAttribute.filterPopulationBySlice(population, 0, 0.1);
        assertEquals(100, population.getPersons().size());
        Set<Id<Person>> expectedPersons = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            if (i % 200 < 20) expectedPersons.add(Id.createPersonId(i));
        }
        Set<Id<Person>> actualPersons = new HashSet<>();
        actualPersons.addAll(population.getPersons().keySet());
        assertEquals(expectedPersons, actualPersons);


    }

    @Test
    public void testPopulationSlicingByAttributeWithNonNullSlice() {
        Population population = getPopulation();
        PopulationSlicerByAttribute.filterPopulationBySlice(population, 3, 0.25);
        assertEquals(250, population.getPersons().size());
        Set<Id<Person>> expectedPersons = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            if (i % 200 >= 150) expectedPersons.add(Id.createPersonId(i));
        }
        Set<Id<Person>> actualPersons = new HashSet<>();
        actualPersons.addAll(population.getPersons().keySet());
        assertEquals(expectedPersons, actualPersons);


    }

    @Test
    public void testInvalidSliceSelection() {
        Population population = getPopulation();
        assertThrows(RuntimeException.class, () -> PopulationSlicerByAttribute.filterPopulationBySlice(population, 4, 0.25));

    }

    @Test
    public void testNoAttributeSet() {
        Population population = getPopulation();
        population.getPersons().get(Id.createPersonId(21)).getAttributes().removeAttribute(PopulationSlicerByAttribute.SLICE);
        PopulationSlicerByAttribute.filterPopulationBySlice(population, 3, 0.25);
        assertEquals(1000, population.getPersons().size());

    }

}