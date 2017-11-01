/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import ch.sbb.matsim.routing.SBBTransitRouterImpl;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.LinkNetworkRouteImpl;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.router.FakeFacility;
import org.matsim.pt.router.TransitRouterConfig;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleCapacityImpl;
import org.matsim.vehicles.VehicleType;

import static org.junit.Assert.assertEquals;


/**
 * @author patrickm
 *
 * purpose: testing the SBBTransitRouter
 *
 * current tests:
 *    - transfer walk distance
 *
 */

public class SBBTransitRouterTest {

    @Test
    public void testTransferWalkDistance() {
        PTFixture f = new PTFixture();

        SBBTransitRouterImpl router = new SBBTransitRouterImpl(f.routerConfig, f.schedule);

        Coord fromCoord = new Coord(0.0, 100.0);
        Coord toCoord = new Coord(10000.0, 5100.0);
        List<Leg> legs = router.calcRoute(new FakeFacility(fromCoord), new FakeFacility(toCoord), 8 * 3600, null);
        
        assertEquals(7, legs.size());
        assertEquals(TransportMode.transit_walk, legs.get(2).getMode());
        assertEquals(325.0, legs.get(2).getRoute().getDistance(),0);
        assertEquals(TransportMode.transit_walk, legs.get(4).getMode());
        assertEquals(325.0, legs.get(4).getRoute().getDistance(),0);
    }

    /**
     * Generates the following network for testing:
     * <pre>
     *                                             (W)
     * (B1)--B2--B3--B4--B5--B6--B7--B8--B9--B10--(B11)
     *                      (C5)
     *                        |
     *                       C4
     *                        |
     *                       C3
     *                        |
     *                       C2
     *                        |
     *                      (C1)
     * (A1)--A2--A3--A4--A5--A6--A7--A8--A9--A10--(A11)
     * (H)
     * </pre>
     * <p>
     * Three transit lines (A, B, C)
     * Agent leaves at (H) and works at (W)
     * The agent must walk between A5 and C1 and C5 and B5
     *
     * @author patrickm
     */
    private static class PTFixture {

        final MutableScenario scenario;
        final TransitSchedule schedule;
        final TransitRouterConfig routerConfig;

        private static final double FREESPEED = 40 / 3.6;
        private static final double CAPACITY = 10000;
        private static final String VEHICLETYPE1 = "bus";
        private static Set<String> allowedModes = new HashSet<>();

        PTFixture() {
            this.scenario = (MutableScenario) ScenarioUtils.createScenario(ConfigUtils.createConfig());
            this.scenario.getConfig().transit().setUseTransit(true);
            this.routerConfig = new TransitRouterConfig(this.scenario.getConfig().planCalcScore(),
                    this.scenario.getConfig().plansCalcRoute(), this.scenario.getConfig().transitRouter(),
                    this.scenario.getConfig().vspExperimental());
            this.routerConfig.setSearchRadius(500.0);
            this.routerConfig.setBeelineWalkConnectionDistance(300);

            this.schedule = this.scenario.getTransitSchedule();
            TransitScheduleFactory sb = this.schedule.getFactory();


            this.allowedModes.add(TransportMode.pt);
            this.allowedModes.add(TransportMode.car);

            // create a public transport vehicle type (bus)
            VehicleType vehicleType1 = createVehicleType(VEHICLETYPE1);

            double stopTime = 0.5 * 60;
            double startTime = 8;
            double endTime = 10;
            double headway = 5.0/60;

            // create lines
            createTransitLine("A", new Coord(0.0, 0.0), new Coord(10000.0, 0.0), 11, stopTime, vehicleType1, startTime, endTime, headway);
            createTransitLine("B", new Coord(0.0, 5000.0), new Coord(10000.0, 5000.0), 11, stopTime, vehicleType1, startTime, endTime, headway);
            createTransitLine("C", new Coord(5000.0, 250.0), new Coord(5000.0, 4750.0), 7, stopTime, vehicleType1, startTime, endTime, headway);
        }

