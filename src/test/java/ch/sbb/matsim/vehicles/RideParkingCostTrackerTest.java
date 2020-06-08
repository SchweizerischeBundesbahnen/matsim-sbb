package ch.sbb.matsim.vehicles;

import ch.sbb.matsim.config.ParkingCostConfigGroup;
import ch.sbb.matsim.config.ZonesListConfigGroup;
import ch.sbb.matsim.events.ParkingCostEvent;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesLoader;
import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.testcases.utils.EventsCollector;
import org.matsim.testcases.utils.EventsLogger;
import org.matsim.vehicles.Vehicle;

/**
 * @author mrieser
 */
public class RideParkingCostTrackerTest {

    @Test
    public void testParkingCostEvents() {
        Fixture f = new Fixture();

        f.events.addHandler(new EventsLogger());

        RideParkingCostTracker tracker = new RideParkingCostTracker(f.scenario, f.zones, f.events);
        f.events.addHandler(tracker);
        EventsCollector collector = new EventsCollector();
        f.events.addHandler(collector);

        Id<Person> personId = Id.create(1, Person.class);
        Id<Vehicle> vehicleId = Id.create(2, Vehicle.class);
        Id<Link> linkHome = Id.create("L", Link.class);
        Id<Link> linkWork = Id.create("B", Link.class);
        Id<Link> linkShop = Id.create("T", Link.class);

        double hourlyParkingCostWork = 20; // this is the value of ACCCAR in zone Bern
        double hourlyParkingCostShop = 3; // this is the value of ACCCAR in zone Thun

        f.events.processEvent(new PersonArrivalEvent(7.25 * 3600, personId, linkWork, "ride"));
        f.events.processEvent(new ActivityStartEvent(7.25 * 3600, personId, linkWork, null, "work", null));
        Assert.assertEquals(2, collector.getEvents().size());

        f.events.processEvent(new ActivityEndEvent(12.00*3600, personId, linkWork, null, "work"));
        Assert.assertEquals(4, collector.getEvents().size());

        Assert.assertEquals(ParkingCostEvent.class, collector.getEvents().get(2).getClass());
        ParkingCostEvent parkingEvent1 = (ParkingCostEvent) collector.getEvents().get(2);
        Assert.assertEquals(personId, parkingEvent1.getPersonId());
        Assert.assertNull(parkingEvent1.getVehicleId());
        Assert.assertEquals(12.00*3600, parkingEvent1.getTime(), 1e-8);
        Assert.assertEquals((12-7.25) * hourlyParkingCostWork, parkingEvent1.getMonetaryAmount(), 1e-8);

        f.events.processEvent(new PersonArrivalEvent(12.25 * 3600, personId, linkShop, "ride"));
        f.events.processEvent(new ActivityStartEvent(12.25 * 3600, personId, linkShop, null, "shop", null));
        Assert.assertEquals(6, collector.getEvents().size());

        f.events.processEvent(new ActivityEndEvent(13.00*3600, personId, linkShop, null, "shop"));
        Assert.assertEquals(8, collector.getEvents().size());

        Assert.assertEquals(ParkingCostEvent.class, collector.getEvents().get(6).getClass());
        ParkingCostEvent parkingEvent2 = (ParkingCostEvent) collector.getEvents().get(6);
        Assert.assertEquals(personId, parkingEvent2.getPersonId());
        Assert.assertNull(parkingEvent2.getVehicleId());
        Assert.assertEquals(13.00*3600, parkingEvent2.getTime(), 1e-8);
        Assert.assertEquals((13-12.25) * hourlyParkingCostShop, parkingEvent2.getMonetaryAmount(), 1e-8);

        f.events.processEvent(new PersonArrivalEvent(13.25 * 3600, personId, linkHome, "ride"));
        f.events.processEvent(new ActivityStartEvent(13.25 * 3600, personId, linkHome, null, "home", null));
        Assert.assertEquals(10, collector.getEvents().size());

        f.events.processEvent(new ActivityEndEvent(15.00*3600, personId, linkShop, null, "home"));
        Assert.assertEquals(11, collector.getEvents().size()); // there should be no parking cost event at home

        f.events.processEvent(new PersonArrivalEvent(15.25 * 3600, personId, linkWork, "ride"));
        f.events.processEvent(new ActivityStartEvent(13.25 * 3600, personId, linkWork, null, "shop", null));
        Assert.assertEquals(13, collector.getEvents().size());

        f.events.processEvent(new ActivityEndEvent(18.00*3600, personId, linkWork, null, "shop"));
        Assert.assertEquals(15, collector.getEvents().size());

        Assert.assertEquals(ParkingCostEvent.class, collector.getEvents().get(13).getClass());
        ParkingCostEvent parkingEvent3 = (ParkingCostEvent) collector.getEvents().get(13);
        Assert.assertEquals(personId, parkingEvent3.getPersonId());
        Assert.assertNull(parkingEvent3.getVehicleId());
        Assert.assertEquals(18.00*3600, parkingEvent3.getTime(), 1e-8);
        Assert.assertEquals((18-15.25) * hourlyParkingCostWork, parkingEvent3.getMonetaryAmount(), 1e-8);

        f.events.processEvent(new PersonArrivalEvent(18.50 * 3600, personId, linkHome, "ride"));
        f.events.processEvent(new ActivityStartEvent(18.00 * 3600, personId, linkHome, null, "shop", null));
        Assert.assertEquals(17, collector.getEvents().size());
    }

