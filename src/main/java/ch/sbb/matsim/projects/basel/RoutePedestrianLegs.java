package ch.sbb.matsim.projects.basel;

import ch.sbb.matsim.config.variables.SBBModes;
import org.apache.commons.lang3.mutable.MutableInt;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.*;
import org.matsim.contrib.common.util.WeightedRandomSelection;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;

import java.util.*;
import java.util.stream.Collectors;

public class RoutePedestrianLegs {

    private final Population population;
    private final Network network;
    private final double walkSpeed;
    private final List<Coord> stationEntrances;
    private final Map<Coord, Id<Node>> stationEntranceNodes = new HashMap<>();
    private final Map<Coord, MutableInt> stationEntranceDistribution = new HashMap<>();
    private final LeastCostPathCalculator lcp;
    private final Map<Id<Link>, MutableInt> linkUse = new HashMap<>();
    int additionalLinks = 0;
    int additionalNodes = 0;

    public RoutePedestrianLegs(Scenario scenario, List<Coord> stationEntrances, double walkSpeed) {
        this.population = scenario.getPopulation();
        this.network = scenario.getNetwork();
        this.walkSpeed = walkSpeed;
        this.stationEntrances = stationEntrances;

        prepareNetwork();
        var disutility = new FreespeedTravelTimeAndDisutility(0.0, 0, -0.01);
        lcp = new DijkstraFactory().createPathCalculator(network, disutility, disutility);

    }