        private VehicleType createVehicleType(String type) {
            VehicleType vehType = scenario.getTransitVehicles().getFactory().createVehicleType(Id.create(type, VehicleType.class));
            VehicleCapacity vehCap = new VehicleCapacityImpl();
            vehCap.setSeats(1000);
            vehCap.setStandingRoom(1000);
            vehType.setCapacity(vehCap);
            vehType.setLength(40.0);
            scenario.getTransitVehicles().addVehicleType(vehType);

            return vehType;
        }

        private void createTransitLine(String lineName, Coord fromCoord, Coord toCoord, int numberOfStops, double stopTime,
                VehicleType vehType, double startTime, double endTime, double headway) {
            createNetwork(fromCoord, toCoord, numberOfStops);
            createStopFacilities(lineName, fromCoord, toCoord, numberOfStops);
            createRoute(lineName, fromCoord, toCoord, numberOfStops, stopTime);
            createDepartures(lineName, vehType, startTime, endTime, headway);
        }

        private void createNetwork(Coord fromCoord, Coord toCoord, int numberOfStops) {
            double intervalX = (toCoord.getX() - fromCoord.getX()) / (numberOfStops - 1);
            double intervalY = (toCoord.getY() - fromCoord.getY()) / (numberOfStops - 1);
            double y = fromCoord.getY();
            double x = fromCoord.getX();
            Node previousNode = null;
            // create nodes and links for lines A and B
            do {
                do {
                    Id<Node> nId = Id.createNodeId(String.format("%05d", (int)x) + "_" + String.format("%05d", (int)y));
                    Node n = scenario.getNetwork().getFactory().createNode(nId, new Coord(x, y));
                    scenario.getNetwork().addNode(n);

                    if (previousNode != null) {
                        Id<Link> lId = Id.createLinkId(previousNode.getId().toString() + "-" + n.getId().toString());
                        Link l = scenario.getNetwork().getFactory().createLink(lId, previousNode, n);
                        l.setLength(NetworkUtils.getEuclideanDistance(previousNode.getCoord(), n.getCoord()));
                        l.setFreespeed(FREESPEED);
                        l.setCapacity(CAPACITY);
                        l.setAllowedModes(allowedModes);

                        scenario.getNetwork().addLink(l);
                    }
                    previousNode = n;

                    x += intervalX;
                } while ( x < toCoord.getX());

                y += intervalY;
            } while (y < toCoord.getY());

            Id<Node> nId = Id.createNodeId(String.format("%05d", (int)toCoord.getX()) + "_" + String.format("%05d", (int)toCoord.getY()));
            Node n = scenario.getNetwork().getFactory().createNode(nId, new Coord(toCoord.getX(), toCoord.getY()));
            boolean hasNode = false;
            for(Node no: scenario.getNetwork().getNodes().values()) {
                if(n.getCoord().equals(no.getCoord()))
                    hasNode = true;
            }
            if(!hasNode)
                scenario.getNetwork().addNode(n);

            Id<Link> lId = Id.createLinkId(previousNode.getId().toString() + "-" + n.getId().toString());
            Link l = scenario.getNetwork().getFactory().createLink(lId, previousNode, n);
            l.setLength(NetworkUtils.getEuclideanDistance(previousNode.getCoord(), n.getCoord()));
            l.setFreespeed(FREESPEED);
            l.setCapacity(CAPACITY);
            l.setAllowedModes(allowedModes);
            scenario.getNetwork().addLink(l);
        }

