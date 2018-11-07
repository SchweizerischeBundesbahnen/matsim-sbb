package ch.sbb.matsim.analysis.PopulationSampler;

import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;

import ch.sbb.matsim.preparation.PopulationSampler.SBBPopulationSampler;

public class SBBPopulationSamplerIT {

    @Test
    public void test_fraction_1() {
        makeTest_withAttributes(10, 1.0, 10);
        makeTest_noAttributes(10, 1.0, 10);
    }

    @Test
    public void test_fraction_1_100() {
        makeTest_withAttributes(100, 1.0, 100);
        makeTest_noAttributes(100, 1.0, 100);
    }

    @Test
    public void test10_50() {
        makeTest_withAttributes(10, 0.5, 4);
        makeTest_noAttributes(10, 0.5, 4);
    }

    @Test
    public void test100_50() {
        makeTest_withAttributes(100, 0.5, 49);
        makeTest_noAttributes(100, 0.5, 49);
    }

    @Test
    public void test100_30() {
        makeTest_withAttributes(100, 0.3, 33);
        makeTest_noAttributes(100, 0.3, 33);
    }

    @Test
    public void test1000_20() {
        makeTest_withAttributes(10000, 0.2, 1959);
        makeTest_noAttributes(10000, 0.2, 1959);
    }

    private void makeTest_withAttributes(int size, double fraction, int expectedSize) {

        TestFixture fixture = new TestFixture(size, true);
        Population population = fixture.scenario.getPopulation();

        SBBPopulationSampler sbbPopulationSampler = new SBBPopulationSampler();
        Assert.assertEquals(size, population.getPersons().size());
        for(Person person: population.getPersons().values()) {
            Assert.assertNotNull(population.getPersonAttributes().getAttribute(person.getId().toString(), "attribute"));
            Assert.assertNull(population.getPersonAttributes().getAttribute(person.getId().toString(), "no_attribute"));
        }
        sbbPopulationSampler.sample(population, fraction);
        Assert.assertEquals(expectedSize, population.getPersons().size());
        for(Person person: population.getPersons().values()) {
            Assert.assertNotNull(population.getPersonAttributes().getAttribute(person.getId().toString(), "attribute"));
        }
    }

    private void makeTest_noAttributes(int size, double fraction, int expectedSize) {

        TestFixture fixture = new TestFixture(size, false);
        Population population = fixture.scenario.getPopulation();

        SBBPopulationSampler sbbPopulationSampler = new SBBPopulationSampler();
        Assert.assertEquals(size, population.getPersons().size());
        for(Person person: population.getPersons().values()) {
            Assert.assertNull(population.getPersonAttributes().getAttribute(person.getId().toString(), "attribute"));
        }
        sbbPopulationSampler.sample(population, fraction);
        Assert.assertEquals(expectedSize, population.getPersons().size());
    }
}
