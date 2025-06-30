package ch.sbb.matsim.plans.models.utilitycomponents;

import ch.sbb.matsim.plans.constants.SynpopConstants;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.population.PopulationUtils;

class PiecewiseComponentTest {

    private PiecewiseComponent createCalculator() {
        double[] thresholds = {22, 26, 69, 89};
        double[] betas = {0.398, 0.0301, 0.0201, -0.118, -0.19};
        return new PiecewiseComponent(SynpopConstants.AGE, thresholds, betas);
    }

    @Test
    void testCalculateInMiddle() {
        PiecewiseComponent piecewiseComponent = createCalculator();
        Person person = PopulationUtils.getFactory().createPerson(Id.createPersonId(0));
        person.getAttributes().putAttribute(SynpopConstants.AGE, 42);
        Assert.assertEquals(9.198, piecewiseComponent.calculate(person), 0.001);
    }

    @Test
    void testCalculateAtLowerEnd() {
        PiecewiseComponent piecewiseComponent = createCalculator();
        Person person2 = PopulationUtils.getFactory().createPerson(Id.createPersonId(2));
        person2.getAttributes().putAttribute(SynpopConstants.AGE, 22);
        Assert.assertEquals(8.756, piecewiseComponent.calculate(person2), 0.001);
    }

    @Test
    void testCalculateAtUpperEnd() {
        PiecewiseComponent piecewiseComponent = createCalculator();
        Person person3 = PopulationUtils.getFactory().createPerson(Id.createPersonId(3));
        person3.getAttributes().putAttribute(SynpopConstants.AGE, 90);
        Assert.assertEquals(7.1907, piecewiseComponent.calculate(person3), 0.001);
    }

    @Test
    void testCalculateAboveUpperEnd() {
        PiecewiseComponent piecewiseComponent = createCalculator();
        Person person4 = PopulationUtils.getFactory().createPerson(Id.createPersonId(4));
        person4.getAttributes().putAttribute(SynpopConstants.AGE, 100);
        Assert.assertEquals(5.2907, piecewiseComponent.calculate(person4), 0.001);
    }
}