        private void createStopFacilities(String lineName, Coord fromCoord, Coord toCoord, int numberOfStops) {
            double intervalX = (toCoord.getX() - fromCoord.getX()) / (numberOfStops - 1);
            double intervalY = (toCoord.getY() - fromCoord.getY()) / (numberOfStops - 1);
            double y = fromCoord.getY();
            double x = fromCoord.getX();
            int stopCounter = 1;

            do {
                do {
                    Id<Node> nId = Id.createNodeId(String.format("%05d", (int) x) + "_" + String.format("%05d", (int) y));
                    Node n = scenario.getNetwork().getNodes().get(nId);
                    Id<Link> lId = Id.createLinkId(n.getId().toString() + "-HS");
                    Link l = scenario.getNetwork().getFactory().createLink(lId, n, n);
                    l.setAllowedModes(allowedModes);
                    l.setFreespeed(FREESPEED);
                    l.setLength(0.0);
                    scenario.getNetwork().addLink(l);

                    Id<TransitStopFacility> stId = Id.create("Line" + lineName + "_" + String.format("%03d", stopCounter), TransitStopFacility.class);
                    TransitStopFacility st = scenario.getTransitSchedule().getFactory().createTransitStopFacility(stId, new Coord(x, y), false);
                    st.setLinkId(lId);
                    scenario.getTransitSchedule().addStopFacility(st);
                    stopCounter++;

                    x += intervalX;
                } while ( x < toCoord.getX());

                y += intervalY;
            } while (y < toCoord.getY());

            Id<Node> nId = Id.createNodeId(String.format("%05d", (int) toCoord.getX()) + "_" + String.format("%05d", (int) toCoord.getY()));
            Node n = scenario.getNetwork().getNodes().get(nId);
            Id<Link> lId = Id.createLinkId(n.getId().toString() + "-HS");
            Link l = scenario.getNetwork().getFactory().createLink(lId, n, n);
            l.setAllowedModes(allowedModes);
            l.setFreespeed(FREESPEED);
            l.setLength(0.0);
            boolean hasLink = false;
            for(Link li: scenario.getNetwork().getLinks().values()) {
                if(l.getFromNode().equals(li.getFromNode()) && l.getToNode().equals(li.getToNode()))
                    hasLink = true;
            }
            if(!hasLink)
                scenario.getNetwork().addLink(l);

            Id<TransitStopFacility> stId = Id.create("Line" + lineName + "_" + String.format("%03d", stopCounter), TransitStopFacility.class);
            TransitStopFacility st = scenario.getTransitSchedule().getFactory().createTransitStopFacility(stId, new Coord(x, y), false);
            st.setLinkId(lId);
            scenario.getTransitSchedule().addStopFacility(st);
        }

