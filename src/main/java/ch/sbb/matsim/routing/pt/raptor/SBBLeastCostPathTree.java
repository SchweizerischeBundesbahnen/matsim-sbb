package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.analysis.skims.LeastCostPathTree;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.costcalculators.RandomizingTimeDistanceTravelDisutilityFactory;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.core.utils.collections.PseudoRemovePriorityQueue;
import org.matsim.core.utils.collections.RouterPriorityQueue;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleUtils;

/**
 * Calculates a least-cost-path tree using Dijkstra's algorithm  for calculating a shortest-path
 * tree, given a node as root of the tree.
 *
 *
 * THIS IS A COPY OF ch.sbb.matsim.analysis.skims.LeastCostPathTree, which itself is a copy of
 * org.matsim.utils.leastcostpathtree.LeastCostPathTree,
 * modified such that not only travel time and travel cost, but also distance is calculated.
 * (modification by mrieser/SBB).
 * This copy is modified to support a stopCriterion as well as backwards-routing, the code was cleaned up to
 * allow multithreaded usage of an instance, and some minor performance optimizations were applied.
 *
 *
 * @author balmermi, mrieser
 * @author mrieser / SBB
 */
public final class SBBLeastCostPathTree {

    private final TravelTime ttFunction;
    private final TravelDisutility tcFunction;

    private final static Vehicle VEHICLE = VehicleUtils.getFactory().createVehicle(Id.create("theVehicle", Vehicle.class), VehicleUtils.getDefaultVehicleType());
    private final static Person PERSON = PopulationUtils.getFactory().createPerson(Id.create("thePerson", Person.class));

    public SBBLeastCostPathTree(TravelTime tt, TravelDisutility tc) {
        this.ttFunction = tt;
        this.tcFunction = tc;
    }

    public Map<Id<Node>, NodeData> calculate(final Network network, final Node origin, final double time) {
        return calculate(network, origin, time, (n, d, t) -> false);
    }

    public Map<Id<Node>, NodeData> calculate(final Network network, final Node origin, final double departureTime, final StopCriterion stopCriterion) {
        Map<Id<Node>, NodeData> nodeData = new HashMap<>((int) (network.getNodes().size() * 1.1));
        NodeData d = new NodeData();
        d.time = departureTime;
        d.cost = 0;
        nodeData.put(origin.getId(), d);

        RouterPriorityQueue<Node> pendingNodes = new PseudoRemovePriorityQueue<>(500);
        relaxNode(origin, pendingNodes, d, nodeData);
        while (!pendingNodes.isEmpty()) {
            Node n = pendingNodes.poll();
            NodeData nData = nodeData.get(n.getId());
            if (stopCriterion.stop(n, nData, departureTime)) {
                break;
            }
            relaxNode(n, pendingNodes, nData, nodeData);
        }
        return nodeData;
    }


    public Map<Id<Node>, NodeData> calculateBackwards(final Network network, final Node origin, final double time) {
        return calculateBackwards(network, origin, time, (n, d, t) -> false);
    }

    public Map<Id<Node>, NodeData> calculateBackwards(final Network network, final Node destination, final double departureTime, final StopCriterion stopCriterion) {
        Map<Id<Node>, NodeData> nodeData = new HashMap<>((int) (network.getNodes().size() * 1.1));
        NodeData d = new NodeData();
        d.time = departureTime;
        d.cost = 0;
        nodeData.put(destination.getId(), d);

        RouterPriorityQueue<Node> pendingNodes = new PseudoRemovePriorityQueue<>(500);
        relaxNodeBackwards(destination, pendingNodes, d, nodeData);
        while (!pendingNodes.isEmpty()) {
            Node n = pendingNodes.poll();
            NodeData nData = nodeData.get(n.getId());
            if (stopCriterion.stop(n, nData, departureTime)) {
                break;
            }
            relaxNodeBackwards(n, pendingNodes, nData, nodeData);
        }
        return nodeData;
    }

