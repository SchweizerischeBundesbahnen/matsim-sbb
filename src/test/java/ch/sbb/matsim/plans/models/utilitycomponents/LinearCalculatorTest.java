package ch.sbb.matsim.plans.models.utilitycomponents;

import ch.sbb.matsim.plans.synpop.SynpopConstants;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.population.PopulationUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LinearCalculatorTest {

    @Test
    void testCalculate() {
        LinearCalculator calculator = new LinearCalculator("age", 0.5);
        Person person = PopulationUtils.getFactory().createPerson(Id.createPersonId(0));
        person.getAttributes().putAttribute(SynpopConstants.AGE, 40);

        double result = calculator.calculate(person);

        assertEquals(20.0, result, 0.001);
    }

}