        private void createRoute(String lineName, Coord fromCoord, Coord toCoord, int numberOfStops, double stopTime)  {

            // create transit line
            TransitLine line = scenario.getTransitSchedule().getFactory().createTransitLine(Id.create("Line" + lineName, TransitLine.class));

            Id<Link> startLink = Id.createLinkId(String.format("%05d", (int)fromCoord.getX()) + "_" + String.format("%05d", (int)fromCoord.getY()) + "-HS");
            Id<Link> endLink = Id.createLinkId(String.format("%05d", (int)toCoord.getX()) + "_" + String.format("%05d", (int)toCoord.getY()) + "-HS");

            LinkNetworkRouteImpl rou = new LinkNetworkRouteImpl(startLink, endLink);

            List<Link> links = new LinkedList<>();
            List<TransitRouteStop> stops = new ArrayList<>();

            TransitStopFacility firstStop = scenario.getTransitSchedule().getFacilities().get(Id.create("Line" + lineName + "_" + String.format("%03d", 1), TransitStopFacility.class));
            TransitRouteStop st = scenario.getTransitSchedule().getFactory().createTransitRouteStop(firstStop, 0.0, 0.0);
            st.setAwaitDepartureTime(true);
            stops.add(st);

            double x_ = fromCoord.getX();
            double y_ = fromCoord.getY();
            int stopCounter = 2;
            double time = 0.0;

            double intervalX = (toCoord.getX() - fromCoord.getX()) / (numberOfStops - 1);
            double intervalY = (toCoord.getY() - fromCoord.getY()) / (numberOfStops - 1);
            double y = fromCoord.getY() + intervalY;
            double x = fromCoord.getX() + intervalX;

            do {
                do {
                    Id<Link> lId = Id.createLinkId(String.format("%05d", (int)x_) + "_" + String.format("%05d", (int)y_) + "-" +
                            String.format("%05d", (int)x) + "_" + String.format("%05d", (int)y));
                    links.add(scenario.getNetwork().getLinks().get(lId));
                    links.add(scenario.getNetwork().getLinks().get(Id.createLinkId(String.format("%05d", (int)x) + "_" + String.format("%05d", (int)y) + "-HS")));

                    x_ = x;
                    y_ = y;

                    TransitStopFacility sto = scenario.getTransitSchedule().getFacilities().get(Id.create("Line" + lineName + "_" + String.format("%03d", stopCounter), TransitStopFacility.class));
                    time += scenario.getNetwork().getLinks().get(lId).getLength() / FREESPEED;
                    TransitRouteStop rst = scenario.getTransitSchedule().getFactory().createTransitRouteStop(sto, time, time + stopTime);
                    rst.setAwaitDepartureTime(true);
                    stops.add(rst);
                    time += stopTime;
                    stopCounter++;

                    x += intervalX;
                } while ( x < toCoord.getX());

                y += intervalY;
            } while (y < toCoord.getY());

            Id<Link> lId = Id.createLinkId(String.format("%05d", (int)x_) + "_" + String.format("%05d", (int)y_) + "-" +
                    String.format("%05d", (int)toCoord.getX()) + "_" + String.format("%05d", (int)toCoord.getY()));
            links.add(scenario.getNetwork().getLinks().get(lId));

            TransitStopFacility sto = scenario.getTransitSchedule().getFacilities().get(Id.create("Line" + lineName + "_" + String.format("%03d", stopCounter), TransitStopFacility.class));
            time += scenario.getNetwork().getLinks().get(lId).getLength() / FREESPEED;
            TransitRouteStop rst = scenario.getTransitSchedule().getFactory().createTransitRouteStop(sto, time, time);
            rst.setAwaitDepartureTime(true);
            stops.add(rst);

            rou.setLinkIds(startLink, NetworkUtils.getLinkIds(links), endLink);

            TransitRoute route = scenario.getTransitSchedule().getFactory().createTransitRoute(Id.create("Route" + lineName + "1", TransitRoute.class), rou, stops, "pt");

            scenario.getTransitSchedule().addTransitLine(line);
            scenario.getTransitSchedule().getTransitLines().get(Id.create("Line" + lineName, TransitLine.class)).addRoute(route);
        }

        private void createDepartures(String lineName, VehicleType vehType, double startTime, double endTime, double headway) {
            int depCounter = 1;
            for(double i = startTime; i <= endTime; i += headway) {
                Vehicle veh = scenario.getTransitVehicles().getFactory().createVehicle(Id.create(vehType.getId().toString() + "_"+lineName+"1_" + String.format("%03d", depCounter), Vehicle.class), vehType);
                scenario.getTransitVehicles().addVehicle(veh);

                Departure dep = scenario.getTransitSchedule().getFactory().createDeparture(Id.create("Dep_"+lineName+"1_" + String.format("%03d", depCounter), Departure.class), i * 3600);
                dep.setVehicleId(Id.create(vehType.getId().toString() + "_"+lineName+"1_" + String.format("%03d", depCounter), Vehicle.class));

                scenario.getTransitSchedule().getTransitLines().get(Id.create("Line"+lineName, TransitLine.class)).getRoutes().get(Id.create("Route"+lineName+"1", TransitRoute.class)).addDeparture(dep);

                depCounter++;
            }
        }
    }
}


/*
    private void createPopulation(Scenario scenario) {

        // one agent show
        Person person = scenario.getPopulation().getFactory().createPerson(Id.create("person", Person.class));
        Plan plan = scenario.getPopulation().getFactory().createPlan();
        person.addPlan(plan);

        Activity startActivity = scenario.getPopulation().getFactory().createActivityFromCoord("home", new Coord(0.0, 100.0));
        startActivity.setEndTime(8.2*3600);
        Leg leg = scenario.getPopulation().getFactory().createLeg(TransportMode.pt);
        Activity endActivity = scenario.getPopulation().getFactory().createActivityFromCoord("work", new Coord(10100.0, 5000.0));
        endActivity.setStartTime(9.2*3600);

        plan.addActivity(startActivity);
        plan.addLeg(leg);
        plan.addActivity(endActivity);

        scenario.getPopulation().addPerson(person);
    }
*/
