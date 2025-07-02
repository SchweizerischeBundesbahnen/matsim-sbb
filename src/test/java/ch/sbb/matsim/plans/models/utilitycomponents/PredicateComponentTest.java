package ch.sbb.matsim.plans.models.utilitycomponents;

import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.population.PopulationUtils;

import java.util.List;

public class PredicateComponentTest {
    @Test
    void testSinglePredicateComponent() {
        UtilityComponent component = UtilityComponentFactory.fromCombinedCondition(List.of("level_of_employment"), List.of(">"),
                List.of("50"),
                1.0);
        UtilityCalculator calculator = new UtilityCalculator(List.of(component));


        Person person2 = PopulationUtils.getFactory().createPerson(Id.createPersonId(2));
        person2.getAttributes().putAttribute("level_of_employment", 60);
        double result = calculator.calculate(person2);
        Assert.assertEquals(result, 1.0, 0.001);

        Person person1 = PopulationUtils.getFactory().createPerson(Id.createPersonId(1));
        person1.getAttributes().putAttribute("level_of_employment", 40);
        double result1 = calculator.calculate(person1);
        Assert.assertEquals(result1, 0.0, 0.001);


    }

    @Test
    void testDoublePredicateComponent() {
        UtilityComponent component = UtilityComponentFactory.fromCombinedCondition(List.of("level_of_employment"), List.of("<", ">"),
                List.of("90", "0"),
                1.0);
        UtilityCalculator calculator = new UtilityCalculator(List.of(component));
        Person person2 = PopulationUtils.getFactory().createPerson(Id.createPersonId(2));
        person2.getAttributes().putAttribute("level_of_employment", 60);
        double result = calculator.calculate(person2);
        Assert.assertEquals(1.0, result, 0.001);

        Person person1 = PopulationUtils.getFactory().createPerson(Id.createPersonId(1));
        person1.getAttributes().putAttribute("level_of_employment", 100);
        double result1 = calculator.calculate(person1);
        Assert.assertEquals(0.0, result1, 0.001);


    }
}
