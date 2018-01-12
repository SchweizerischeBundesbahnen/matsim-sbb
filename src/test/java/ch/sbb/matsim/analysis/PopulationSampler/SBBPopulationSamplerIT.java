package ch.sbb.matsim.analysis.PopulationSampler;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.population.Population;

import ch.sbb.matsim.preparation.PopulationSampler.SBBPopulationSampler;

public class SBBPopulationSamplerIT {

    @Test
    public void test_fraction_1() throws IOException {
        makeTest(10, 1.0, 10);
    }

    @Test
    public void test_fraction_1_100() throws IOException {
        makeTest(100, 1.0, 100);
    }

    @Test
    public void test10_50() throws IOException {
        makeTest(10, 0.5, 4);
    }

    @Test
    public void test100_50() throws IOException {
        makeTest(100, 0.5, 49);
    }

    @Test
    public void test100_30() throws IOException {
        makeTest(100, 0.3, 33);
    }

    @Test
    public void test1000_20() throws IOException {
        makeTest(10000, 0.2, 1959);
    }

    private void makeTest(int size, double fraction, int expectedSize) {

        TestFixture fixture = new TestFixture(size);
        Population population = fixture.scenario.getPopulation();

        SBBPopulationSampler sbbPopulationSampler = new SBBPopulationSampler();
        Assert.assertEquals(size, population.getPersons().size());
        sbbPopulationSampler.sample(population, fraction);
        Assert.assertEquals(expectedSize, population.getPersons().size());
    }
}
