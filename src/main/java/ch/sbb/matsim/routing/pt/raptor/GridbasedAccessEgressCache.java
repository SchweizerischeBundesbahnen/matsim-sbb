package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.config.SBBIntermodalConfiggroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.variables.SBBModes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
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
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.network.filter.NetworkFilterManager;
import org.matsim.core.router.DefaultRoutingRequest;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.facilities.Facility;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class GridbasedAccessEgressCache implements AccessEgressRouteCache {

    public static final double CAR_FREESPEED_TRAVELTIME_FACTOR = 1.25;
    private final Network carnet;
    private final Network bikenet;
    private final LeastCostPathCalculator carlcp;
    private final LeastCostPathCalculator bikelcp;
    private final int gridsizeInM = 200;
    private final int diameterInM = 30000;
    private final int cellSize;
    private final int rowSize;
    private final String linkIdAttribute;
    private final Scenario scenario;
    private final SBBIntermodalConfiggroup sbbIntermodalConfiggroup;
    private Set<Id<TransitStopFacility>> cachedStops;
    private Map<Id<TransitStopFacility>, int[][]> cachedDistancesAndTimes = new HashMap<>();
    private Map<Id<TransitStopFacility>, Map<String, Integer>> accessTimesAtStops = new HashMap<>();
    private Logger logger = LogManager.getLogger(getClass());
    private Vehicle bike;


    @Inject
    public GridbasedAccessEgressCache(Scenario scenario) {
        this.scenario = scenario;
        this.sbbIntermodalConfiggroup = ConfigUtils.addOrGetModule(scenario.getConfig(), SBBIntermodalConfiggroup.class);
        var railRaptorConfigGroup = ConfigUtils.addOrGetModule(scenario.getConfig(), SwissRailRaptorConfigGroup.class);
        SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet carInterModalSet = railRaptorConfigGroup.getIntermodalAccessEgressParameterSets().stream().filter(set -> set.getMode().equals(SBBModes.CARFEEDER)).findAny().get();
        this.linkIdAttribute = carInterModalSet.getLinkIdAttribute();
        String stopFilterValue = carInterModalSet.getStopFilterValue();
        String stopFilterAttribute = carInterModalSet.getStopFilterAttribute();
        this.cachedStops = scenario.getTransitSchedule().getFacilities().values().stream().filter(transitStopFacility -> transitStopFacility.getAttributes().getAttribute(stopFilterAttribute).equals(stopFilterValue)).map(TransitStopFacility::getId).collect(Collectors.toSet());
        logger.info("Cache will consist of " + cachedStops.size() + " stops.");

        rowSize = diameterInM / gridsizeInM;
        cellSize = rowSize * rowSize;
        NetworkFilterManager nfm = new NetworkFilterManager(scenario.getNetwork(), scenario.getConfig().network());
        nfm.addLinkFilter(l -> l.getAllowedModes().contains(SBBModes.CAR));
        this.carnet = nfm.applyFilters();
        new NetworkCleaner().run(carnet);
        FreespeedTravelTimeAndDisutility disutility = new FreespeedTravelTimeAndDisutility(0.0, 0, -0.01);
        this.carlcp = new DijkstraFactory().createPathCalculator(carnet, disutility, disutility);
        VehicleType bikeType = scenario.getVehicles().getFactory().createVehicleType(Id.create("bikeType", VehicleType.class));
        bikeType.setNetworkMode(SBBModes.BIKE);
        bikeType.setMaximumVelocity(16 / 3.6);
        this.bike = scenario.getVehicles().getFactory().createVehicle(Id.create("bike", Vehicle.class), bikeType);

        NetworkFilterManager nfm2 = new NetworkFilterManager(scenario.getNetwork(), scenario.getConfig().network());
        nfm2.addLinkFilter(l -> l.getAllowedModes().contains(SBBModes.BIKE));
        this.bikenet = nfm2.applyFilters();
        new NetworkCleaner().run(bikenet);

        this.bikelcp = new DijkstraFactory().createPathCalculator(bikenet, disutility, disutility);

        prepareAccessTimes();

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


    void calculateGridTraveltimes() {
        Gbl.printMemoryUsage();
        logger.info("Building Cache Structure for " + cachedStops.size() + " stops.");
        cachedDistancesAndTimes = cachedStops
                .stream()
                .map(stopFacilityId -> scenario.getTransitSchedule().getFacilities().get(stopFacilityId))
                .collect(Collectors.toMap(stop -> stop.getId(), stop -> calculateGridForStop(stop)));
        Gbl.printMemoryUsage();

    }

    int[][] calculateGridForStop(TransitStopFacility stop) {
        Link carFromLink = carnet.getLinks().get(Id.createLinkId(String.valueOf(stop.getAttributes().getAttribute(linkIdAttribute))));
        Node carFromNode = carFromLink.getToNode();
        Link bikeFromLink = NetworkUtils.getNearestLink(bikenet, stop.getCoord());
        Node bikeFromNode = bikeFromLink.getToNode();
        int[][] dist = new int[cellSize][4];

        for (int i = 0; i < cellSize; i++) {
            Coord coord = getCellCoordinate(stop.getCoord(), i);
            Node nearestBikeNode = NetworkUtils.getNearestNode(bikenet, coord);
            Node nearestCarNode = NetworkUtils.getNearestNode(carnet, coord);

            var carpath = carlcp.calcLeastCostPath(nearestCarNode, carFromNode, 0, null, null);
            double carTravelTime = carpath.travelTime * CAR_FREESPEED_TRAVELTIME_FACTOR;
            double carDistance = carpath.links.stream().mapToDouble(l -> l.getLength()).sum();
            var bikepath = bikelcp.calcLeastCostPath(nearestBikeNode, bikeFromNode, 0, null, bike);
            double bikeTravelTime = bikepath.travelTime;
            double bikeDistance = bikepath.links.stream().mapToDouble(l -> l.getLength()).sum();
            dist[i][0] = (int) carTravelTime;
            dist[i][1] = (int) carDistance;
            dist[i][2] = (int) bikeTravelTime;
            dist[i][3] = (int) bikeDistance;
        }

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
        Id<TransitStopFacility> transitStopFacilityId = ((SBBIntermodalRaptorStopFinder.ChangedLinkFacility) stopFacility).getId();
        int cell = getCellNumber(stopFacility.getCoord(), actFacility.getCoord());
        int[][] cache = this.cachedDistancesAndTimes.get(transitStopFacilityId);
        if (cache != null && cellInBound(cell)) {
            int[] cellCache = cache[cell];
            double travelTime = mode.equals(SBBModes.BIKEFEEDER) ? cellCache[2] : cellCache[0];
            double travelDistance = mode.equals(SBBModes.BIKEFEEDER) ? cellCache[3] : cellCache[2];
            double accessTime = this.accessTimesAtStops.get(transitStopFacilityId).get(mode);
            double egressTime = NetworkUtils.getLinkEgressTime(scenario.getNetwork().getLinks().get(actFacility.getLinkId()), mode).seconds();
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

    public void writeCache(String fileName) {
        try (BufferedWriter bw = IOUtils.getBufferedWriter(fileName)) {
            for (var entry : this.cachedDistancesAndTimes.entrySet()) {
                bw.write(entry.getKey().toString() + ";");
                String value = Arrays.stream(entry.getValue())
                        .flatMapToInt(ii -> Arrays.stream(ii))
                        .mapToObj(a -> Integer.toString(a))
                        .collect(Collectors.joining(";"));
                bw.write(value);
                bw.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void readCache(String fileName) {
        try (BufferedReader bufferedReader = IOUtils.getBufferedReader(fileName)) {
            String line = bufferedReader.readLine();
            while (line != null) {
                String[] entries = line.split(";");
                if (entries.length > 0) {
                    var stopId = Id.create(entries[0], TransitStopFacility.class);
                    int[][] cache = new int[cellSize][4];
                    int cell = 0;
                    for (int i = 1; i < entries.length; i = i + 4) {
                        cache[cell][0] = Integer.parseInt(entries[i]);
                        cache[cell][1] = Integer.parseInt(entries[i + 1]);
                        cache[cell][2] = Integer.parseInt(entries[i + 2]);
                        cache[cell][3] = Integer.parseInt(entries[i + 3]);
                    }
                    cachedDistancesAndTimes.put(stopId, cache);
                }
                line = bufferedReader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


}