    /**
     * Creates a simple test scenario matching the accesstime_zone.SHP test file.
     */
    private static class Fixture {
        Config config;
        Scenario scenario;
        ZonesCollection zones = new ZonesCollection();
        EventsManager events;

        public Fixture() {
            this.config = ConfigUtils.createConfig();
            prepareConfig();
            this.scenario = ScenarioUtils.createScenario(this.config);
            createNetwork();
            loadZones();
            prepareEvents();
        }

        private void prepareConfig() {
            ZonesListConfigGroup zonesConfig = ConfigUtils.addOrGetModule(this.config, ZonesListConfigGroup.class);
            ZonesListConfigGroup.ZonesParameterSet parkingZonesConfig = new ZonesListConfigGroup.ZonesParameterSet();
            parkingZonesConfig.setFilename("src/test/resources/shapefiles/AccessTime/accesstime_zone.SHP");
            parkingZonesConfig.setId("parkingZones");
            parkingZonesConfig.setIdAttributeName("ID");
            zonesConfig.addZones(parkingZonesConfig);

            ParkingCostConfigGroup parkingConfig = ConfigUtils.addOrGetModule(this.config, ParkingCostConfigGroup.class);
            parkingConfig.setZonesId("parkingZones");
            parkingConfig.setZonesRideParkingCostAttributeName("ACCCAR"); // yes, we misuse the access times in the test data as parking costs
        }

        private void createNetwork() {
            Network network = this.scenario.getNetwork();
            NetworkFactory nf = network.getFactory();

            Node nL1 = nf.createNode(Id.create("L1", Node.class), new Coord(545000, 150000));
            Node nL2 = nf.createNode(Id.create("L2", Node.class), new Coord(540000, 165000));
            Node nB1 = nf.createNode(Id.create("B1", Node.class), new Coord(595000, 205000));
            Node nB2 = nf.createNode(Id.create("B2", Node.class), new Coord(605000, 195000));
            Node nT1 = nf.createNode(Id.create("T1", Node.class), new Coord(610000, 180000));
            Node nT2 = nf.createNode(Id.create("T2", Node.class), new Coord(620000, 175000));

            network.addNode(nL1);
            network.addNode(nL2);
            network.addNode(nB1);
            network.addNode(nB2);
            network.addNode(nT1);
            network.addNode(nT2);

            Link lL = createLink(nf, "L", nL1, nL2, 500, 1000, 10);
            Link lLB = createLink(nf, "LB", nL2, nB1, 5000, 2000, 25);
            Link lB = createLink(nf, "B", nB1, nB2, 500, 1000, 10);
            Link lBT = createLink(nf, "BT", nB2, nT1, 5000, 2000, 25);
            Link lT = createLink(nf, "T", nT1, nT2, 500, 1000, 10);
            Link lTL = createLink(nf, "TL", nT2, nL1, 5000, 2000, 25);

            network.addLink(lL);
            network.addLink(lLB);
            network.addLink(lB);
            network.addLink(lBT);
            network.addLink(lT);
            network.addLink(lTL);
        }

        private Link createLink(NetworkFactory nf, String id, Node fromNode, Node toNode, double length, double capacity, double freespeed) {
            Link l = nf.createLink(Id.create(id, Link.class), fromNode, toNode);
            l.setLength(length);
            l.setCapacity(capacity);
            l.setFreespeed(freespeed);
            l.setAllowedModes(CollectionUtils.stringToSet("car"));
            l.setNumberOfLanes(1);
            return l;
        }

        private void loadZones() {
            ZonesLoader.loadAllZones(config, this.zones);
        }

        private void prepareEvents() {
            this.events = EventsUtils.createEventsManager(this.config);
        }

    }
}