package ch.sbb.matsim.analysis;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.handler.EventHandler;
import org.matsim.core.mobsim.qsim.*;
import org.matsim.core.population.routes.LinkNetworkRouteFactory;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.counts.Count;
import org.matsim.counts.Counts;
import org.matsim.testcases.MatsimTestUtils;
import org.matsim.testcases.utils.EventsCollector;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;


public class LinkVolumeToCSVTest {
    @Rule
    public MatsimTestUtils utils = new MatsimTestUtils();

    @Test
    public void test_allLinks() throws IOException {

        TestFixture testFixture = new TestFixture();
        LinkVolumeToCSV linkVolumeToCSV = new LinkVolumeToCSV(testFixture.scenario, this.utils.getOutputDirectory());

        testFixture.addDemand();
        testFixture.addEvents(linkVolumeToCSV);

        linkVolumeToCSV.closeFile();

        BufferedReader br = new BufferedReader(new FileReader(this.utils.getOutputDirectory() + "matsim_linkvolumes.csv"));
        StringBuilder sb = new StringBuilder();
        String line = br.readLine();

        while (line != null) {
            sb.append(line);
            sb.append("\n");
            line = br.readLine();
        }

        String data = sb.toString();
        Assert.assertEquals(expectedFull, data);
    }

    @Test
    public void test_withLinkFilter() throws IOException {
        TestFixture testFixture = new TestFixture();
        Counts<Link> counts = new Counts<>();
        Count<Link> count = counts.createAndAddCount(Id.create(3, Link.class), "in the ghetto");
        count.createVolume(1, 987); // we'll probably only provide daily values in the first hour.
        testFixture.scenario.addScenarioElement(Counts.ELEMENT_NAME, counts);
        LinkVolumeToCSV linkVolumeToCSV = new LinkVolumeToCSV(testFixture.scenario, this.utils.getOutputDirectory());

        testFixture.addDemand();
        testFixture.addEvents(linkVolumeToCSV);

        linkVolumeToCSV.closeFile();

        BufferedReader br = new BufferedReader(new FileReader(this.utils.getOutputDirectory() + "matsim_linkvolumes.csv"));
        StringBuilder sb = new StringBuilder();
        String line = br.readLine();

        while (line != null) {
            sb.append(line);
            sb.append("\n");
            line = br.readLine();
        }

        String data = sb.toString();
        Assert.assertEquals(expectedFiltered, data);
    }

    private class TestFixture {
        public Scenario scenario;
        public Config config;
        private Link link1;
        private Link link2;
        private Link link3;

        TestFixture() {
            config = ConfigUtils.createConfig();
            config.qsim().setEndTime(35000);
            Scenario scenario = ScenarioUtils.createScenario(config);
            this.scenario = scenario;

            Network network = scenario.getNetwork();

            NetworkFactory nf = network.getFactory();

            Node node2 = nf.createNode(Id.create(2, Node.class), new Coord(15000, 0));
            Node node3 = nf.createNode(Id.create(3, Node.class), new Coord(25000, 0));
            Node node4 = nf.createNode(Id.create(4, Node.class), new Coord(35000, 0));
            Node node5 = nf.createNode(Id.create(5, Node.class), new Coord(40000, 0));

            network.addNode(node2);
            network.addNode(node3);
            network.addNode(node4);
            network.addNode(node5);

            link1 = createLink(nf, 2, 1200, node2, node3);
            link2 = createLink(nf, 3, 1200, node3, node4);
            link3 = createLink(nf, 4, 6500, node4, node5);

            network.addLink(link1);
            network.addLink(link2);
            network.addLink(link3);


        }

        private Link createLink(NetworkFactory nf, int id, double length, Node fromNode, Node toNode) {
            Link link = nf.createLink(Id.create(id, Link.class), fromNode, toNode);
            link.setLength(length);
            Set<String> mode = new HashSet<>(Arrays.asList("car", "ride"));
            link.setAllowedModes(mode);
            link.setFreespeed(34.3);
            link.setCapacity(1000);
            link.setNumberOfLanes(1);
            return link;
        }

