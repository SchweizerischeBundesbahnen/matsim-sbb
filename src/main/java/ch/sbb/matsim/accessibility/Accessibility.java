package ch.sbb.matsim.accessibility;

import ch.sbb.matsim.analysis.skims.LeastCostPathTree;
import ch.sbb.matsim.routing.pt.raptor.RaptorParameters;
import ch.sbb.matsim.routing.pt.raptor.RaptorParametersForPerson;
import ch.sbb.matsim.routing.pt.raptor.RaptorRoute;
import ch.sbb.matsim.routing.pt.raptor.RaptorRouteSelector;
import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig;
import ch.sbb.matsim.routing.pt.raptor.RaptorStopFinder;
import ch.sbb.matsim.routing.pt.raptor.RaptorUtils;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorCore;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
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

    public void calculateAccessibility(List<Coord> coordinates, Modes[] modes, File csvOutputFile) {
        if (!this.scenarioLoaded) {
            loadScenario();
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

    private void loadScenario() {
        Scenario scenario = ScenarioUtils.createScenario(this.config);
        log.info("loading network from " + networkFilename);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFilename);

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

        log.info("extracting car-only network");
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

    private static class RowWorker implements Runnable {

        private final Network carNetwork;
        private final Network xy2linksNetwork;
        private final TravelTime tt;
        private final TravelDisutility td;
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
            this.tt = tt;
            this.td = td;
            this.carAMDepTimes = carAMDepTimes;
            this.carPMDepTimes = carPMDepTimes;
            this.amLcpTree = new LeastCostPathTree[carAMDepTimes.length];
            for (int i = 0; i < carAMDepTimes.length; i++) {
                this.amLcpTree[i] = new LeastCostPathTree(tt, td);
            }
            this.pmLcpTree = new LeastCostPathTree[carPMDepTimes.length];
            for (int i = 0; i < carPMDepTimes.length; i++) {
                this.pmLcpTree[i] = new LeastCostPathTree(tt, td);
            }
            this.raptor = new SwissRailRaptor(raptorData, (RaptorParametersForPerson)null, (RaptorRouteSelector)null, (RaptorStopFinder)null);
            this.parameters = parameters;
            this.ptMinDepartureTime = ptMinDepartureTime;
            this.ptMaxDepartureTime = ptMaxDepartureTime;
            this.trainDetector = trainDetector;

            this.zoneData = zoneData;
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
            // CAR
            Node node = this.carNetwork.getNodes().get(NetworkUtils.getNearestLink(this.xy2linksNetwork, fromCoord).getToNode().getId());

            for (int i = 0; i < this.amLcpTree.length; i++) {
                this.amLcpTree[i].calculate(this.carNetwork, node, this.carAMDepTimes[i]);
            }

            for (int i = 0; i < this.pmLcpTree.length; i++) {
                this.pmLcpTree[i].calculate(this.carNetwork, node, this.carPMDepTimes[i]);
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

            List<Map<Id<TransitStopFacility>, SwissRailRaptorCore.TravelInfo>> trees = new ArrayList<>();

            for (double time = this.ptMinDepartureTime; time < this.ptMaxDepartureTime; time += this.stepSize) {
                Map<Id<TransitStopFacility>, SwissRailRaptorCore.TravelInfo> tree = this.raptor.calcTree(fromStops, time, this.parameters);
                trees.add(tree);
            }

            Zone fromZone = this.zones.findZone(fromCoord.getX(), fromCoord.getY());
            double carAccessTime = fromZone == null ? 0 : ((Number) fromZone.getAttribute("ACCCAR")).doubleValue(); // in seconds

            // CALCULATION

            double[] accessibility = new double[this.modes.length];
            for (Map.Entry<Coord, ZoneData> e : this.zoneData.entrySet()) {
                Coord toCoord = e.getKey();
                ZoneData zData = e.getValue();
                Node toNode = zData.node;
                double attraction = zData.attraction;

                // CAR

                double distCar = 0;
                double amTravelTime = 0;
                int amCount = 0;
                for (int i = 0; i < this.amLcpTree.length; i++) {
                    LeastCostPathTree.NodeData data = this.amLcpTree[i].getTree().get(toNode.getId());
                    if (data != null) {
                        amTravelTime += data.getTime() - this.carAMDepTimes[i];
                        distCar += data.getDistance();
                        amCount++;
                    }
                }
                amTravelTime /= Math.max(1, amCount);

                double pmTravelTime = 0;
                int pmCount = 0;
                for (int i = 0; i < this.pmLcpTree.length; i++) {
                    LeastCostPathTree.NodeData data = this.pmLcpTree[i].getTree().get(toNode.getId());
                    if (data != null) {
                        pmTravelTime += data.getTime() - this.carPMDepTimes[i];
                        distCar += data.getDistance();
                        pmCount++;
                    }
                }
                pmTravelTime /= Math.max(1, pmCount);
                distCar /= Math.max(1, amCount + pmCount);

                distCar /= 1000.0; // we use kilometers in the following formulas

                double ttCar = Math.max(amTravelTime, pmTravelTime) / 60; // we need minutes in the following formulas
                double distCar0015 = (distCar < 15) ? distCar : 0;
                double distCar1550 = (distCar >= 15 && distCar < 50) ? distCar : 0;
                double distCar5099 = (distCar >= 50 && distCar < 100) ? distCar : 0;
                double distCar100x = (distCar >= 100) ? distCar : 0;

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

                    for (Map.Entry<ODConnection, Double> cs : connectionShares.entrySet()) {
                        ODConnection connection = cs.getKey();
                        double share = cs.getValue();

                        accessTime += share * accessTimes.get(connection.travelInfo.departureStop).floatValue();
                        egressTime += share * (float) connection.egressTime;
                        transferCount += share * (float) connection.transferCount;
                        travelTime += share * (float) connection.totalTravelTime();

                        double connTotalInVehTime = 0;
                        double connTrainInVehTime = 0;

                        RaptorRoute route = connection.travelInfo.getRaptorRoute();
                        for (RaptorRoute.RoutePart part : route.getParts()) {
                            if (part.line != null) {
                                // it's a non-transfer part, an actual pt stage

                                boolean isTrain = this.trainDetector.test(part.line, part.route);
                                double inVehicleTime = part.arrivalTime - part.boardingTime;

                                connTotalInVehTime += inVehicleTime;

                                if (isTrain) {
                                    connTrainInVehTime += inVehicleTime;
                                }
                            }
                        }

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
                }


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

                    double uBike = modes.bike ? (-0.25 + (-0.150) * distCar / 0.21667) : modes.missingModeUtility;
                    double uCar = modes.car ? (-0.40 + (-0.053)*ttCar + (-0.040)*distCar0015 + (-0.040)*distCar1550 + 0.015*distCar5099 + 0.010*distCar100x + (-0.047)*(carAccessTime+carEgressTime)/60 + (-0.135)*carParkingCost*2) : modes.missingModeUtility;
                    double uPt = (modes.pt && hasPT) ? (+0.75 + (-0.042)*ttBus + (-0.0378)*ttTrain + (-0.015)*distCar0015 + (-0.015)*distCar1550 + 0.005*distCar5099 + 0.025*distCar100x + (-0.050)*(ptAccessTime+ptEgressTime) + (-0.014)*(60/ptFrequency) + (-0.227)*ptTransfers) : modes.missingModeUtility;
                    double uWalk = modes.walk ? (+0.23 + (-0.100) * distCar / 0.078336) : modes.missingModeUtility;

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
