/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.preparation;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.LinkNetworkRouteImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CutterFixture {
    final MutableScenario scenario;
    final Config config = ConfigUtils.createConfig(new Cutter.CutterConfigGroup());

    final Network network;
    final Population population;

    private final Node[] nodes = new Node[17];
    private final Link[] links = new Link[26];
    private final Person[] persons = new Person[6];

    public CutterFixture() {
        Cutter.CutterConfigGroup cutterConfig = ConfigUtils.addOrGetModule(this.config, Cutter.CutterConfigGroup.class);
        cutterConfig.setxCoordCenter(0);
        cutterConfig.setyCoordCenter(2500);
        cutterConfig.setRadius(4000);

        ScenarioUtils.ScenarioBuilder scBuilder = new ScenarioUtils.ScenarioBuilder(this.config);
        this.scenario = (MutableScenario)scBuilder.build();
        this.network = this.scenario.getNetwork();
        this.population = this.scenario.getPopulation();
    }

    protected void init() {
        this.buildNetwork();
        this.buildPopulation();
    }

    /**
     * Generates the following small network for testing:
     * <pre>
     *     *
     * (1)--*--(3)-----(5)
     *  |    *  |       |
     *  |    *  |       |
     *  |    *  |       |
     * (0)--*--(2)-----(4)
     *     *
     * </pre>
     */
    protected void buildNetwork() {
        this.nodes[0] = this.network.getFactory().createNode(Id.create("0", Node.class), new Coord(0.0D, 0.0D));
        this.nodes[1] = this.network.getFactory().createNode(Id.create("1", Node.class), new Coord(0.0D, 5000.0D));
        this.nodes[2] = this.network.getFactory().createNode(Id.create("2", Node.class), new Coord(5000.0D, 0.0D));
        this.nodes[3] = this.network.getFactory().createNode(Id.create("3", Node.class), new Coord(5000.0D, 5000.0D));
        this.nodes[4] = this.network.getFactory().createNode(Id.create("4", Node.class), new Coord(10000.0D, 0.0D));
        this.nodes[5] = this.network.getFactory().createNode(Id.create("5", Node.class), new Coord(10000.0D, 5000.0D));

        int i;
        for(i = 0; i < 6; ++i) {
            this.network.addNode(this.nodes[i]);
        }

        this.links[0] = this.network.getFactory().createLink(Id.create("0", Link.class), this.nodes[0], this.nodes[1]);
        this.links[1] = this.network.getFactory().createLink(Id.create("1", Link.class), this.nodes[1], this.nodes[0]);
        this.links[2] = this.network.getFactory().createLink(Id.create("2", Link.class), this.nodes[2], this.nodes[3]);
        this.links[3] = this.network.getFactory().createLink(Id.create("3", Link.class), this.nodes[3], this.nodes[2]);
        this.links[4] = this.network.getFactory().createLink(Id.create("4", Link.class), this.nodes[4], this.nodes[5]);
        this.links[5] = this.network.getFactory().createLink(Id.create("5", Link.class), this.nodes[5], this.nodes[4]);
        this.links[6] = this.network.getFactory().createLink(Id.create("6", Link.class), this.nodes[0], this.nodes[2]);
        this.links[7] = this.network.getFactory().createLink(Id.create("7", Link.class), this.nodes[2], this.nodes[0]);
        this.links[8] = this.network.getFactory().createLink(Id.create("8", Link.class), this.nodes[2], this.nodes[4]);
        this.links[9] = this.network.getFactory().createLink(Id.create("9", Link.class), this.nodes[4], this.nodes[2]);
        this.links[10] = this.network.getFactory().createLink(Id.create("10", Link.class), this.nodes[1], this.nodes[3]);
        this.links[11] = this.network.getFactory().createLink(Id.create("11", Link.class), this.nodes[3], this.nodes[1]);
        this.links[12] = this.network.getFactory().createLink(Id.create("12", Link.class), this.nodes[3], this.nodes[5]);
        this.links[13] = this.network.getFactory().createLink(Id.create("13", Link.class), this.nodes[5], this.nodes[3]);

        for(i = 0; i < 14; ++i) {
            this.links[i].setLength(5000.0D);
            this.links[i].setFreespeed(44.44D);
            this.links[i].setCapacity(2000.0D);
            this.links[i].setNumberOfLanes(1.0D);
            this.network.addLink(this.links[i]);
        }
    }

    protected void buildPopulation() {
        // agent 1 has all activities and legs on the inside
        Person agent001 = this.population.getFactory().createPerson(Id.createPersonId("agent_001"));
        Plan plan001 = PopulationUtils.createPlan();

        Activity activity0011 = PopulationUtils.createActivityFromCoord("Home", new Coord(0.0D, 0.0D));
        activity0011.setStartTime(0);
        activity0011.setEndTime(28800);
        plan001.addActivity(activity0011);

        Leg leg0011 = PopulationUtils.createLeg("car");
        leg0011.setDepartureTime(28800);
        leg0011.setTravelTime(300);
        List<Id<Link>> linkList0011 = Arrays.asList(this.links[0].getId());
        Route route0011 = new LinkNetworkRouteImpl(this.links[0].getId(), linkList0011, this.links[0].getId());
        leg0011.setRoute(route0011);
        plan001.addLeg(leg0011);

        Activity activity0012 = PopulationUtils.createActivityFromCoord("Work", new Coord(0.0D, 5000.0D));
        activity0012.setStartTime(29100);
        activity0012.setEndTime(61200);
        plan001.addActivity(activity0012);

        Leg leg0012 = PopulationUtils.createLeg("car");
        leg0012.setDepartureTime(61200);
        leg0012.setTravelTime(300);
        List<Id<Link>> linkList0012 = Arrays.asList(this.links[1].getId());
        Route route0012 = new LinkNetworkRouteImpl(this.links[1].getId(), linkList0012, this.links[1].getId());
        leg0012.setRoute(route0012);
        plan001.addLeg(leg0012);

        Activity activity0013 = PopulationUtils.createActivityFromCoord("Home", new Coord(0.0D, 0.0D));
        activity0013.setStartTime(61500);
        activity0013.setEndTime(86400);
        plan001.addActivity(activity0013);

        agent001.addPlan(plan001);

        this.persons[0] = agent001;

        this.population.addPerson(agent001);
        this.population.getPersonAttributes().putAttribute(agent001.getId().toString(), "subpopulation", "regular");

        // agent 2 starts on the inside and works on the outside
        Person agent002 = this.population.getFactory().createPerson(Id.createPersonId("agent_002"));
        Plan plan002 = PopulationUtils.createPlan();

        Activity activity0021 = PopulationUtils.createActivityFromCoord("Home", new Coord(0.0D, 0.0D));
        activity0021.setStartTime(0);
        activity0021.setEndTime(28800);
        plan001.addActivity(activity0021);

        Leg leg0021 = PopulationUtils.createLeg("car");
        leg0021.setDepartureTime(28800);
        leg0021.setTravelTime(300);
        List<Id<Link>> linkList0021 = Arrays.asList(this.links[6].getId());
        Route route0021 = new LinkNetworkRouteImpl(this.links[6].getId(), linkList0021, this.links[6].getId());
        leg0021.setRoute(route0021);
        plan002.addLeg(leg0021);

        Activity activity0022 = PopulationUtils.createActivityFromCoord("Work", new Coord(5000.0D, 0.0D));
        activity0022.setStartTime(29100);
        activity0022.setEndTime(61200);
        plan002.addActivity(activity0022);

        Leg leg0022 = PopulationUtils.createLeg("car");
        leg0022.setDepartureTime(61200);
        leg0022.setTravelTime(300);
        List<Id<Link>> linkList0022 = Arrays.asList(this.links[7].getId());
        Route route0022 = new LinkNetworkRouteImpl(this.links[7].getId(), linkList0022, this.links[7].getId());
        leg0022.setRoute(route0022);
        plan002.addLeg(leg0022);

        Activity activity0023 = PopulationUtils.createActivityFromCoord("Home", new Coord(0.0D, 0.0D));
        activity0023.setStartTime(61500);
        activity0023.setEndTime(86400);
        plan002.addActivity(activity0023);

        agent002.addPlan(plan002);

        this.persons[1] = agent002;

        this.population.addPerson(agent002);
        this.population.getPersonAttributes().putAttribute(agent002.getId().toString(), "subpopulation", "regular");

        // agent 3 has all actvities on the inside, but crosses the border twice
        Person agent003 = this.population.getFactory().createPerson(Id.createPersonId("agent_003"));
        Plan plan003 = PopulationUtils.createPlan();

        Activity activity0031 = PopulationUtils.createActivityFromCoord("Home", new Coord(0.0D, 0.0D));
        activity0031.setStartTime(0);
        activity0031.setEndTime(28800);
        plan003.addActivity(activity0031);

        Leg leg0031 = PopulationUtils.createLeg("car");
        leg0031.setDepartureTime(28800);
        leg0031.setTravelTime(900);
        List<Id<Link>> linkList0031 = Arrays.asList(this.links[6].getId(), this.links[2].getId(), this.links[11].getId());
        Route route0031 = new LinkNetworkRouteImpl(this.links[6].getId(), linkList0031, this.links[11].getId());
        leg0031.setRoute(route0031);
        plan003.addLeg(leg0031);

        Activity activity0032 = PopulationUtils.createActivityFromCoord("Work", new Coord(0.0D, 5000.0D));
        activity0032.setStartTime(29700);
        activity0032.setEndTime(61200);
        plan003.addActivity(activity0032);

        Leg leg0032 = PopulationUtils.createLeg("car");
        leg0032.setDepartureTime(61200);
        leg0032.setTravelTime(900);
        List<Id<Link>> linkList0032 = Arrays.asList(this.links[10].getId(), this.links[3].getId(), this.links[7].getId());
        Route route0032 = new LinkNetworkRouteImpl(this.links[10].getId(), linkList0032, this.links[7].getId());
        leg0032.setRoute(route0032);
        plan003.addLeg(leg0032);

        Activity activity0033 = PopulationUtils.createActivityFromCoord("Home", new Coord(0.0D, 0.0D));
        activity0033.setStartTime(62100);
        activity0033.setEndTime(86400);
        plan003.addActivity(activity0033);

        agent003.addPlan(plan003);

        this.persons[2] = agent003;

        this.population.addPerson(agent003);
        this.population.getPersonAttributes().putAttribute(agent003.getId().toString(), "subpopulation", "regular");

        // agent 4 has all activities and legs on the outside
        Person agent004 = this.population.getFactory().createPerson(Id.createPersonId("agent_004"));
        Plan plan004 = PopulationUtils.createPlan();

        Activity activity0041 = PopulationUtils.createActivityFromCoord("Home", new Coord(5000.0D, 0.0D));
        activity0041.setStartTime(0);
        activity0041.setEndTime(28800);
        plan004.addActivity(activity0041);

        Leg leg0041 = PopulationUtils.createLeg("car");
        leg0041.setDepartureTime(28800);
        leg0041.setTravelTime(300);
        List<Id<Link>> linkList0041 = Arrays.asList(this.links[2].getId());
        Route route0041 = new LinkNetworkRouteImpl(this.links[2].getId(), linkList0041, this.links[2].getId());
        leg0041.setRoute(route0041);
        plan004.addLeg(leg0041);

        Activity activity0042 = PopulationUtils.createActivityFromCoord("Work", new Coord(5000.0D, 5000.0D));
        activity0042.setStartTime(29100);
        activity0042.setEndTime(61200);
        plan004.addActivity(activity0042);

        Leg leg0042 = PopulationUtils.createLeg("car");
        leg0042.setDepartureTime(61200);
        leg0042.setTravelTime(300);
        List<Id<Link>> linkList0042 = Arrays.asList(this.links[3].getId());
        Route route0042 = new LinkNetworkRouteImpl(this.links[3].getId(), linkList0042, this.links[3].getId());
        leg0042.setRoute(route0042);
        plan004.addLeg(leg0042);

        Activity activity0043 = PopulationUtils.createActivityFromCoord("Home", new Coord(5000.0D, 0.0D));
        activity0043.setStartTime(61500);
        activity0043.setEndTime(86400);
        plan004.addActivity(activity0043);

        agent004.addPlan(plan004);

        this.persons[3] = agent004;

        this.population.addPerson(agent004);
        this.population.getPersonAttributes().putAttribute(agent004.getId().toString(), "subpopulation", "regular");

        // agent 5 has all actvities on the outside, but crosses the border twice
        Person agent005 = this.population.getFactory().createPerson(Id.createPersonId("agent_005"));
        Plan plan005 = PopulationUtils.createPlan();

        Activity activity0051 = PopulationUtils.createActivityFromCoord("Home", new Coord(5000.0D, 0.0D));
        activity0051.setStartTime(0);
        activity0051.setEndTime(28800);
        plan005.addActivity(activity0051);

        Leg leg0051 = PopulationUtils.createLeg("car");
        leg0051.setDepartureTime(28800);
        leg0051.setTravelTime(900);
        List<Id<Link>> linkList0051 = Arrays.asList(this.links[7].getId(), this.links[0].getId(), this.links[10].getId());
        Route route0051 = new LinkNetworkRouteImpl(this.links[7].getId(), linkList0051, this.links[10].getId());
        leg0051.setRoute(route0051);
        plan005.addLeg(leg0051);

        Activity activity0052 = PopulationUtils.createActivityFromCoord("Work", new Coord(5000.0D, 5000.0D));
        activity0052.setStartTime(29700);
        activity0052.setEndTime(61200);
        plan005.addActivity(activity0052);

        Leg leg0052 = PopulationUtils.createLeg("car");
        leg0052.setDepartureTime(61200);
        leg0052.setTravelTime(900);
        List<Id<Link>> linkList0052 = Arrays.asList(this.links[11].getId(), this.links[1].getId(), this.links[6].getId());
        Route route0052 = new LinkNetworkRouteImpl(this.links[11].getId(), linkList0052, this.links[6].getId());
        leg0052.setRoute(route0052);
        plan005.addLeg(leg0052);

        Activity activity0053 = PopulationUtils.createActivityFromCoord("Home", new Coord(5000.0D, 0.0D));
        activity0053.setStartTime(62100);
        activity0053.setEndTime(86400);
        plan005.addActivity(activity0053);

        agent005.addPlan(plan005);

        this.persons[4] = agent005;

        this.population.addPerson(agent005);
        this.population.getPersonAttributes().putAttribute(agent005.getId().toString(), "subpopulation", "regular");

        // agent 6 has all actvities on the outside, crosses the border twice, but not on his last leg
        Person agent006 = this.population.getFactory().createPerson(Id.createPersonId("agent_006"));
        Plan plan006 = PopulationUtils.createPlan();

        Activity activity0061 = PopulationUtils.createActivityFromCoord("Home", new Coord(5000.0D, 0.0D));
        activity0061.setStartTime(0);
        activity0061.setEndTime(28800);
        plan006.addActivity(activity0061);

        Leg leg0061 = PopulationUtils.createLeg("car");
        leg0061.setDepartureTime(28800);
        leg0061.setTravelTime(900);
        List<Id<Link>> linkList0061 = Arrays.asList(this.links[7].getId(), this.links[0].getId(), this.links[10].getId());
        Route route0061 = new LinkNetworkRouteImpl(this.links[7].getId(), linkList0051, this.links[10].getId());
        leg0061.setRoute(route0061);
        plan006.addLeg(leg0061);

        Activity activity0062 = PopulationUtils.createActivityFromCoord("Work", new Coord(5000.0D, 5000.0D));
        activity0062.setStartTime(29700);
        activity0062.setEndTime(61200);
        plan006.addActivity(activity0062);

        Leg leg0062 = PopulationUtils.createLeg("car");
        leg0062.setDepartureTime(61200);
        leg0062.setTravelTime(300);
        List<Id<Link>> linkList0062 = Arrays.asList(this.links[3].getId());
        Route route0062 = new LinkNetworkRouteImpl(this.links[3].getId(), linkList0062, this.links[3].getId());
        leg0062.setRoute(route0062);
        plan006.addLeg(leg0062);

        Activity activity0063 = PopulationUtils.createActivityFromCoord("Home", new Coord(5000.0D, 0.0D));
        activity0063.setStartTime(61500);
        activity0063.setEndTime(86400);
        plan006.addActivity(activity0063);

        agent006.addPlan(plan006);

        this.persons[5] = agent006;

        this.population.addPerson(agent006);
        this.population.getPersonAttributes().putAttribute(agent006.getId().toString(), "subpopulation", "regular");
    }
}