    public static void main(String[] args) {
        String path = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20230201_Biel_2040\\sim\\pedsim\\";
        String networkFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20230201_Biel_2040\\sim\\pedsim\\biel.xml.gz";
        String inputPlansFile = path + "biel-legs.xml.gz";
        String outputPlansFile = path + "routed-plans.xml.gz";
        String outputNetFile = path + "biel-net_with_station_exits.xml.gz";
        double walkSpeed = 4.2 / 3.6;
        Coord murtenstr = new Coord(2585399.862850578, 1220063.0303093693);
        Coord zentral = new Coord(2585150.7279656795, 1220229.8680819331);
        Coord sued = new Coord(2585094.6465606242, 1220103.5361372966);
        Coord aldi = new Coord(2585056.697442964, 1220269.2606236746);

        List<Coord> stationEntrances = List.of(murtenstr, aldi, zentral, sued);
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);
        new PopulationReader(scenario).readFile(inputPlansFile);
        RoutePedestrianLegs routePedestrianLegs = new RoutePedestrianLegs(scenario, stationEntrances, walkSpeed);
        routePedestrianLegs.routePlans(outputPlansFile);
        routePedestrianLegs.writeNetwork(outputNetFile);

    }

    private void prepareNetwork() {
        for (Coord coord : stationEntrances) {
            createConnectionNodesAndLinks(coord);
        }

        for (Link l : network.getLinks().values()) {
            l.setCapacity(10000);
            l.setAllowedModes(Set.of(SBBModes.WALK_MAIN_MAINMODE));
            l.setFreespeed(walkSpeed);
        }
    }

    private void createConnectionNodesAndLinks(Coord coord) {
        int distance = 10;
        Collection<Node> nearestNodes = NetworkUtils.getNearestNodes(network, coord, distance);
        while (nearestNodes.size() < 2) {
            distance += 10;
            nearestNodes = NetworkUtils.getNearestNodes(network, coord, distance);
        }
        Node accessNode = network.getFactory().createNode(Id.createNodeId("nn_" + additionalNodes++), new Coord(coord.getX() + 4.0, coord.getY() + 4.0));
        network.addNode(accessNode);
        Node stairNode = network.getFactory().createNode(Id.createNodeId("nn_" + additionalNodes++), coord);
        network.addNode(stairNode);
        Link stairLink1 = network.getFactory().createLink(Id.createLinkId("nl_" + additionalLinks++), accessNode, stairNode);
        Link stairLink2 = network.getFactory().createLink(Id.createLinkId("nl_" + additionalLinks++), stairNode, accessNode);
        stairLink1.setLength(10.0);
        stairLink2.setLength(10.0);
        network.addLink(stairLink1);
        network.addLink(stairLink2);

        for (Node nearestNode : nearestNodes) {
            Link accessLink = network.getFactory().createLink(Id.createLinkId("nl_" + additionalLinks++), nearestNode, accessNode);
            accessLink.setLength(CoordUtils.calcEuclideanDistance(coord, nearestNode.getCoord()) + 1.0);
            Link egressLink = network.getFactory().createLink(Id.createLinkId("nl_" + additionalLinks++), accessNode, nearestNode);
            egressLink.setLength(CoordUtils.calcEuclideanDistance(coord, nearestNode.getCoord()) + 1.0);
            network.addLink(accessLink);
            network.addLink(egressLink);
        }


        this.stationEntranceNodes.put(coord, stairNode.getId());
        this.stationEntranceDistribution.put(coord, new MutableInt());
    }

    private void writeNetwork(String outputNetFile) {
        linkUse.forEach((l, v) -> network.getLinks().get(l).getAttributes().putAttribute("PED_VOLUME", v.intValue()));
        new NetworkWriter(network).write(outputNetFile);
    }

    private void routePlans(String outputPlansFile) {
        for (Person p : population.getPersons().values()) {
            Plan plan = p.getSelectedPlan();
            if (plan.getPlanElements().size() == 3) {
                Activity fromAct = (Activity) plan.getPlanElements().get(0);

                Activity toAct = (Activity) plan.getPlanElements().get(2);
                boolean egress = fromAct.getType().equals(ExtractLegsAtStation.STATION_ACT);
                Coord toCoord = egress ? toAct.getCoord() : fromAct.getCoord();
                LeastCostPathCalculator.Path bestPath = selectEntranceAndCalcPath(egress, toCoord);
                fromAct.setCoord(bestPath.getFromNode().getCoord());
                fromAct.setFacilityId(null);
                fromAct.setLinkId(null);
                toAct.setLinkId(null);
                if (toAct.getCoord() == null) {
                    toAct.setCoord(bestPath.getToNode().getCoord());
                }
                Leg walkLeg = (Leg) plan.getPlanElements().get(1);
                walkLeg.setDepartureTime(fromAct.getEndTime().seconds());
                Link startLink = bestPath.getFromNode().getInLinks().values().stream().findAny().get();

                Link endLink = bestPath.links.size() > 0 ? bestPath.links.get(bestPath.links.size() - 1) : startLink;
                Route route = RouteUtils.createLinkNetworkRouteImpl(startLink.getId(), bestPath.links.stream().map(l -> l.getId()).collect(Collectors.toList()), endLink.getId());
                route.setTravelTime(bestPath.travelTime);
                route.setDistance(bestPath.links.stream().mapToDouble(l -> l.getLength()).sum());
                walkLeg.setRoute(route);
                walkLeg.setTravelTime(route.getTravelTime().seconds());
                bestPath.links.forEach(link -> linkUse.computeIfAbsent(link.getId(), l -> new MutableInt()).increment());

            }
        }
        new PopulationWriter(population).write(outputPlansFile);
        stationEntranceDistribution.forEach((coord, mutableInt) -> System.out.println(coord + " : " + mutableInt.toString()));
    }

    private LeastCostPathCalculator.Path selectEntranceAndCalcPath(boolean egress, Coord toCoord) {
        Node toNode = NetworkUtils.getNearestNode(network, toCoord);
        List<Candidate> candidatePaths = new ArrayList<>();
        for (Map.Entry<Coord, Id<Node>> e : stationEntranceNodes.entrySet()) {
            LeastCostPathCalculator.Path path = egress ?
                    lcp.calcLeastCostPath(network.getNodes().get(e.getValue()), toNode, 0.0, null, null) :
                    lcp.calcLeastCostPath(toNode, network.getNodes().get(e.getValue()), 0.0, null, null);
            double distance = path.links.stream().mapToDouble(l -> l.getLength()).sum();
            Candidate c = new Candidate(path, distance, e.getKey());
            candidatePaths.add(c);
        }
//        useShortestWay(candidatePaths);
        calcProbabilities(candidatePaths);
        WeightedRandomSelection<Candidate> weightedRandomSelection = new WeightedRandomSelection(MatsimRandom.getRandom());
        candidatePaths
                .stream().filter(candidate -> candidate.probability > 0.001)
                .forEach(candidate -> weightedRandomSelection.add(candidate, candidate.probability));

        Candidate selection = weightedRandomSelection.select();
        this.stationEntranceDistribution.get(selection.entranceCoord).increment();
        return selection.path;

    }

    private void calcProbabilities(List<Candidate> candidatePaths) {
        double e = 0.006;
        double sum = candidatePaths.stream().mapToDouble(c -> Math.exp(-e * c.distance)).sum();
        candidatePaths.forEach(candidate -> candidate.probability = Math.exp(-e * candidate.distance) / sum);
    }

    private void useShortestWay(List<Candidate> candidatePaths) {
        double shortest = Double.MAX_VALUE;
        Candidate shortestCandidate = null;
        for (Candidate c : candidatePaths) {
            if (c.distance < shortest) {
                shortest = c.distance;
                shortestCandidate = c;
            }
        }
        shortestCandidate.probability = 1.0;
    }

    private static class Candidate {
        LeastCostPathCalculator.Path path;
        double distance;
        Coord entranceCoord;
        double probability = 0.0;

        public Candidate(LeastCostPathCalculator.Path path, double distance, Coord entranceCoord) {
            this.path = path;
            this.distance = distance;
            this.entranceCoord = entranceCoord;
        }
    }

}
