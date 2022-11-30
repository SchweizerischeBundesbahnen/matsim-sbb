package ch.sbb.matsim.projects.langsamverkehr;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.csv.CSVWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class CreateEntrancesAndExits {

    private final Population population;
    String entrancesAndExitsFile = null;
    Map<String, List<Coord>> eAELocationsMap = new HashMap<>();
    Map<TransitStopFacility, Station> stations = new HashMap<>();
    Map<Node, TransitStopFacility> nodesToStations = new HashMap<>();
    Map<Id<Link>, TransitStopFacility> stopFacilityLinkId = new HashMap<>();
    String outputPlans = "";
    String outputNetwork = "";
    String outputCSV = "";
    Network network;
    LeastCostPathCalculator lcp;
    int additionalLinks = 0;
    int additionalNodes = 0;
    int count = 0;
    TransitSchedule transitSchedule;

    Coord bernCenter = new Coord(2600074.409087829, 1199814.4754796433);
    double radius = 10000;

    CreateEntrancesAndExits(Network network, Population population, TransitSchedule transitSchedule, String outputPlans, String outputNetwork, String outputCSV) {
        this.network = network;
        this.population = population;
        this.transitSchedule = transitSchedule;
        this.outputPlans = outputPlans;
        this.outputNetwork = outputNetwork;
        this.outputCSV = outputCSV;
    }

    void readEntrancesAndExits() {
        if (entrancesAndExitsFile == null) {
            create4EntrancesAndExits();
        } else {
            try (BufferedReader bufferedReader = new BufferedReader(new FileReader(entrancesAndExitsFile))) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    String[] lineValues = line.split(";");
                    if (eAELocationsMap.containsKey(lineValues[0])) {
                        List<Coord> coords = eAELocationsMap.get(lineValues[0]);
                        coords.add(new Coord(Double.parseDouble(lineValues[1]), Double.parseDouble(lineValues[2])));
                    } else {
                        List<Coord> coords = new ArrayList<>();
                        coords.add(new Coord(Double.parseDouble(lineValues[1]), Double.parseDouble(lineValues[2])));
                        eAELocationsMap.put(lineValues[0], coords);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        for (Link l : network.getLinks().values()) {
            l.setCapacity(10000);
            l.setAllowedModes(Set.of(SBBModes.WALK_MAIN_MAINMODE, SBBModes.WALK_FOR_ANALYSIS));
            l.setFreespeed(4.2 / 3.6);
        }
        new NetworkWriter(network).write(outputNetwork);
        FreespeedTravelTimeAndDisutility disutility = new FreespeedTravelTimeAndDisutility(0.0, 0, -0.01);
        lcp = new DijkstraFactory().createPathCalculator(network, disutility, disutility);
        routePlans(outputPlans);
        writeEntraceAndExitUsage();
    }

    private void writeEntraceAndExitUsage() {
        String[] columns = {"id","x","y","usage","all","percent"};
        try (CSVWriter csvWriter = new CSVWriter("", columns, outputCSV)) {
            for (Station station : stations.values())
                for (Node node : station.nodesList) {
                    csvWriter.set("id", node.getId().toString());
                    csvWriter.set("x", Double.toString(node.getCoord().getX()));
                    csvWriter.set("y", Double.toString(node.getCoord().getY()));
                    csvWriter.set("usage", Integer.toString(station.usageMap.get(node)));
                    csvWriter.set("all", Integer.toString(station.count));
                    if (station.count == 0) {
                        csvWriter.set("percent", "0");
                    } else {
                        csvWriter.set("percent", Double.toString(station.usageMap.get(node)/(double)station.count));
                    }
                    csvWriter.writeRow();
                }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void routePlans(String outputPlans) {
        for (TransitStopFacility stopFacility : transitSchedule.getFacilities().values()) {
            if (stopFacility.getAttributes().getAttribute("03_Stop_Code") != null) {
                stopFacilityLinkId.put(stopFacility.getLinkId(), stopFacility);
            }
        }
        /*
        List<Person> notInBern = new ArrayList<>();
        for (Person p : population.getPersons().values()) {
            Activity start = (Activity) p.getSelectedPlan().getPlanElements().get(0);
            Activity end = (Activity) p.getSelectedPlan().getPlanElements().get(2);
            if (!((CoordUtils.calcEuclideanDistance(start.getCoord(), bernCenter) < radius) && (CoordUtils.calcEuclideanDistance(end.getCoord(), bernCenter) < radius))) {
                notInBern.add(p);
            }
        }
        for (Person person : notInBern) {
            population.removePerson(person.getId());
        }
        */
        List<Person> removePerson = new ArrayList<>();
        for (Person p : population.getPersons().values()) {
            Plan plan = p.getSelectedPlan();

            if (plan.getPlanElements().size() == 3) {
                Activity sA = (Activity) plan.getPlanElements().get(0);
                Activity eA = (Activity) plan.getPlanElements().get(2);
                Leg leg = (Leg) plan.getPlanElements().get(1);
                Node fN = NetworkUtils.getNearestNode(network, sA.getCoord());
                Node tN = NetworkUtils.getNearestNode(network, eA.getCoord());
                Path path = null;
                if (stopFacilityLinkId.containsKey(sA.getLinkId())) {
                    for (Node node : stations.get(stopFacilityLinkId.get(sA.getLinkId())).nodesList) {
                        Path tmpPath = lcp.calcLeastCostPath(node, tN, 0, null, null);
                        if (path == null) {
                            path = tmpPath;
                        } else if (path.travelCost > tmpPath.travelCost) {
                            path = tmpPath;
                        }
                    }
                }
                if (stopFacilityLinkId.containsKey(eA.getLinkId())) {
                    for (Node node : stations.get(stopFacilityLinkId.get(eA.getLinkId())).nodesList) {
                        Path tmpPath = lcp.calcLeastCostPath(fN, node, 0, null, null);
                        if (path == null) {
                            path = tmpPath;
                        } else if (path.travelCost > tmpPath.travelCost) {
                            path = tmpPath;
                        }
                    }
                } else {
                    path = lcp.calcLeastCostPath(fN, tN, 0, null, null);
                }
                if (path == null) {
                    removePerson.add(p);
                    continue;
                }
                sA.setCoord(path.getFromNode().getCoord());
                sA.setFacilityId(null);
                sA.setLinkId(null);
                eA.setLinkId(null);
                if (eA.getCoord() == null) {
                    eA.setCoord(path.getToNode().getCoord());
                }
                leg.setDepartureTime(sA.getEndTime().seconds());
                Link startLink = path.getFromNode().getInLinks().values().stream().findAny().get();
                Link endLink = path.links.size() > 0 ? path.links.get(path.links.size() - 1) : startLink;
                Route route = RouteUtils.createLinkNetworkRouteImpl(startLink.getId(), path.links.stream().map(l -> l.getId()).collect(Collectors.toList()), endLink.getId());
                leg.setTravelTime(path.travelTime);
                route.setDistance(path.links.stream().mapToDouble(l -> l.getLength()).sum());
                leg.setRoute(route);
                analyseEtranceAndExitsUsage(path);
            }
        }
        System.out.println("removed Persons: " + removePerson.size());
        for (Person person : removePerson) {
            population.removePerson(person.getId());
        }
        new PopulationWriter(population).write(outputPlans);
    }

    private void analyseEtranceAndExitsUsage(Path path) {
        for (Node node : path.nodes) {
            if (nodesToStations.containsKey(node)) {
                TransitStopFacility transitStopFacility = nodesToStations.get(node);
                Station station = stations.get(transitStopFacility);
                station.useSation(node);
            }
        }
    }

    private void create4EntrancesAndExits() {
        for (TransitStopFacility transitStopFacility : transitSchedule.getFacilities().values()) {
            if (transitStopFacility.getAttributes().getAttribute("03_Stop_Code") == null) {
                continue;
            }
            /*
            if (CoordUtils.calcEuclideanDistance(transitStopFacility.getCoord(), bernCenter) > (radius * 1.1)) {
                continue;
            }
             */
            Node n0 = create1EntrancesAndExits(transitStopFacility, 0, 20);
            Node n1 = create1EntrancesAndExits(transitStopFacility, 0, -20);
            Node n2 = create1EntrancesAndExits(transitStopFacility, 20, 0);
            Node n3 = create1EntrancesAndExits(transitStopFacility, -20, 0);

            nodesToStations.put(n0, transitStopFacility);
            nodesToStations.put(n1, transitStopFacility);
            nodesToStations.put(n2, transitStopFacility);
            nodesToStations.put(n3, transitStopFacility);

            Link l1 = network.getFactory().createLink(Id.createLinkId("l_" + transitStopFacility.getId().toString() + "_" + additionalLinks++), n0, n1);
            Link l1g = network.getFactory().createLink(Id.createLinkId("l_" + transitStopFacility.getId().toString() + "_" + additionalLinks++), n1, n0);
            Link l2 = network.getFactory().createLink(Id.createLinkId("l_" + transitStopFacility.getId().toString() + "_" + additionalLinks++), n0, n2);
            Link l2g = network.getFactory().createLink(Id.createLinkId("l_" + transitStopFacility.getId().toString() + "_" + additionalLinks++), n2, n0);
            Link l3 = network.getFactory().createLink(Id.createLinkId("l_" + transitStopFacility.getId().toString() + "_" + additionalLinks++), n0, n3);
            Link l3g = network.getFactory().createLink(Id.createLinkId("l_" + transitStopFacility.getId().toString() + "_" + additionalLinks++), n3, n0);
            Link l4 = network.getFactory().createLink(Id.createLinkId("l_" + transitStopFacility.getId().toString() + "_" + additionalLinks++), n1, n2);
            Link l4g = network.getFactory().createLink(Id.createLinkId("l_" + transitStopFacility.getId().toString() + "_" + additionalLinks++), n2, n1);
            Link l5 = network.getFactory().createLink(Id.createLinkId("l_" + transitStopFacility.getId().toString() + "_" + additionalLinks++), n1, n3);
            Link l5g = network.getFactory().createLink(Id.createLinkId("l_" + transitStopFacility.getId().toString() + "_" + additionalLinks++), n3, n1);
            Link l6 = network.getFactory().createLink(Id.createLinkId("l_" + transitStopFacility.getId().toString() + "_" + additionalLinks++), n2, n3);
            Link l6g = network.getFactory().createLink(Id.createLinkId("l_" + transitStopFacility.getId().toString() + "_" + additionalLinks++), n3, n2);

            List<Node> nodes = new ArrayList<>();
            List<Link> links = new ArrayList<>();

            network.addNode(n0);
            nodes.add(n0);
            network.addNode(n1);
            nodes.add(n1);
            network.addNode(n2);
            nodes.add(n2);
            network.addNode(n3);
            nodes.add(n3);

            network.addLink(l1);
            links.add(l1);
            network.addLink(l1g);
            links.add(l1g);
            network.addLink(l2);
            links.add(l2);
            network.addLink(l2g);
            links.add(l2g);
            network.addLink(l3);
            links.add(l3);
            network.addLink(l3g);
            links.add(l3g);
            network.addLink(l4);
            links.add(l4);
            network.addLink(l4g);
            links.add(l4g);
            network.addLink(l5);
            links.add(l5);
            network.addLink(l5g);
            links.add(l5g);
            network.addLink(l6);
            links.add(l6);
            network.addLink(l6g);
            links.add(l6g);

            additionalNodes = 0;
            additionalLinks = 0;

            connectToNetwork(nodes);

            stations.put(transitStopFacility, new Station(nodes));
        }
    }

    private void connectToNetwork(List<Node> nodes) {
        for (Node node : nodes) {
            int distance = 25;
            List<Node> nearestNodes = new ArrayList<>();
            List<Node> removeNode = new ArrayList<>();
            while (nearestNodes.size() == 0) {
                nearestNodes = (List<Node>) NetworkUtils.getNearestNodes(network, node.getCoord(), distance);
                for (Node tmpNode : nearestNodes) {
                    if (tmpNode.getId().toString().contains("pt")) {
                        removeNode.add(tmpNode);
                    }
                }
                nearestNodes.removeAll(removeNode);
                removeNode.clear();
                distance += 25;
            }
            for (Node nearesNode : nearestNodes) {
                Link l1 = network.getFactory().createLink(Id.createLinkId("vl_pt_" + count++), node, nearesNode);
                Link l2 = network.getFactory().createLink(Id.createLinkId("vl_" + count++), nearesNode, node);
                network.addLink(l1);
                network.addLink(l2);
            }
        }
    }


    private Node create1EntrancesAndExits(TransitStopFacility transitStopFacility, double x, double y) {
        Coord coord = new Coord(transitStopFacility.getCoord().getX() + x, transitStopFacility.getCoord().getY() + y);
        Node node = network.getFactory().createNode(Id.createNodeId("n_pt_" + transitStopFacility.getId().toString() + "_" + additionalNodes++), coord);
        return node;
    }

    public static void main(String[] args) throws IOException {
        rokasWalkRoute();
    }

    static private void rokasWalkRoute() throws IOException {
        URL url = new URL("https://journey-maps.api.sbb.ch:443");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.addRequestProperty("api_key", "c51ca728f50a17a5439670dd3faf7ead");
        System.out.println(con.getResponseCode());
    }

}
