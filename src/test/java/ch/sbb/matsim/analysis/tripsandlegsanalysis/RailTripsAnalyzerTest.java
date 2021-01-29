package ch.sbb.matsim.analysis.tripsandlegsanalysis;

import org.junit.Test;
import org.locationtech.jts.util.Assert;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

public class RailTripsAnalyzerTest {

    @Test
    public void runRailTripsAnalyzerTest() {
        Scenario scenario = setupScenario();
        RailTripsAnalyzer railTripsAnalyzer = new RailTripsAnalyzer(scenario.getTransitSchedule());
        var trips = TripStructureUtils.getTrips(scenario.getPopulation().getPersons().get(Id.createPersonId("mobi-test1")).getSelectedPlan());
        var trip0 = trips.get(0);
        var trip1 = trips.get(1);
        //Realp-Flueelen
        Assert.equals(50442.05, railTripsAnalyzer.calcRailDistance(trip0));
        //Flueelen - Goeschenen
        Assert.equals(38142.03, railTripsAnalyzer.calcRailDistance(trip1));

        var tup0 = railTripsAnalyzer.getOriginDestination(trip0);
        var tup1 = railTripsAnalyzer.getOriginDestination(trip1);
        //Realp
        Assert.equals(tup0.getFirst().toString(), "2537");
        //Flueelen
        Assert.equals(tup0.getSecond().toString(), "1749");
        Assert.equals(tup1.getFirst().toString(), "1749");
        //Goeschenen
        Assert.equals(tup1.getSecond().toString(), "1850");

    }

    private Scenario setupScenario() {
        System.setProperty("matsim.preferLocalDtds", "true");
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile("test/input/scenarios/mobi31test/singleptagent.xml");
        new TransitScheduleReader(scenario).readFile("test/input/scenarios/mobi31test/transitSchedule.xml.gz");
        return scenario;
    }
}