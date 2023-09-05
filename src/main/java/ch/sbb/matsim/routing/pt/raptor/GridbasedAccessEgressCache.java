package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.config.SBBIntermodalConfiggroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.variables.SBBModes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.filter.NetworkFilterManager;
import org.matsim.core.router.DefaultRoutingRequest;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.speedy.LeastCostPathTree;
import org.matsim.core.router.speedy.SpeedyALTFactory;
import org.matsim.core.router.speedy.SpeedyGraph;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.facilities.Facility;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;

import jakarta.inject.Inject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GridbasedAccessEgressCache implements AccessEgressRouteCache {

    public static final double CAR_FREESPEED_TRAVELTIME_FACTOR = 1.25;
    double bikeFreespeed = 16 / 3.6;
    private final Network carnet;
    private final Network bikenet;
    private final int gridsizeInM = 200;
    private final int diameterInM = 30000;
    private final int cellSize;
    private final int rowSize;
    private final String linkIdAttribute;
    private final Scenario scenario;
    private final SBBIntermodalConfiggroup sbbIntermodalConfiggroup;
    FreespeedTravelTimeAndDisutility disutility = new FreespeedTravelTimeAndDisutility(-0.1, 0.1, -0.01);
    private int threads;
    private Map<Id<TransitStopFacility>, Map<String, Integer>> accessTimesAtStops = new HashMap<>();
    private Logger logger = LogManager.getLogger(getClass());
    private Vehicle bike;
    private List<Id<TransitStopFacility>> cachedStops;
    private Map<Id<TransitStopFacility>, int[][]> cachedDistancesAndTimes = new ConcurrentHashMap<>();



    @Inject
    public GridbasedAccessEgressCache(Scenario scenario) {
        this.scenario = scenario;
        this.sbbIntermodalConfiggroup = ConfigUtils.addOrGetModule(scenario.getConfig(), SBBIntermodalConfiggroup.class);
        this.threads = scenario.getConfig().global().getNumberOfThreads();
        var railRaptorConfigGroup = ConfigUtils.addOrGetModule(scenario.getConfig(), SwissRailRaptorConfigGroup.class);
        SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet carInterModalSet = railRaptorConfigGroup.getIntermodalAccessEgressParameterSets().stream().filter(set -> set.getMode().equals(SBBModes.CARFEEDER)).findAny().get();
        this.linkIdAttribute = carInterModalSet.getLinkIdAttribute();
        String stopFilterValue = carInterModalSet.getStopFilterValue();
        String stopFilterAttribute = carInterModalSet.getStopFilterAttribute();
        scenario.getNetwork().getLinks().values().stream().filter(link -> link.getLength() < 0.1).forEach(link -> link.setLength(0.1));
        this.cachedStops = scenario.getTransitSchedule().getFacilities().values().stream().filter(transitStopFacility -> transitStopFacility.getAttributes().getAttribute(stopFilterAttribute).equals(stopFilterValue)).map(TransitStopFacility::getId).collect(Collectors.toList());
        logger.info("Cache will consist of " + cachedStops.size() + " stops.");

        rowSize = diameterInM / gridsizeInM;
        cellSize = rowSize * rowSize;
        NetworkFilterManager nfm = new NetworkFilterManager(scenario.getNetwork(), scenario.getConfig().network());
        nfm.addLinkFilter(l -> l.getAllowedModes().contains(SBBModes.CAR));
        this.carnet = nfm.applyFilters();


        VehicleType bikeType = scenario.getVehicles().getFactory().createVehicleType(Id.create("bikeType", VehicleType.class));
        bikeType.setNetworkMode(SBBModes.BIKE);
        bikeType.setMaximumVelocity(bikeFreespeed);
        this.bike = scenario.getVehicles().getFactory().createVehicle(Id.create("bike", Vehicle.class), bikeType);

        //TODO: fix this to pure bike net once our data is ready for it
        //  this.carnet.getLinks().values().forEach(link -> {
        //    Set<String> allowedModes = new HashSet<>(link.getAllowedModes());
        //  allowedModes.add(SBBModes.BIKE);
        //   link.setAllowedModes(allowedModes);
        // });
        //TODO: use scenario.getNetwork((
        NetworkFilterManager nfm2 = new NetworkFilterManager(scenario.getNetwork(), scenario.getConfig().network());
        nfm2.addLinkFilter(l -> l.getAllowedModes().contains(SBBModes.BIKE));
        this.bikenet = nfm2.applyFilters();


        prepareAccessTimes();
        calculateGridTraveltimesViaTree();

    }

    private void prepareAccessTimes() {
        for (var stopId : this.cachedStops) {
            Map<String, Integer> accessTimesPerMode = new HashMap<>();
            this.accessTimesAtStops.put(stopId, accessTimesPerMode);
            var stop = scenario.getTransitSchedule().getFacilities().get(stopId);
            Link carFromLink = carnet.getLinks().get(Id.createLinkId(String.valueOf(stop.getAttributes().getAttribute(linkIdAttribute))));

            for (var param : sbbIntermodalConfiggroup.getModeParameterSets()) {
                if (param.getAccessTimeZoneId() != null) {
                    int accessTime = (int) NetworkUtils.getLinkAccessTime(carFromLink, param.getMode()).seconds();
                    accessTimesPerMode.put(param.getMode(), accessTime);
                }
            }
        }
    }


    void calculateGridTraveltimesViaTree() {
        Gbl.printMemoryUsage();
        bikenet.getLinks().values().stream().filter(link -> link.getFreespeed() > bikeFreespeed).forEach(link -> link.setFreespeed(bikeFreespeed));
        SpeedyGraph carGraph = new SpeedyGraph(carnet);
        SpeedyGraph bikeGraph = new SpeedyGraph(bikenet);
        SpeedyALTFactory factory = new SpeedyALTFactory();
        logger.info("Building Cache Structure for " + cachedStops.size() + " stops.");

        Gbl.printMemoryUsage();
        List<List<Id<TransitStopFacility>>> partitions = new ArrayList<>();
        if (cachedStops.size() < threads) {
            threads = Math.max(cachedStops.size(), 1);
        }
        int size = cachedStops.size() / threads;
        for (int i = 0; i < threads; i++) {
            int toIndex = Math.min((i + 1) * size, cachedStops.size());
            List<Id<TransitStopFacility>> partition = cachedStops.subList(i * size, toIndex);
            partitions.add(partition);
        }
        partitions.parallelStream().forEach(list -> {
            LeastCostPathTree bikeLeastCostPathTree = new LeastCostPathTree(bikeGraph, disutility, disutility);
            LeastCostPathTree carLeastCostPathTree = new LeastCostPathTree(carGraph, disutility, disutility);
            var carLcp = factory.createPathCalculator(carnet, disutility, disutility);
            var bikeLcp = factory.createPathCalculator(bikenet, disutility, disutility);
            for (Id<TransitStopFacility> stopId : list) {
                var stop = scenario.getTransitSchedule().getFacilities().get(stopId);
                cachedDistancesAndTimes.put(stopId, calculateGridForStopViaTree(stop, carLeastCostPathTree, bikeLeastCostPathTree, carLcp, bikeLcp));
            }

        });

    }


    int[][] calculateGridForStopViaTree(TransitStopFacility stop, LeastCostPathTree carLeastCostPathTree, LeastCostPathTree bikeLeastCostPathTree, LeastCostPathCalculator carlcp, LeastCostPathCalculator bikelcp) {
        Link carFromLink = carnet.getLinks().get(Id.createLinkId(String.valueOf(stop.getAttributes().getAttribute(linkIdAttribute))));
        Node carFromNode = carFromLink.getToNode();

        Link bikeFromLink = NetworkUtils.getNearestLink(bikenet, stop.getCoord());
        Node bikeFromNode = bikeFromLink.getToNode();
        carLeastCostPathTree.calculate(carFromNode.getId().index(), 0, null, null, new LeastCostPathTree.TravelDistanceStopCriterion(this.diameterInM * 5.0));
        bikeLeastCostPathTree.calculate(bikeFromNode.getId().index(), 0, null, bike, new LeastCostPathTree.TravelDistanceStopCriterion(this.diameterInM * 5.0));


        int[][] dist = new int[cellSize][4];

        int found = 0;
        for (int i = 0; i < cellSize; i++) {
            Coord coord = getCellCoordinate(stop.getCoord(), i);
            Node nearestCarNode = NetworkUtils.getNearestNode(carnet, coord);
            int nearestCarNodeIndex = nearestCarNode.getId().index();
            Node nearestBikeNode = NetworkUtils.getNearestNode(bikenet, coord);
            int nearestBikeNodeIndex = nearestBikeNode.getId().index();
            double carTravelTime;
            double carDistance;
            if (carLeastCostPathTree.getTime(nearestCarNodeIndex).isDefined()) {
                carTravelTime = carLeastCostPathTree.getTime(nearestCarNodeIndex).seconds() * CAR_FREESPEED_TRAVELTIME_FACTOR;
                carDistance = carLeastCostPathTree.getDistance(nearestCarNodeIndex);
                found++;
            } else {
                var carpath = carlcp.calcLeastCostPath(nearestCarNode, carFromNode, 0, null, null);
                carTravelTime = carpath.travelTime * CAR_FREESPEED_TRAVELTIME_FACTOR;
                carDistance = carpath.links.stream().mapToDouble(l -> l.getLength()).sum();
            }


            double bikeTravelTime;
            double bikeDistance;
            if (bikeLeastCostPathTree.getTime(nearestBikeNodeIndex).isDefined()) {
                bikeTravelTime = bikeLeastCostPathTree.getTime(nearestBikeNodeIndex).seconds();
                bikeDistance = bikeLeastCostPathTree.getDistance(nearestBikeNodeIndex);
            } else {
                var bikepath = bikelcp.calcLeastCostPath(nearestBikeNode, bikeFromNode, 0, null, bike);
                bikeTravelTime = bikepath.travelTime;
                bikeDistance = bikepath.links.stream().mapToDouble(l -> l.getLength()).sum();
            }
            dist[i][0] = (int) carTravelTime;
            dist[i][1] = (int) carDistance;
            dist[i][2] = (int) bikeTravelTime;
            dist[i][3] = (int) bikeDistance;
        }
        //logger.info("Found " + found + " of " + cellSize);
        return dist;
    }

    Coord getCellCoordinate(Coord stopCoord, int i) {
        double initialYOffset = stopCoord.getY() + diameterInM / 2; //top left
        double initialXOffset = stopCoord.getX() - diameterInM / 2;
        int rowNumber = i / rowSize;
        int columNumber = i % rowSize;
        double x = initialXOffset + columNumber * gridsizeInM;
        double y = initialYOffset - rowNumber * gridsizeInM;
        return new Coord(x, y);
    }

    /**
     * @param coord
     * @param stopCoord
     * @return grid field number, sorted from top left to bottom right
     */
    int getCellNumber(Coord stopCoord, Coord coord) {
        double topLeftY = stopCoord.getY() + diameterInM / 2;
        double topLeftX = stopCoord.getX() - diameterInM / 2;
        double xOffset = coord.getX() - topLeftX; //>=0
        double yOffset = -coord.getY() + topLeftY; //>=0
        int row = (int) (yOffset / gridsizeInM);
        int column = (int) (xOffset / gridsizeInM);
        int i = row * rowSize + column;
        return i;
    }

    @Override
    public RouteCharacteristics getCachedRouteCharacteristics(String mode, Facility stopFacility, Facility actFacility, RoutingModule module, Person person) {
        Id<TransitStopFacility> transitStopFacilityId = ((Identifiable<TransitStopFacility>) stopFacility).getId();
        int cell = getCellNumber(stopFacility.getCoord(), actFacility.getCoord());
        int[][] cache = this.cachedDistancesAndTimes.get(transitStopFacilityId);
        if (cache != null && cellInBound(cell)) {
            int[] cellCache = cache[cell];
            double travelTime = mode.equals(SBBModes.BIKEFEEDER) ? cellCache[2] : cellCache[0];
            double travelDistance = mode.equals(SBBModes.BIKEFEEDER) ? cellCache[3] : cellCache[1];
            Integer accessTime = this.accessTimesAtStops.get(transitStopFacilityId).get(mode);
            if (accessTime == null) {
                accessTime = 0;
            }
            double egressTime = NetworkUtils.getLinkEgressTime(scenario.getNetwork().getLinks().get(actFacility.getLinkId()), mode).orElse(0);
            return new RouteCharacteristics(travelDistance, accessTime, egressTime, travelTime);
        } else {
            List<? extends PlanElement> routeParts = module.calcRoute(DefaultRoutingRequest.withoutAttributes(stopFacility, actFacility, 3d * 3600, person));
            Leg routedLeg = TripStructureUtils.getLegs(routeParts).stream().filter(leg -> leg.getMode().equals(mode)).findFirst().orElseThrow(RuntimeException::new);
            double egressTime = NetworkUtils.getLinkEgressTime(scenario.getNetwork().getLinks().get(actFacility.getLinkId()), mode).seconds();
            double travelDistance = routedLeg.getRoute().getDistance();
            double travelTime = routedLeg.getRoute().getTravelTime().seconds();
            double accessTime = NetworkUtils.getLinkAccessTime(scenario.getNetwork().getLinks().get(stopFacility.getLinkId()), mode).seconds();
            return new RouteCharacteristics(travelDistance, accessTime, egressTime, travelTime);

        }
    }

    private boolean cellInBound(int cell) {
        return (cell >= 0 && cell < cellSize);
    }



}