package ch.sbb.matsim.projects.basel.pedestrians;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.csv.CSVReader;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.population.*;
import org.matsim.contrib.common.util.WeightedRandomSelection;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class RouteBaselSBBPedestrianLegs {


    private final Scenario scenario;
    private final Map<Id<Link>, StopWithAccessPoints> stopsWithAccessPoints = new HashMap<>();
    private final LeastCostPathCalculator lcp;
    private final double walkSpeed = 4.2 / 3.6;
    private final Map<Id<Link>, int[]> linkUse = new HashMap<>();
    private final Map<String, int[]> entranceUse = new HashMap<>();
    private final Map<Id<Link>, String> entranceLinks = new HashMap<>();


    public RouteBaselSBBPedestrianLegs(Scenario scenario) {
        this.scenario = scenario;

        prepareNetwork(scenario.getNetwork());
        var disutility = new FreespeedTravelTimeAndDisutility(0.0, 0, -0.01);
        lcp = new DijkstraFactory().createPathCalculator(scenario.getNetwork(), disutility, disutility);

    }

    public static void main(String[] args) {
        var networkFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220412_Basel_2050\\pedestrians_basel_sbb\\osm\\basel-sbb-net.xml.gz";
        var populationFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220412_Basel_2050\\sim\\v310\\pedsim\\v310-basel-sbb-legs.xml.gz";
        String scenarioFolder = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220412_Basel_2050\\sim\\v310\\pedsim\\";
        var stopsWithAccessPointsFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220412_Basel_2050\\pedestrians_basel_sbb\\v301\\results\\west\\Verteilung Eingänge-west.csv";
        var entranceLinksFile = scenarioFolder + "Eingänge-Querschnitte.csv";
        var outputNetwork = scenarioFolder + "output_net.xml.gz";
        var outputPlans = scenarioFolder + "routed_plans.xml.gz";

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);
        new PopulationReader(scenario).readFile(populationFile);
        RouteBaselSBBPedestrianLegs routeBaselSBBPedestrianLegs = new RouteBaselSBBPedestrianLegs(scenario);
        routeBaselSBBPedestrianLegs.readStopsAndAccessPoints(stopsWithAccessPointsFile);
        routeBaselSBBPedestrianLegs.parseEntranceLinks(entranceLinksFile);
        routeBaselSBBPedestrianLegs.adjustCoordinatesInPlans();
        routeBaselSBBPedestrianLegs.routeLegs();
        routeBaselSBBPedestrianLegs.writeNetwork(outputNetwork);
        new PopulationWriter(scenario.getPopulation()).write(outputPlans);
    }

    private void prepareNetwork(Network network) {
        for (Link l : network.getLinks().values()) {
            l.setCapacity(10000);
            l.setAllowedModes(Set.of(SBBModes.WALK_MAIN_MAINMODE));
            l.setFreespeed(walkSpeed);
        }
    }

    private void parseEntranceLinks(String entranceLinksFile) {

        try (BufferedReader br = IOUtils.getBufferedReader(entranceLinksFile)) {
            String line = br.readLine();
            while (line != null) {
                var s = line.split(";");
                var name = s[0];
                System.out.println(name);
                if (!name.equals("")) {
                    for (int i = 1; i < s.length; i++) {
                        if (s[i].equals("")) continue;
                        Id<Link> linkId = Id.createLinkId(s[i]);
                        if (!scenario.getNetwork().getLinks().containsKey(linkId)) {
                            System.out.println("Link missing " + linkId);
                        }
                        entranceLinks.put(linkId, name);
                    }
                }
                line = br.readLine();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void readStopsAndAccessPoints(String stopsWithAccessPointsFile) {
        try (CSVReader reader = new CSVReader(stopsWithAccessPointsFile, ";")) {
            var line = reader.readLine();
            while (line != null) {
                Id<Link> linkId = Id.createLinkId(line.get("link_id"));
                String name = line.get("Name");
                double weight = Double.parseDouble(line.get("Gewicht"));
                double x = Double.parseDouble(line.get("x"));
                double y = Double.parseDouble(line.get("y"));
                Coord pointLocation = new Coord(x, y);
                this.stopsWithAccessPoints.computeIfAbsent(linkId, linkId1 -> new StopWithAccessPoints(linkId1, name)).add(pointLocation, weight, name);

                line = reader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(stopsWithAccessPoints);
    }

    private void routeLegs() {

        scenario.getPopulation().getPersons().values().stream().map(person -> person.getSelectedPlan()).forEach(
                plan -> {
                    Activity fromAct = (Activity) plan.getPlanElements().get(0);
                    Activity toAct = (Activity) plan.getPlanElements().get(2);
                    //if (toAct.getType().equals("pt interaction") && fromAct.getType().equals("pt interaction")) {
                    Leg leg = (Leg) plan.getPlanElements().get(1);
                    routeLeg(fromAct.getCoord(), toAct.getCoord(), leg);
                    //}
                }
        );
    }

    private void routeLeg(Coord fromCoord, Coord toCoord, Leg walkLeg) {
        var bestPath = lcp.calcLeastCostPath(NetworkUtils.getNearestNode(scenario.getNetwork(), fromCoord), NetworkUtils.getNearestNode(scenario.getNetwork(), toCoord), 0.0, null, null);
        Link startLink = bestPath.getFromNode().getInLinks().values().stream().findAny().get();
        Link endLink = bestPath.links.size() > 0 ? bestPath.links.get(bestPath.links.size() - 1) : startLink;
        Route route = RouteUtils.createLinkNetworkRouteImpl(startLink.getId(), bestPath.links.stream().map(l -> l.getId()).collect(Collectors.toList()), endLink.getId());
        route.setTravelTime(bestPath.travelTime);
        route.setDistance(bestPath.links.stream().mapToDouble(l -> l.getLength()).sum());
        walkLeg.setRoute(route);
        walkLeg.setTravelTime(route.getTravelTime().seconds());
        int hour = ((int) walkLeg.getDepartureTime().seconds() / 3600);
        if (hour > 23) {
            hour = hour - 24;
        }
        int finalHour = hour;
        bestPath.links.forEach(link -> linkUse.computeIfAbsent(link.getId(), l -> new int[24])[finalHour]++);

    }

    private void adjustCoordinatesInPlans() {
        Set<Id<Link>> lostLinkIds = new HashSet<>();
        for (Person p : scenario.getPopulation().getPersons().values()) {
            Plan plan = p.getSelectedPlan();
            for (var activity : TripStructureUtils.getActivities(plan, TripStructureUtils.StageActivityHandling.StagesAsNormalActivities)) {
                Id<Link> activityLinkId = activity.getLinkId();
                if (activityLinkId != null) {
                    var stopWithAccessPoints = stopsWithAccessPoints.get(activityLinkId);
                    if (stopWithAccessPoints != null) {
                        Coord newCoord = stopWithAccessPoints.select();
                        activity.setCoord(newCoord);
                        //activity.setType(stopWithAccessPoints.getName(newCoord));
                    }
                    if (activity.getCoord() == null) {
                        lostLinkIds.add(activity.getLinkId());
                    }
                    activity.setLinkId(null);
                }
            }
        }
        System.out.println(lostLinkIds);
    }

    private void writeNetwork(String outputNetFile) {
        entranceLinks.forEach((linkId, name) -> {
                    var entrance = entranceUse.computeIfAbsent(name, n -> new int[24]);
                    int[] linkUseAtEntrance = linkUse.get(linkId);
                    if (linkUseAtEntrance == null) {
                        System.out.println(linkId + " is null entrance");
                    } else {
                        for (int i = 0; i < 24; i++) {
                            entrance[i] += linkUseAtEntrance[i];

                        }
                    }
                }
        );
        linkUse.forEach((l, v) -> scenario.getNetwork().getLinks().get(l).getAttributes().putAttribute("PED_VOLUME", Arrays.stream(v).sum()));
        new NetworkWriter(scenario.getNetwork()).write(outputNetFile);
        entranceUse.forEach((s, i) -> System.out.println(s + ";" + Arrays.stream(i).mapToObj(a -> Integer.toString(a)).collect(Collectors.joining(";"))));

    }

    private static class StopWithAccessPoints {
        final Id<Link> stopFacilityLinkId;
        final String name;
        private final WeightedRandomSelection<Coord> weightedAccessPoints = new WeightedRandomSelection<>(MatsimRandom.getRandom());
        private final Map<Coord, String> coord2Names = new HashMap<>();

        private StopWithAccessPoints(Id<Link> stopFacilityLinkId, String name) {
            this.stopFacilityLinkId = stopFacilityLinkId;
            this.name = name;
        }

        public Coord select() {
            return weightedAccessPoints.select();
        }

        public String getName(Coord coord) {
            return coord2Names.get(coord);
        }

        public void add(Coord coord, double weight, String name) {
            coord2Names.put(coord, name);
            weightedAccessPoints.add(coord, weight);

        }


        @Override
        public String toString() {
            return "StopWithAccessPoints{" +
                    "stopFacilityLinkId=" + stopFacilityLinkId +
                    ", name='" + name + '\'' +
                    ", weightedAccessPoints=" + weightedAccessPoints +
                    '}';
        }
    }


}