    private void relaxNode(final Node n, RouterPriorityQueue<Node> pendingNodes, final NodeData nData, Map<Id<Node>, NodeData> nodeData) {
        double currTime = nData.getTime();
        double currCost = nData.getCost();
        double currDistance = nData.distance;
        for (Link l : n.getOutLinks().values()) {
            Node nn = l.getToNode();
            NodeData nnData = nodeData.computeIfAbsent(nn.getId(), id -> new NodeData());
            double visitCost = currCost + tcFunction.getLinkTravelDisutility(l, currTime, PERSON, VEHICLE);
            double visitTime = currTime + ttFunction.getLinkTravelTime(l, currTime, PERSON, VEHICLE);
            double distance = currDistance + l.getLength();

            if (visitCost < nnData.getCost()) {
                nnData.visit(l, visitCost, visitTime, distance);
                pendingNodes.decreaseKey(nn, visitCost);
            }
        }
    }

    private void relaxNodeBackwards(final Node n, RouterPriorityQueue<Node> pendingNodes, final NodeData nData, Map<Id<Node>, NodeData> nodeData) {
        double currTime = nData.getTime();
        double currCost = nData.getCost();
        double currDistance = nData.distance;
        for (Link l : n.getInLinks().values()) {
            Node nn = l.getFromNode();
            NodeData nnData = nodeData.computeIfAbsent(nn.getId(), id -> new NodeData());
            double queryTime = currTime;
            while (queryTime < 0) {
                queryTime += 86400; // shift time by 24 hours to prevent exceptions when querying link travel times and link travel disutility
            }
            double visitCost = currCost + tcFunction.getLinkTravelDisutility(l, queryTime, PERSON, VEHICLE);
            double visitTime = currTime - ttFunction.getLinkTravelTime(l, queryTime, PERSON, VEHICLE);
            double distance = currDistance + l.getLength();

            if (visitCost < nnData.getCost()) {
                nnData.visit(l, visitCost, visitTime, distance);
                pendingNodes.decreaseKey(nn, visitCost);
            }
        }
    }

    public static class NodeData {
        private Link link = null;
        private double cost = Double.MAX_VALUE;
        private double time = 0;
        private double distance = 0;

        /*package*/ void visit(final Link link, final double cost1, final double time1, double distance) {
            this.link = link;
            this.cost = cost1;
            this.time = time1;
            this.distance = distance;
        }

        public double getCost() {
            return this.cost;
        }

        public double getTime() {
            return this.time;
        }

        public double getDistance() {
            return this.distance;
        }

        public Link getLink() {
            return this.link;
        }
    }

    public interface StopCriterion {
        boolean stop(Node node, NodeData data, double departureTime);
    }

    public static final class TravelTimeStopCriterion implements StopCriterion {

        private final double limit;

        public TravelTimeStopCriterion(double limit) {
            this.limit = limit;
        }

        @Override
        public boolean stop(Node node, NodeData data, double departureTime) {
            return Math.abs(data.time - departureTime) >= this.limit; // use Math.abs() so it also works in backwards search
        }
    }

    public static final class TravelDistanceStopCriterion implements StopCriterion {

        private final double limit;

        public TravelDistanceStopCriterion(double limit) {
            this.limit = limit;
        }

        @Override
        public boolean stop(Node node, NodeData data, double departureTime) {
            return data.distance >= this.limit;
        }
    }

    public static void main(String[] args) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Network network = scenario.getNetwork();
        new MatsimNetworkReader(scenario.getNetwork()).readFile("../../input/network.xml");

        TravelTimeCalculator.Builder ttcb = new TravelTimeCalculator.Builder(network);
        ttcb.configure(scenario.getConfig().travelTimeCalculator());
        ttcb.setTimeslice(60);
        ttcb.setMaxTime(30 * 3600);
        TravelTimeCalculator ttc = ttcb.build();
        LeastCostPathTree st = new LeastCostPathTree(ttc.getLinkTravelTimes(),
                new RandomizingTimeDistanceTravelDisutilityFactory(TransportMode.car, scenario.getConfig()).createTravelDisutility(ttc.getLinkTravelTimes()));
        Node origin = network.getNodes().get(Id.create(1, Node.class));
        st.calculate(network, origin, 8*3600);
        Map<Id<Node>, LeastCostPathTree.NodeData> tree = st.getTree();
        for (Entry<Id<Node>, LeastCostPathTree.NodeData> e : tree.entrySet()) {
            Id<Node> id = e.getKey();
            LeastCostPathTree.NodeData d = e.getValue();
            if (d.getPrevNodeId() != null) {
                System.out.println(id + "\t" + d.getTime() + "\t" + d.getCost() + "\t" + d.getPrevNodeId());
            } else {
                System.out.println(id + "\t" + d.getTime() + "\t" + d.getCost() + "\t" + "0");
            }
        }
    }
}