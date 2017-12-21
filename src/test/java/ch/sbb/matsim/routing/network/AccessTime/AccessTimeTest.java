package ch.sbb.matsim.routing.network.AccessTime;

import java.util.ArrayList;

import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;


public class AccessTimeTest {

    public Coord bern = new Coord(600000, 200000); // 20
    public Coord stleo = new Coord(598345.54, 122581.99); // 2


    private double assetScoring(Boolean withAccessTime, double constant, double utilityOfTravaleling, double expected) {
        TestFixture fixture = new TestFixture(bern, stleo, "car", withAccessTime, constant, "car,bike");

        fixture.egressParams.setMarginalUtilityOfTraveling(utilityOfTravaleling);
        fixture.accessParams.setMarginalUtilityOfTraveling(utilityOfTravaleling);

        fixture.run();

        Person person = fixture.population.getPersons().get(Id.createPersonId("1"));
        Double score = person.getSelectedPlan().getScore();
        Assert.assertEquals(expected, score, 0.001);
        return score;

    }

    @Test
    public final void testScoring() {

        ArrayList<Double> scores = new ArrayList<>();

        scores.add(assetScoring(false, 0.0, -1.68, -432));
        scores.add(assetScoring(true, 0.0, 0.0, -432));
        scores.add(assetScoring(true, 0.0, -1.68, -432.009));
        scores.add(assetScoring(true, -1.0, -1.68, -433.009));
        scores.add(assetScoring(true, -10.0, -1.68, -442.009));
        scores.add(assetScoring(true, -10.0, -30, -874.1666));

        System.out.println(scores.toString());
    }


}