        public void addEvents(EventHandler handler) {
            EventsManager eventsManager = EventsUtils.createEventsManager(config);
            QSim qSim = QSimUtils.createDefaultQSim(scenario, eventsManager);

            EventsCollector collector = new EventsCollector();
            eventsManager.addHandler(collector);
            eventsManager.addHandler(handler);

            qSim.run();
            List<Event> allEvents = collector.getEvents();

            for (Event event : allEvents) {
                System.out.println(event.toString());
            }

        }

        void addDemand() {
            Population population = this.scenario.getPopulation();
            PopulationFactory pf = population.getFactory();
            Person person = pf.createPerson(Id.create(1, Person.class));
            Plan plan = pf.createPlan();
            Activity act1 = pf.createActivityFromLinkId("home", link1.getId());
            act1.setEndTime(29500);
            Leg leg = pf.createLeg("car");

            LinkNetworkRouteFactory factory = new LinkNetworkRouteFactory();
            NetworkRoute route = (NetworkRoute) factory.createRoute(link1.getId(), link3.getId());
            route.setLinkIds(link1.getId(), Arrays.asList(link2.getId()), link3.getId());
            leg.setRoute(route);

            leg.setRoute(route);
            Activity act2 = pf.createActivityFromLinkId("work", link3.getId());

            plan.addActivity(act1);
            plan.addLeg(leg);
            plan.addActivity(act2);
            person.addPlan(plan);
            population.addPerson(person);
        }


    }

    private String expectedFull = "link_id;mode;bin;volume\n" +
            "2;car;1;0\n" +
            "2;car;2;0\n" +
            "2;car;3;0\n" +
            "2;car;4;0\n" +
            "2;car;5;0\n" +
            "2;car;6;0\n" +
            "2;car;7;0\n" +
            "2;car;8;0\n" +
            "2;car;9;1\n" +
            "2;car;10;0\n" +
            "2;car;11;0\n" +
            "2;car;12;0\n" +
            "2;car;13;0\n" +
            "2;car;14;0\n" +
            "2;car;15;0\n" +
            "2;car;16;0\n" +
            "2;car;17;0\n" +
            "2;car;18;0\n" +
            "2;car;19;0\n" +
            "2;car;20;0\n" +
            "2;car;21;0\n" +
            "2;car;22;0\n" +
            "2;car;23;0\n" +
            "2;car;24;0\n" +
            "2;car;25;0\n" +
            "3;car;1;0\n" +
            "3;car;2;0\n" +
            "3;car;3;0\n" +
            "3;car;4;0\n" +
            "3;car;5;0\n" +
            "3;car;6;0\n" +
            "3;car;7;0\n" +
            "3;car;8;0\n" +
            "3;car;9;1\n" +
            "3;car;10;0\n" +
            "3;car;11;0\n" +
            "3;car;12;0\n" +
            "3;car;13;0\n" +
            "3;car;14;0\n" +
            "3;car;15;0\n" +
            "3;car;16;0\n" +
            "3;car;17;0\n" +
            "3;car;18;0\n" +
            "3;car;19;0\n" +
            "3;car;20;0\n" +
            "3;car;21;0\n" +
            "3;car;22;0\n" +
            "3;car;23;0\n" +
            "3;car;24;0\n" +
            "3;car;25;0\n";

    private String expectedFiltered = "link_id;mode;bin;volume\n" +
            "3;car;1;0\n" +
            "3;car;2;0\n" +
            "3;car;3;0\n" +
            "3;car;4;0\n" +
            "3;car;5;0\n" +
            "3;car;6;0\n" +
            "3;car;7;0\n" +
            "3;car;8;0\n" +
            "3;car;9;1\n" +
            "3;car;10;0\n" +
            "3;car;11;0\n" +
            "3;car;12;0\n" +
            "3;car;13;0\n" +
            "3;car;14;0\n" +
            "3;car;15;0\n" +
            "3;car;16;0\n" +
            "3;car;17;0\n" +
            "3;car;18;0\n" +
            "3;car;19;0\n" +
            "3;car;20;0\n" +
            "3;car;21;0\n" +
            "3;car;22;0\n" +
            "3;car;23;0\n" +
            "3;car;24;0\n" +
            "3;car;25;0\n";

}