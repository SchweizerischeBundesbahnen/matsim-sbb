package ch.sbb.matsim.analysis.tripsandlegsanalysis;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesLoader;
import org.junit.Test;
import org.locationtech.jts.util.Assert;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class RailTripsAnalyzerTest {

    @Test
    public void runRailTripsAnalyzerTest() {
        Scenario scenario = setupScenario();
        ZonesCollection zonesCollection = new ZonesCollection();
        zonesCollection.addZones(ZonesLoader.loadZones("zones", "test/input/scenarios/mobi31test/zones/andermatt-zones.shp"));
        RailTripsAnalyzer railTripsAnalyzer = new RailTripsAnalyzer(scenario.getTransitSchedule(), scenario.getNetwork(), zonesCollection);
        var trips = TripStructureUtils.getTrips(scenario.getPopulation().getPersons().get(Id.createPersonId("mobi-test1")).getSelectedPlan());
        var trip0 = trips.get(0);
        var trip1 = trips.get(1);
        //Realp-Flueelen
        Assert.equals(50442.05, railTripsAnalyzer.calcRailDistance(trip0));
        //Flueelen - Goeschenen
        Assert.equals(38142.03, railTripsAnalyzer.calcRailDistance(trip1));
        Assert.equals(38142.03, railTripsAnalyzer.getFQDistance(trip1, true));
        Id<TransitStopFacility> stopIdRealp = Id.create(2537, TransitStopFacility.class);
        Assert.equals(true, railTripsAnalyzer.isSwissRailStop(stopIdRealp));
        Assert.equals(true, railTripsAnalyzer.isSwissRailOrFQStop(stopIdRealp));

        var tup0 = railTripsAnalyzer.getOriginDestination(trip0);
        var tup1 = railTripsAnalyzer.getOriginDestination(trip1);
        //Realp
        Assert.equals(tup0.getFirst().toString(), "2537");
        //Flueelen
        Assert.equals(tup0.getSecond().toString(), "1749");
        Assert.equals(tup1.getFirst().toString(), "1749");
        //Goeschenen
        Assert.equals(tup1.getSecond().toString(), "1850");
        Leg trainLeg = trip0.getLegsOnly().stream().filter(leg -> leg.getMode().equals(SBBModes.PT)).findAny().get();
        TransitPassengerRoute route = (TransitPassengerRoute) trainLeg.getRoute();

        List<Id<Link>> ptLinksTraveledOn = List.of(Id.create("pt_2537", Link.class), Id.create("pt_2537-pt_1959", Link.class), Id.create("pt_1959", Link.class), Id.create("pt_1959-pt_1187", Link.class), Id.create("pt_1187", Link.class));
        List<Id<Link>> ptLinksTraveledOnExcludingAccessEgressStop = List.of(Id.create("pt_2537-pt_1959", Link.class), Id.create("pt_1959", Link.class), Id.create("pt_1959-pt_1187", Link.class));
        Assert.equals(ptLinksTraveledOnExcludingAccessEgressStop, railTripsAnalyzer.getPtLinkIdsTraveledOnExludingAccessEgressStop(route));
        Assert.equals(ptLinksTraveledOn, railTripsAnalyzer.getPtLinkIdsTraveledOn(route));
        Map<Id<TransitLine>, Set<Id<TransitRoute>>> linesAtRealp = railTripsAnalyzer.getTransitLinesAndRoutesAtStop(stopIdRealp);
        Assert.equals(linesAtRealp.size(), 1);
        Id<TransitLine> lineAtRealp = Id.create("Simba2017_003-D-15104", TransitLine.class);
        Assert.equals(linesAtRealp.containsKey(lineAtRealp), true);
        Assert.equals(linesAtRealp.get(lineAtRealp).size(), 4);


        System.out.println(linesAtRealp);
    }

    private Scenario setupScenario() {
        System.setProperty("matsim.preferLocalDtds", "true");
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile("test/input/scenarios/mobi31test/singleptagent.xml");
        new TransitScheduleReader(scenario).readFile("test/input/scenarios/mobi31test/transitSchedule.xml.gz");
        new MatsimNetworkReader(scenario.getNetwork()).readFile("test/input/scenarios/mobi31test/network.xml.gz");
        return scenario;
    }
}