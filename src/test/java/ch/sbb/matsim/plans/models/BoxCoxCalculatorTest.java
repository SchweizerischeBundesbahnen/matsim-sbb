package ch.sbb.matsim.plans.models;

import ch.sbb.matsim.plans.synpop.SynpopConstants;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.population.PopulationUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoxCoxCalculatorTest {

    @Test
    void testCalculateLambdaNotZero() {
        Person person = PopulationUtils.getFactory().createPerson(Id.createPersonId(0));
        person.getAttributes().putAttribute(SynpopConstants.AGE, 30);
        BoxCoxCalculator calculator = new BoxCoxCalculator("age", 0.5, 0.2);
        double result = calculator.calculate(person);
        assertEquals(2.43587621459, result, 0.001);

    }

    @Test
    void testCalculateLambdaZero() {
        Person person = PopulationUtils.getFactory().createPerson(Id.createPersonId(0));
        person.getAttributes().putAttribute(SynpopConstants.AGE, 30);
        BoxCoxCalculator calculator = new BoxCoxCalculator("age", 0.5, 0);
        double result = calculator.calculate(person);
        double expected = Math.log(30) * 0.5;
        assertEquals(expected, result, 0.001);
    }
}