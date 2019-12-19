package ch.sbb.matsim.accessibility;

import ch.sbb.matsim.analysis.skims.LeastCostPathTree;
import ch.sbb.matsim.analysis.skims.LeastCostPathTree.NodeData;
import ch.sbb.matsim.routing.pt.raptor.*;
import ch.sbb.matsim.routing.pt.raptor.RaptorRoute.RoutePart;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorCore.TravelInfo;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class Accessibility {

    private final static Logger log = Logger.getLogger(Accessibility.class);

    private final String networkFilename;
    private final String eventsFilename;
    private final String scheduleFilename;
    private final String transitNetworkFilename;
    private final Config config;
    private final Map<Coord, Double> attractions;
    private Predicate<Link> xy2linksPredicate;
    private boolean scenarioLoaded = false;
    private Network carNetwork;
    private TravelTime tt;
    private TravelDisutility td;
    private int threadCount = 4;
    private final double[] carAMDepTimes;
    private final double[] carPMDepTimes;
    private SwissRailRaptorData raptorData;
    private final double ptMinDepartureTime;
    private final double ptMaxDepartureTime;
    private final BiPredicate<TransitLine, TransitRoute> trainDetector;
    private final Zones zones;

    public Accessibility(String networkFilename, String eventsFilename, String scheduleFilename, String transitNetworkFilename,
                         Map<Coord, Double> attractions, double[] carAMDepTimes, double[] carPMDepTimes,
                         double ptMinDepartureTime, double ptMaxDepartureTime, BiPredicate<TransitLine, TransitRoute> trainDetector,
                         Zones zones) {
        this.networkFilename = networkFilename;
        this.eventsFilename = eventsFilename;
        this.scheduleFilename = scheduleFilename;
        this.transitNetworkFilename = transitNetworkFilename;
        this.config = ConfigUtils.createConfig();
        this.attractions = attractions;
        this.carAMDepTimes = eventsFilename == null ? new double[] { 8*3600 } : carAMDepTimes;
        this.carPMDepTimes = eventsFilename == null ? new double[0] : carPMDepTimes;
        this.ptMinDepartureTime = ptMinDepartureTime;
        this.ptMaxDepartureTime = ptMaxDepartureTime;
        this.trainDetector = trainDetector;
        this.zones = zones;
    }

    public void setXy2LinksPredicate(Predicate<Link> xy2linksPredicate) {
        this.xy2linksPredicate = xy2linksPredicate;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    private static boolean requiresCar(Modes[] modes) {
        for (Modes mode : modes) {
            if (mode.car) {
                return true;
            }
        }
        return false;
    }

    public void calculateAccessibility(List<Coord> coordinates, Modes[] modes, File csvOutputFile) {
        boolean requiresCar = requiresCar(modes);

        if (!this.scenarioLoaded) {
            loadScenario(requiresCar);
        }

        log.info("filter car-only network for assigning links to locations");
        Network xy2linksNetwork = extractXy2LinksNetwork(this.carNetwork);

        Map<Coord, ZoneData> zoneData = new HashMap<>();
        for (Map.Entry<Coord, Double> e : this.attractions.entrySet()) {
            double attraction = e.getValue();
            if (attraction > 0) {
                Coord coord = e.getKey();
                zoneData.put(coord, new ZoneData(
                        this.carNetwork.getNodes().get(NetworkUtils.getNearestLink(xy2linksNetwork, coord).getFromNode().getId()),
                        attraction
                ));
            }
        }

        ConcurrentLinkedQueue<Coord> accessibilityCoords = new ConcurrentLinkedQueue<>(coordinates);
        ConcurrentLinkedQueue<Tuple<Coord, double[]>> results = new ConcurrentLinkedQueue<>();
        try (BufferedWriter writer = IOUtils.getBufferedWriter(csvOutputFile.getAbsolutePath())) {
            writer.write("X,Y");
            for (Modes mode : modes) {
                writer.write(',');
                writer.write(mode.id);
            }
            writer.write(IOUtils.NATIVE_NEWLINE);

            Counter counter = new Counter("#", " / " + coordinates.size());
            Thread[] threads = new Thread[this.threadCount];
            for (int i = 0; i < this.threadCount; i++) {
                RowWorker worker = new RowWorker(
                        this.carNetwork, xy2linksNetwork, tt, td, this.carAMDepTimes, this.carPMDepTimes,
                        this.raptorData, RaptorUtils.createParameters(this.config), ptMinDepartureTime, ptMaxDepartureTime, trainDetector,
                        zoneData, modes, this.zones, counter, accessibilityCoords, results);
                threads[i] = new Thread(worker, "accessibility-" + i);
                threads[i].start();
            }
            for (int i = 0; i < threadCount; i++) {
                try {
                    threads[i].join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            counter.printCounter();
            while (!results.isEmpty()) {
                Tuple<Coord, double[]> result = results.poll();
                Coord coord = result.getFirst();
                double[] accessibilities = result.getSecond();
                writer.write(Double.toString(coord.getX()));
                writer.write(',');
                writer.write(Double.toString(coord.getY()));
                for (double acc : accessibilities) {
                    writer.write(',');
                    writer.write(Double.toString(acc));
                }
                writer.write(IOUtils.NATIVE_NEWLINE);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadScenario(boolean requiresCar) {
        Scenario scenario = ScenarioUtils.createScenario(this.config);
        log.info("loading network from " + networkFilename);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFilename);
        if (requiresCar) {
            if (eventsFilename != null) {
                log.info("extracting actual travel times from " + eventsFilename);
                TravelTimeCalculator ttc = TravelTimeCalculator.create(scenario.getNetwork(), config.travelTimeCalculator());
                EventsManager events = EventsUtils.createEventsManager();
                events.addHandler(ttc);
                new MatsimEventsReader(events).readFile(eventsFilename);
                this.tt = ttc.getLinkTravelTimes();
            } else {
                this.tt = new FreeSpeedTravelTime();
                log.info("No events specified. Travel Times will be calculated with free speed travel times.");
            }

            this.td = new OnlyTimeDependentTravelDisutility(tt);

        } else {
            log.info("not loading events, as no car-accessibility needs to be calculated.");
        }
        log.info("extracting car-only network"); // this is used in any case, not only when car is needed.
        this.carNetwork = NetworkUtils.createNetwork();
        new TransportModeNetworkFilter(scenario.getNetwork()).filter(this.carNetwork, Collections.singleton(TransportMode.car));

        log.info("loading schedule from " + this.scheduleFilename);
        Scenario ptScenario;
        if (transitNetworkFilename.equals(networkFilename)) {
            ptScenario = scenario;
        } else {
            ptScenario = ScenarioUtils.createScenario(this.config);
            new MatsimNetworkReader(ptScenario.getNetwork()).readFile(transitNetworkFilename);
        }
        new TransitScheduleReader(ptScenario).readFile(this.scheduleFilename);
        log.info("prepare PT Matrix calculation");
        RaptorStaticConfig raptorConfig = RaptorUtils.createStaticConfig(this.config);
        raptorConfig.setOptimization(RaptorStaticConfig.RaptorOptimization.OneToAllRouting);
        this.raptorData = SwissRailRaptorData.create(ptScenario.getTransitSchedule(), raptorConfig, ptScenario.getNetwork());

        this.scenarioLoaded = true;
    }

    private Network extractXy2LinksNetwork(Network network) {
        Predicate<Link> predicate = this.xy2linksPredicate;
        if (predicate == null) {
            predicate = l -> true;
        }
        Network xy2lNetwork = NetworkUtils.createNetwork();
        NetworkFactory nf = xy2lNetwork.getFactory();
        for (Link link : network.getLinks().values()) {
            if (predicate.test(link)) {
                // okay, we need that link
                Node fromNode = link.getFromNode();
                Node xy2lFromNode = xy2lNetwork.getNodes().get(fromNode.getId());
                if (xy2lFromNode == null) {
                    xy2lFromNode = nf.createNode(fromNode.getId(), fromNode.getCoord());
                    xy2lNetwork.addNode(xy2lFromNode);
                }
                Node toNode = link.getToNode();
                Node xy2lToNode = xy2lNetwork.getNodes().get(toNode.getId());
                if (xy2lToNode == null) {
                    xy2lToNode = nf.createNode(toNode.getId(), toNode.getCoord());
                    xy2lNetwork.addNode(xy2lToNode);
                }
                Link xy2lLink = nf.createLink(link.getId(), xy2lFromNode, xy2lToNode);
                xy2lLink.setAllowedModes(link.getAllowedModes());
                xy2lLink.setCapacity(link.getCapacity());
                xy2lLink.setFreespeed(link.getFreespeed());
                xy2lLink.setLength(link.getLength());
                xy2lLink.setNumberOfLanes(link.getNumberOfLanes());
                xy2lNetwork.addLink(xy2lLink);
            }
        }
        return xy2lNetwork;
    }

    private static class ZoneData {
        final Node node;
        final double attraction;

        public ZoneData(Node node, double attraction) {
            this.node = node;
            this.attraction = attraction;
        }
    }

    /**
     * Simple implementation for TravelTime and TravelDisutility that assumes a fixed speed on all links,
     * resulting in the shortest (and not the fastest) path to be found.
     */
    private static class FixedSpeedTravelTimeAndDisutility implements TravelTime, TravelDisutility {

        private final double speed;

        public FixedSpeedTravelTimeAndDisutility(double speed) {
            this.speed = speed;
        }

        @Override
        public double getLinkTravelDisutility(Link link, double v, Person person, Vehicle vehicle) {
            return link.getLength() / this.speed;
        }

        @Override
        public double getLinkMinimumTravelDisutility(Link link) {
            return link.getLength() / this.speed;
        }

        @Override
        public double getLinkTravelTime(Link link, double v, Person person, Vehicle vehicle) {
            return link.getLength() / this.speed;
        }
    }

    private static class RowWorker implements Runnable {

        private final boolean requiresCar;
        private final Network carNetwork;
        private final Network xy2linksNetwork;
        private final LeastCostPathTree shortestLcpTree;
        private final LeastCostPathTree[] amLcpTree;
        private final LeastCostPathTree[] pmLcpTree;
        private final double[] carAMDepTimes;
        private final double[] carPMDepTimes;
        private final SwissRailRaptor raptor;
        private final RaptorParameters parameters;
        private final double ptMinDepartureTime;
        private final double ptMaxDepartureTime;
        private final double stepSize = 120;
        private final BiPredicate<TransitLine, TransitRoute> trainDetector;
        private final Map<Coord, ZoneData> zoneData;
        private final Modes[] modes;
        private final Counter counter;
        private final Queue<Coord> coordinates;
        private final Queue<Tuple<Coord, double[]>> results;
        private Zones zones;

        RowWorker(Network carNetwork, Network xy2linksNetwork, TravelTime tt, TravelDisutility td, double[] carAMDepTimes, double[] carPMDepTimes,
                  SwissRailRaptorData raptorData, RaptorParameters parameters, double ptMinDepartureTime, double ptMaxDepartureTime, BiPredicate<TransitLine, TransitRoute> trainDetector,
                  Map<Coord, ZoneData> zoneData, Modes[] modes, Zones zones, Counter counter, Queue<Coord> coordinates, Queue<Tuple<Coord, double[]>> results) {
            this.carNetwork = carNetwork;
            this.xy2linksNetwork = xy2linksNetwork;
            this.carAMDepTimes = carAMDepTimes;
            this.carPMDepTimes = carPMDepTimes;

            FixedSpeedTravelTimeAndDisutility shortestTTD = new FixedSpeedTravelTimeAndDisutility(1.0);
            this.shortestLcpTree = new LeastCostPathTree(shortestTTD, shortestTTD);

            this.amLcpTree = new LeastCostPathTree[carAMDepTimes.length];
            for (int i = 0; i < carAMDepTimes.length; i++) {
                this.amLcpTree[i] = new LeastCostPathTree(tt, td);
            }
            this.pmLcpTree = new LeastCostPathTree[carPMDepTimes.length];
            for (int i = 0; i < carPMDepTimes.length; i++) {
                this.pmLcpTree[i] = new LeastCostPathTree(tt, td);
            }
            this.raptor = new SwissRailRaptor(raptorData, null, null, null);
            this.parameters = parameters;
            this.ptMinDepartureTime = ptMinDepartureTime;
            this.ptMaxDepartureTime = ptMaxDepartureTime;
            this.trainDetector = trainDetector;

            this.zoneData = zoneData;
            this.requiresCar = requiresCar(modes);
            this.modes = modes;
            this.zones = zones;
            this.counter = counter;
            this.coordinates = coordinates;
            this.results = results;
        }

        public void run() {
            while (true) {
                Coord fromCoord = this.coordinates.poll();
                if (fromCoord == null) {
                    return;
                }

                this.counter.incCounter();
                double[] accessibilities = calcForCoord(fromCoord);
                this.results.add(new Tuple<>(fromCoord, accessibilities));
            }
        }

        private double[] calcForCoord(Coord fromCoord) {
            Node nearestNode = this.carNetwork.getNodes().get(NetworkUtils.getNearestLink(this.xy2linksNetwork, fromCoord).getToNode().getId());

            // CAR
            if (this.requiresCar) {
                for (int i = 0; i < this.amLcpTree.length; i++) {
                    this.amLcpTree[i].calculate(this.carNetwork, nearestNode, this.carAMDepTimes[i]);
                }

                for (int i = 0; i < this.pmLcpTree.length; i++) {
                    this.pmLcpTree[i].calculate(this.carNetwork, nearestNode, this.carPMDepTimes[i]);
                }
            }

            // WALK, BIKE
            {
                this.shortestLcpTree.calculate(this.carNetwork, nearestNode, 8 * 3600);
            }

            // PT

            double walkSpeed = this.parameters.getBeelineWalkSpeed();

            Collection<TransitStopFacility> fromStops = findStopCandidates(fromCoord, this.raptor, this.parameters);
            Map<Id<TransitStopFacility>, Double> accessTimes = new HashMap<>();
            for (TransitStopFacility stop : fromStops) {
                double distance = CoordUtils.calcEuclideanDistance(fromCoord, stop.getCoord());
                double accessTime = distance / walkSpeed;
                accessTimes.put(stop.getId(), accessTime);
            }

            List<Map<Id<TransitStopFacility>, TravelInfo>> trees = new ArrayList<>();

            for (double time = this.ptMinDepartureTime; time < this.ptMaxDepartureTime; time += this.stepSize) {
                Map<Id<TransitStopFacility>, TravelInfo> tree = this.raptor.calcTree(fromStops, time, this.parameters);
                trees.add(tree);
            }

            Zone fromZone = this.zones.findZone(fromCoord.getX(), fromCoord.getY());
            double carAccessTime = fromZone == null ? 0 : ((Number) fromZone.getAttribute("ACCCAR")).doubleValue(); // in seconds

            // CALCULATION

            double[] accessibility = new double[this.modes.length];
            for (Entry<Coord, ZoneData> e : this.zoneData.entrySet()) {
                Coord toCoord = e.getKey();
                ZoneData zData = e.getValue();
                Node toNode = zData.node;
                double attraction = zData.attraction;

                // CAR

                double distCar = 0;
                double amTravelTime = 0;
                double pmTravelTime = 0;
                boolean hasCar = false;
                if (requiresCar) {
                    int amCount = 0;
                    for (int i = 0; i < this.amLcpTree.length; i++) {
                        NodeData data = this.amLcpTree[i].getTree().get(toNode.getId());
                        if (data != null) {
                            amTravelTime += data.getTime() - this.carAMDepTimes[i];
                            distCar += data.getDistance();
                            amCount++;
                        }
                    }
                    amTravelTime /= Math.max(1, amCount);

                    int pmCount = 0;
                    for (int i = 0; i < this.pmLcpTree.length; i++) {
                        NodeData data = this.pmLcpTree[i].getTree().get(toNode.getId());
                        if (data != null) {
                            pmTravelTime += data.getTime() - this.carPMDepTimes[i];
                            distCar += data.getDistance();
                            pmCount++;
                        }
                    }
                    hasCar = (amCount + pmCount) > 0;
                    pmTravelTime /= Math.max(1, pmCount);
                    distCar /= Math.max(1, amCount + pmCount);

                    distCar /= 1000.0; // we use kilometers in the following formulas
                }

                double ttCar = Math.max(amTravelTime, pmTravelTime) / 60; // we need minutes in the following formulas
                double distCar0015 = Math.min(distCar, 15);
                double distCar1550 = Math.max(0, Math.min(distCar - 15, 35)); // 35 = 50 - 15, upperBound - lowerBound
                double distCar5099 = Math.max(0, Math.min(distCar - 50, 50)); // 50 = 100 - 50
                double distCar100x = Math.max(0, distCar - 100);

                // WALK, BIKE

                boolean hasShortestDistance = true;
                double distShortest = 0;
                {
                    NodeData data = this.shortestLcpTree.getTree().get(toNode.getId());
                    if (data != null) {
                        distShortest = data.getDistance();
                    } else {
                        hasShortestDistance = false;
                    }
                }
                distShortest /= 1000.0; // we use kilometers in the following formulas

                // PT
                Collection<TransitStopFacility> toStops = findStopCandidates(toCoord, this.raptor, this.parameters);
                Map<Id<TransitStopFacility>, Double> egressTimes = new HashMap<>();
                for (TransitStopFacility stop : toStops) {
                    double distance = CoordUtils.calcEuclideanDistance(stop.getCoord(), toCoord);
                    double egressTime = distance / walkSpeed;
                    egressTimes.put(stop.getId(), egressTime);
                }

                List<ODConnection> connections = buildODConnections(trees, egressTimes);
                boolean hasPT = !connections.isEmpty();

                double ttTrain = 0;
                double ttBus = 0;
                double ptAccessTime = 0;
                double ptEgressTime = 0;
                double ptFrequency = 0;
                double ptTransfers = 0;
                double ptDistance = 0;

                if (hasPT) {
                    connections = sortAndFilterConnections(connections);

                    double avgAdaptionTime = calcAverageAdaptionTime(connections, ptMinDepartureTime, ptMaxDepartureTime);

                    Map<ODConnection, Double> connectionShares = calcConnectionShares(connections, ptMinDepartureTime, ptMaxDepartureTime);

                    float accessTime = 0;
                    float egressTime = 0;
                    float transferCount = 0;
                    float travelTime = 0;

                    double totalInVehTime = 0;
                    double trainInVehTime = 0;

                    for (Entry<ODConnection, Double> cs : connectionShares.entrySet()) {
                        ODConnection connection = cs.getKey();
                        double share = cs.getValue();

                        accessTime += share * accessTimes.get(connection.travelInfo.departureStop).floatValue();
                        egressTime += share * (float) connection.egressTime;
                        transferCount += share * (float) connection.transferCount;
                        travelTime += share * (float) connection.totalTravelTime();

                        double connTotalDistance = 0;
                        double connTotalInVehTime = 0;
                        double connTrainInVehTime = 0;

                        RaptorRoute route = connection.travelInfo.getRaptorRoute();
                        for (RoutePart part : route.getParts()) {
                            if (part.line != null) {
                                // it's a non-transfer part, an actual pt stage

                                boolean isTrain = this.trainDetector.test(part.line, part.route);
                                double inVehicleTime = part.arrivalTime - part.boardingTime;

                                connTotalDistance += part.distance;
                                connTotalInVehTime += inVehicleTime;

                                if (isTrain) {
                                    connTrainInVehTime += inVehicleTime;
                                }
                            }
                        }
                        ptDistance += share * connTotalDistance;

                        totalInVehTime += share * connTotalInVehTime;
                        trainInVehTime += share * connTrainInVehTime;
                    }

                    float trainShareByTravelTime = totalInVehTime > 0 ? (float) (trainInVehTime / totalInVehTime) : 0;

                    ttTrain = travelTime / 60 * trainShareByTravelTime; // in minutes
                    ttBus = travelTime / 60 - ttTrain; // in minutes
                    ptAccessTime = accessTime / 60; // in minutes
                    ptEgressTime = egressTime / 60; // in minutes
                    ptFrequency = 900 / avgAdaptionTime;
                    ptTransfers = transferCount;

                    ptDistance /= 1000.0; // we use kilometers in the following formulas
                }
                double distPt0015 = Math.min(ptDistance, 15);
                double distPt1550 = Math.max(0, Math.min(ptDistance - 15, 35)); // 35 = 50 - 15, upperBound - lowerBound
                double distPt5099 = Math.max(0, Math.min(ptDistance - 50, 50)); // 50 = 100 - 50
                double distPt100x = Math.max(0, ptDistance - 100);

                // ACCESSIBILITY

//                U(bike)= -0.25 + (-0.150)*dist_car/0.21667
//
//                U(car)  = -0.40 + (-0.053)*TT_car + (-0.040)*dist_car_0015 + (-0.040)*dist_car_1550 + 0.015*dist_car_5099 + 0.010*dist_car_100x + (-0.047)*(FROM[ACCCAR]+TO[ACCCAR])/60 + (-0.135)*TO[PCOST]*2
//
//                U(pt)  = +0.75 + (-0.042)*TT_bus + (-0.0378)*TT_train + (-0.015)*dist_car_0015 + (-0.015)*dist_car_1550 + 0.005*dist_car_5099 + 0.025*dist_car_100x + (-0.050)*(pt_accTime+pt_egrTime) + (-0.014)*(60/pt_freq) + (-0.227)*transfers
//
//                U(walk)= +2.30 + (-0.100)*dist_car/0.078336

                Zone toZone = this.zones.findZone(toCoord.getX(), toCoord.getY());
                double carEgressTime = toZone == null ? 0 : ((Number) toZone.getAttribute("ACCCAR")).doubleValue(); // in seconds
                double carParkingCost = toZone == null ? 0 : ((Number) toZone.getAttribute("PCOST")).doubleValue();

                for (int m = 0; m < this.modes.length; m++) {
                    Modes modes = this.modes[m];

                    double uBike = (modes.bike && hasShortestDistance) ? (-0.25 + (-0.150) * distShortest / 0.21667) : modes.missingModeUtility;
                    double uCar = (modes.car && hasCar) ? (-0.40 + (-0.053) * ttCar + (-0.040) * distCar0015 + (-0.040) * distCar1550 + 0.015 * distCar5099 + 0.010 * distCar100x + (-0.047) * (carAccessTime + carEgressTime) / 60 + (-0.135) * carParkingCost * 2) : modes.missingModeUtility;
                    double uPt = (modes.pt && hasPT) ? (+0.75 + (-0.042)*ttBus + (-0.0378)*ttTrain + (-0.015)*distPt0015 + (-0.015)*distPt1550 + 0.005*distPt5099 + 0.025*distPt100x + (-0.050)*(ptAccessTime+ptEgressTime) + (-0.014)*(60/ptFrequency) + (-0.227)*ptTransfers) : modes.missingModeUtility;
                    double uWalk = (modes.walk && hasShortestDistance) ? (+2.30 + (-0.100) * distShortest / 0.078336) : modes.missingModeUtility;

                    double theta = modes.theta;
                    double destinationUtility = Math.exp(uCar / theta) + Math.exp(uPt / theta) + Math.exp(uWalk / theta) + Math.exp(uBike / theta);

                    accessibility[m] += attraction * Math.exp(theta * Math.log(destinationUtility));
                }
            }
            return accessibility;
        }
    }

    private static Collection<TransitStopFacility> findStopCandidates(Coord coord, SwissRailRaptor raptor, RaptorParameters parameters) {
        Collection<TransitStopFacility> stops = raptor.getUnderlyingData().findNearbyStops(coord.getX(), coord.getY(), parameters.getSearchRadius());
        if (stops.isEmpty()) {
            TransitStopFacility nearest = raptor.getUnderlyingData().findNearestStop(coord.getX(), coord.getY());
            double nearestStopDistance = CoordUtils.calcEuclideanDistance(coord, nearest.getCoord());
            stops = raptor.getUnderlyingData().findNearbyStops(coord.getX(), coord.getY(), nearestStopDistance + parameters.getExtensionRadius());
        }
        return stops;
    }


    private static List<ODConnection> buildODConnections(List<Map<Id<TransitStopFacility>, SwissRailRaptorCore.TravelInfo>> trees, Map<Id<TransitStopFacility>, Double> egressTimes) {
        List<ODConnection> connections = new ArrayList<>();

        for (Map<Id<TransitStopFacility>, SwissRailRaptorCore.TravelInfo> tree : trees) {
            for (Map.Entry<Id<TransitStopFacility>, Double> egressEntry : egressTimes.entrySet()) {
                Id<TransitStopFacility> egressStopId = egressEntry.getKey();
                Double egressTime = egressEntry.getValue();
                SwissRailRaptorCore.TravelInfo info = tree.get(egressStopId);
                if (info != null && !info.isWalkOnly()) {
                    ODConnection connection = new ODConnection(info.ptDepartureTime, info.ptTravelTime, info.accessTime, egressTime, info.transferCount, info);
                    connections.add(connection);
                }
            }
        }

        return connections;
    }

    static double calcAverageAdaptionTime(List<ODConnection> connections, double minDepartureTime, double maxDepartureTime) {
        double prevDepartureTime = Double.NaN;
        double nextDepartureTime = Double.NaN;
        ODConnection prevConnection = null;
        ODConnection nextConnection = null;

        Iterator<ODConnection> connectionIterator = connections.iterator();
        if (connectionIterator.hasNext()) {
            nextConnection = connectionIterator.next();
            nextDepartureTime = nextConnection.departureTime - nextConnection.accessTime;
        }

        double sum = 0.0;
        int count = 0;
        for (double time = minDepartureTime; time < maxDepartureTime; time += 60.0) {
            double adaptionTime;

            while (time >= nextDepartureTime) {
                prevDepartureTime = nextDepartureTime;
                prevConnection = nextConnection;
                if (connectionIterator.hasNext()) {
                    nextConnection = connectionIterator.next();
                    nextDepartureTime = nextConnection.departureTime - nextConnection.accessTime;
                } else {
                    nextDepartureTime = Double.NaN;
                    nextConnection = null;
                }
            }

            if (prevConnection == null) {
                adaptionTime = nextDepartureTime - time;
            } else if (nextConnection == null) {
                adaptionTime = time - prevDepartureTime;
            } else {
                double prevAdaptionTime = time - prevDepartureTime;
                double nextAdaptionTime = nextDepartureTime - time;
                double prevTotalTime = prevConnection.travelTime + prevAdaptionTime;
                double nextTotalTime = nextConnection.travelTime + nextAdaptionTime;

                if (prevTotalTime < nextTotalTime) {
                    adaptionTime = prevAdaptionTime;
                } else {
                    adaptionTime = nextAdaptionTime;
                }
            }

            sum += adaptionTime;
            count++;
        }
        return sum / count;
    }

    /** calculates the share each connection covers based on minimizing (travelTime + adaptionTime)
     */
    static Map<ODConnection, Double> calcConnectionShares(List<ODConnection> connections, double minDepartureTime, double maxDepartureTime) {
        double prevDepartureTime = Double.NaN;
        double nextDepartureTime = Double.NaN;

        ODConnection prevConnection = null;
        ODConnection nextConnection = null;

        Map<ODConnection, Double> shares = new HashMap<>();

        Iterator<ODConnection> connectionIterator = connections.iterator();
        if (connectionIterator.hasNext()) {
            nextConnection = connectionIterator.next();
            nextDepartureTime = nextConnection.departureTime - nextConnection.accessTime;
        }

        for (double time = minDepartureTime; time < maxDepartureTime; time += 60.0) {
            if (time >= nextDepartureTime) {
                prevDepartureTime = nextDepartureTime;
                prevConnection = nextConnection;
                if (connectionIterator.hasNext()) {
                    nextConnection = connectionIterator.next();
                    nextDepartureTime = nextConnection.departureTime - nextConnection.accessTime;
                } else {
                    nextDepartureTime = Double.NaN;
                    nextConnection = null;
                }
            }

            if (prevConnection == null) {
                shares.compute(nextConnection, (c, oldVal) -> (oldVal == null ? 1 : (oldVal+1)));
            } else if (nextConnection == null) {
                shares.compute(prevConnection, (c, oldVal) -> (oldVal == null ? 1 : (oldVal+1)));
            } else {
                double prevAdaptionTime = time - prevDepartureTime;
                double nextAdaptionTime = nextDepartureTime - time;
                double prevTotalTime = prevConnection.travelTime + prevAdaptionTime;
                double nextTotalTime = nextConnection.travelTime + nextAdaptionTime;

                if (prevTotalTime < nextTotalTime) {
                    shares.compute(prevConnection, (c, oldVal) -> (oldVal == null ? 1 : (oldVal+1)));
                } else {
                    shares.compute(nextConnection, (c, oldVal) -> (oldVal == null ? 1 : (oldVal+1)));
                }
            }
        }

        double sum = (maxDepartureTime - minDepartureTime) / 60;
        for (Map.Entry<ODConnection, Double> e : shares.entrySet()) {
            ODConnection c = e.getKey();
            shares.put(c, e.getValue() / sum);
        }

        return shares;
    }


    static List<ODConnection> sortAndFilterConnections(List<ODConnection> connections) {
        connections.sort((c1, c2) -> Double.compare((c1.departureTime - c1.accessTime), (c2.departureTime - c2.accessTime)));

        // step forward through all connections and figure out which can be ignore because the earlier one is better
        List<ODConnection> filteredConnections1 = new ArrayList<>();
        ODConnection earlierConnection = null;
        for (ODConnection connection : connections) {
            if (earlierConnection == null) {
                filteredConnections1.add(connection);
                earlierConnection = connection;
            } else {
                double timeDiff = (connection.departureTime - connection.accessTime) - (earlierConnection.departureTime - earlierConnection.accessTime);
                if (earlierConnection.totalTravelTime() + timeDiff > connection.totalTravelTime()) {
                    // connection is better to earlierConnection, use it
                    filteredConnections1.add(connection);
                    earlierConnection = connection;
                }
            }
        }

        // now step backwards through the remaining connections and figure out which can be ignored because the later one is better
        List<ODConnection> filteredConnections = new ArrayList<>();
        ODConnection laterConnection = null;

        for (int i = filteredConnections1.size() - 1; i >= 0; i--) {
            ODConnection connection = filteredConnections1.get(i);
            if (laterConnection == null) {
                filteredConnections.add(connection);
                laterConnection = connection;
            } else {
                double timeDiff = (laterConnection.departureTime - laterConnection.accessTime) - (connection.departureTime - connection.accessTime);
                if (laterConnection.totalTravelTime() + timeDiff > connection.totalTravelTime()) {
                    // connection is better to laterConnection, use it
                    filteredConnections.add(connection);
                    laterConnection = connection;
                }
            }
        }

        Collections.reverse(filteredConnections);
        // now the filtered connections are in ascending departure time order
        return filteredConnections;
    }

    static class ODConnection {
        final double departureTime;
        final double travelTime;
        final double accessTime;
        final double egressTime;
        final double transferCount;
        final SwissRailRaptorCore.TravelInfo travelInfo;

        ODConnection(double departureTime, double travelTime, double accessTime, double egressTime, double transferCount, SwissRailRaptorCore.TravelInfo info) {
            this.departureTime = departureTime;
            this.travelTime = travelTime;
            this.accessTime = accessTime;
            this.egressTime = egressTime;
            this.transferCount = transferCount;
            this.travelInfo = info;
        }

        double totalTravelTime() {
            return this.accessTime + this.travelTime + this.egressTime;
        }
    }

    public static class Modes {
        private String id;
        private boolean car = true;
        private boolean pt = true;
        private boolean walk = true;
        private boolean bike = true;
        private double missingModeUtility = -9999;
        private double theta = 1;

        public Modes(String id) {
            this.id = id;
        }

        public Modes(String id, boolean car, boolean pt, boolean walk, boolean bike) {
            this.id = id;
            this.car = car;
            this.pt = pt;
            this.walk = walk;
            this.bike = bike;
        }
    }

}
