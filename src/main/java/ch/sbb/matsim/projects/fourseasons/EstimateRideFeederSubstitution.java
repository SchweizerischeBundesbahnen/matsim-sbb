package ch.sbb.matsim.projects.fourseasons;

import ch.sbb.matsim.analysis.tripsandlegsanalysis.RailTripsAnalyzer;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.preparation.casestudies.GenerateNetworkChangeEvents;
import com.google.common.primitives.Ints;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.HasPlansAndId;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.network.filter.NetworkFilterManager;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.*;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EstimateRideFeederSubstitution {

    private static final double TRANSFERWAITTIMELOSS = 900;
    private static final double TIMESAVINGS = 120;
    private final RailTripsAnalyzer railTripsAnalyzer;
    private final List<Id<TransitStopFacility>> stopsCandidateList;
    private final Scenario scenario;
    private static final Logger LOGGER = LogManager.getLogger(EstimateRideFeederSubstitution.class);
    private final Map<Id<TransitStopFacility>, Long> flowsAtStops;
    private final Map<Id<TransitStopFacility>, SubstitionData> substitionDataPerStop = new HashMap<>();
    private final NetworkRoutingModule networkRoutingModule;
    private final Network carnet;

    public EstimateRideFeederSubstitution(Scenario scenario, List<Id<TransitStopFacility>> stopsCandidateList, TravelDisutility disutility, TravelTime travelTime) {
        //fix zones collection if required ever again
        this.railTripsAnalyzer = new RailTripsAnalyzer(scenario.getTransitSchedule(), scenario.getNetwork(), null);
        this.stopsCandidateList = stopsCandidateList;
        this.scenario = scenario;
        flowsAtStops = calculateFlowPassingThroughStops();
        NetworkFilterManager nfm = new NetworkFilterManager(scenario.getNetwork(), scenario.getConfig().network());
        nfm.addLinkFilter(l -> l.getAllowedModes().contains(SBBModes.CAR));

        this.carnet = nfm.applyFilters();
        new NetworkCleaner().run(carnet);
        var lcp = new DijkstraFactory().createPathCalculator(carnet, disutility, travelTime);
        this.networkRoutingModule = new NetworkRoutingModule(SBBModes.RIDEFEEDER, scenario.getPopulation().getFactory(), carnet, lcp);
    }

    public static void main(String[] args) throws IOException {

        String stationsList = args[0];
        double factor = Double.parseDouble(args[1]);
        String outputFile = args[2];
        String transitScheduleFile = args[3];
        String networkFile = args[4];
        String eventsFile = args[5];
        String experiencedPlansFile1 = args[6];
        String experiencedPlansFile2 = args.length > 7 ? args[7] : null;
        List<Id<TransitStopFacility>> stations = Files.lines(Path.of(stationsList)).filter(s -> (!s.equals(""))).map(s -> Id.create(s, TransitStopFacility.class)).collect(Collectors.toList());
        LOGGER.info(stations);

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(transitScheduleFile);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);
        new PopulationReader(scenario).readFile(experiencedPlansFile1);
        if (experiencedPlansFile2 != null) {
            new PopulationReader(scenario).readFile(experiencedPlansFile2);
        }
        EstimateRideFeederSubstitution estimateRideFeederSubstitution;
        if (!eventsFile.equals("-")) {
            TravelTimeCalculator travelTimeCalculator = GenerateNetworkChangeEvents.readEvents(scenario, eventsFile);
            TravelTime travelTime = travelTimeCalculator.getLinkTravelTimes();
            TravelDisutility disutility = new OnlyTimeDependentTravelDisutility(travelTime);
            estimateRideFeederSubstitution = new EstimateRideFeederSubstitution(scenario, stations, disutility, travelTime);
        } else {
            FreespeedTravelTimeAndDisutility disutility = new FreespeedTravelTimeAndDisutility(0.0, 0, -0.01);
            estimateRideFeederSubstitution = new EstimateRideFeederSubstitution(scenario, stations, disutility, disutility);
        }

        estimateRideFeederSubstitution.run();
        estimateRideFeederSubstitution.writeStatsTable(outputFile, factor);

    }

    private void run() {
        for (var stop : this.stopsCandidateList) {
            LOGGER.info("Handling Stop: " + scenario.getTransitSchedule().getFacilities().get(stop).getName());
            SubstitionData substitionData = new SubstitionData(stop);
            substitionDataPerStop.put(stop, substitionData);
            Tuple<List<TripStructureUtils.Trip>, List<TripStructureUtils.Trip>> trips = findRailTripsStartingOrEndingAtStop(stop);
            handleStartingTrips(trips.getFirst(), substitionData);
            handleEndingTrips(trips.getSecond(), substitionData);
        }
    }

    private void writeStatsTable(String filename, double factor) {
        String stopId = "stop_id";
        String stopName = "stop_name";
        String einsteiger = "Einsteiger";
        String aussteiger = "Aussteiger";
        String durchfahrer = "Durchfahrer";
        String zeitersparnis = "Zeitersparnis";
        String bahn_pkm_verloren = "Bahn_PKM_verloren";
        String ridesharing_pkm = "Ridesharing_PKM";
        String zeitdiff = "Mittlere Zeitdifferenz";
        String zeitdiff_max = "Max Zeitdifferenz";
        String zeitdiff_sum = "Gesamt Zeitdifferenz";
        String verteilung_bf = "Verteilung_Bf";
        String rideshare_spitz = "Ridesharing Fahrten Spitzenstunde";
        String[] header = {stopId, stopName, einsteiger, aussteiger, durchfahrer, zeitersparnis, bahn_pkm_verloren, ridesharing_pkm, zeitdiff_sum, zeitdiff, zeitdiff_max, verteilung_bf,
                rideshare_spitz};

        try (CSVWriter writer = new CSVWriter(null, header, filename)) {
            for (SubstitionData d : substitionDataPerStop.values()) {
                writer.set(stopId, d.stopFacilityId.toString());
                writer.set(stopName, scenario.getTransitSchedule().getFacilities().get(d.stopFacilityId).getName());
                writer.set(einsteiger, Double.toString(factor * d.startingTrips));
                writer.set(aussteiger, Double.toString(factor * d.endingTrips));
                Long flow = this.flowsAtStops.get(d.stopFacilityId);
                if (flow == null) {
                    flow = 0L;
                }
                writer.set(durchfahrer, Double.toString(factor * flow));
                writer.set(zeitersparnis, Time.writeTime(factor * flow * TIMESAVINGS));
                writer.set(bahn_pkm_verloren, Double.toString(factor * d.railDistanceLost.getSum() / 1000.0));
                writer.set(ridesharing_pkm, Double.toString(factor * d.rideshareDistanceNeeded.getSum() / 1000.0));
                writer.set(zeitdiff, Time.writeTime(d.timeGains.getMean()));
                writer.set(zeitdiff_sum, Time.writeTime(factor * d.timeGains.getSum()));
                writer.set(zeitdiff_max, Time.writeTime(d.timeGains.getMax()));
                writer.set(verteilung_bf, d.substitutionDistribution.entrySet().stream().map(e -> e.getKey().toString() + "-" + factor * e.getValue().doubleValue()).collect(Collectors.joining(",")));
                writer.set(rideshare_spitz, Double.toString(factor * Ints.max(d.rideshareTripsPerHour)));

                writer.writeRow();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void handleEndingTrips(List<Trip> endingTrips, SubstitionData substitionData) {
        substitionData.endingTrips = endingTrips.size();
        for (Trip trip : endingTrips) {
            Leg railLegStarting = trip.getLegsOnly().stream()
                    .filter(leg -> SBBModes.isPTMode(leg.getMode()))
                    .filter(leg -> {
                        TransitPassengerRoute route = (TransitPassengerRoute) leg.getRoute();
                        return route.getEgressStopId().equals(substitionData.stopFacilityId);
                    })
                    .findFirst().get();
            TransitPassengerRoute railRoute = (TransitPassengerRoute) railLegStarting.getRoute();
            Tuple<Id<TransitStopFacility>, Double> substitutionStopAndTimeOffsetDifference = findPreviousStop(railRoute);
            Id<TransitStopFacility> substituteStopId = substitutionStopAndTimeOffsetDifference.getFirst();
            Link substituteFromLink = NetworkUtils.getNearestLink(carnet, scenario.getTransitSchedule().getFacilities().get(substituteStopId).getCoord());
            Link toLink = carnet.getLinks().get(trip.getDestinationActivity().getLinkId());
            var route = networkRoutingModule.calcRoute(
                    DefaultRoutingRequest.withoutAttributes(new LinkWrapperFacility(substituteFromLink), new LinkWrapperFacility(toLink), trip.getOriginActivity().getEndTime().seconds(), null));
            Leg substituteLeg = (Leg) route.get(0);

            double originalTravelTimeToSubstituteStop =
                    trip.getDestinationActivity().getStartTime().seconds() - (substitutionStopAndTimeOffsetDifference.getSecond() + railRoute.getBoardingTime().seconds() + railRoute.getTravelTime()
                            .seconds());
            double substituteTravelTime = TRANSFERWAITTIMELOSS + substituteLeg.getTravelTime().seconds();
            substitionData.rideshareDistanceNeeded.addValue(substituteLeg.getRoute().getDistance());
            substitionData.timeGains.addValue(substituteTravelTime - originalTravelTimeToSubstituteStop);
            substitionData.substitutionDistribution.computeIfAbsent(substituteStopId, s -> new MutableInt()).increment();
            int departureHour = (int) Math.floor(trip.getOriginActivity().getEndTime().seconds() / 3600.);
            substitionData.rideshareTripsPerHour[departureHour]++;
            double railDistanceLost_m = RouteUtils.calcDistance(
                    scenario.getTransitSchedule().getTransitLines().get(railRoute.getLineId()).getRoutes().get(railRoute.getRouteId()),
                    scenario.getTransitSchedule().getFacilities().get(substituteStopId),
                    scenario.getTransitSchedule().getFacilities().get(railRoute.getEgressStopId()),
                    scenario.getNetwork());
            substitionData.railDistanceLost.addValue(railDistanceLost_m);
        }

    }

    private void handleStartingTrips(List<Trip> startingTrips, SubstitionData substitionData) {
        substitionData.startingTrips = startingTrips.size();
        for (Trip trip : startingTrips) {
            Leg railLegStarting = trip.getLegsOnly().stream()
                    .filter(leg -> SBBModes.isPTMode(leg.getMode()))
                    .filter(leg -> {
                        TransitPassengerRoute route = (TransitPassengerRoute) leg.getRoute();
                        return route.getAccessStopId().equals(substitionData.stopFacilityId);
                    })
                    .findFirst().get();
            TransitPassengerRoute railRoute = (TransitPassengerRoute) railLegStarting.getRoute();
            Tuple<Id<TransitStopFacility>, Double> substitutionStopAndTimeOffsetDifference = findNextStop(railRoute);
            Id<TransitStopFacility> substituteStopId = substitutionStopAndTimeOffsetDifference.getFirst();

            Link substituteToLink = NetworkUtils.getNearestLink(carnet, scenario.getTransitSchedule().getFacilities().get(substituteStopId).getCoord());
            Link fromLink = carnet.getLinks().get(trip.getOriginActivity().getLinkId());
            var route = networkRoutingModule.calcRoute(
                    DefaultRoutingRequest.withoutAttributes(new LinkWrapperFacility(fromLink), new LinkWrapperFacility(substituteToLink), trip.getOriginActivity().getEndTime().seconds(), null));
            Leg substituteLeg = (Leg) route.get(0);
            double originalTravelTimeToSubstituteStop = substitutionStopAndTimeOffsetDifference.getSecond() + railRoute.getBoardingTime().seconds() - trip.getOriginActivity().getEndTime().seconds();
            double substituteTravelTime = TRANSFERWAITTIMELOSS + substituteLeg.getTravelTime().seconds();
            substitionData.rideshareDistanceNeeded.addValue(substituteLeg.getRoute().getDistance());
            substitionData.timeGains.addValue(substituteTravelTime - originalTravelTimeToSubstituteStop);
            substitionData.substitutionDistribution.computeIfAbsent(substituteStopId, s -> new MutableInt()).increment();
            int departureHour = (int) Math.floor(trip.getOriginActivity().getEndTime().seconds() / 3600.);
            substitionData.rideshareTripsPerHour[departureHour]++;
            double railDistanceLost_m = RouteUtils.calcDistance(scenario.getTransitSchedule().getTransitLines().get(railRoute.getLineId()).getRoutes().get(railRoute.getRouteId()),
                    scenario.getTransitSchedule().getFacilities().get(railRoute.getAccessStopId()), scenario.getTransitSchedule().getFacilities().get(substituteStopId), scenario.getNetwork());
            substitionData.railDistanceLost.addValue(railDistanceLost_m);
        }

    }

    private Tuple<Id<TransitStopFacility>, Double> findNextStop(TransitPassengerRoute railRoute) {

        var transitRoute = scenario.getTransitSchedule().getTransitLines().get(railRoute.getLineId()).getRoutes().get(railRoute.getRouteId());
        var accessFacility = scenario.getTransitSchedule().getFacilities().get(railRoute.getAccessStopId());
        TransitRouteStop accesRouteStop = transitRoute.getStop(accessFacility);
        int index = transitRoute.getStops().lastIndexOf(accesRouteStop);
        double departureOffset = accesRouteStop.getDepartureOffset().seconds();
        TransitRouteStop substitute = transitRoute.getStops().get(index + 1);
        double nextDepartureOffset = substitute.getDepartureOffset().or(substitute.getArrivalOffset()).seconds();
        double railTimeSaved = nextDepartureOffset - departureOffset;
        return new Tuple<>(substitute.getStopFacility().getId(), railTimeSaved);

    }

    private Tuple<Id<TransitStopFacility>, Double> findPreviousStop(TransitPassengerRoute railRoute) {

        var transitRoute = scenario.getTransitSchedule().getTransitLines().get(railRoute.getLineId()).getRoutes().get(railRoute.getRouteId());
        var egressFacility = scenario.getTransitSchedule().getFacilities().get(railRoute.getEgressStopId());
        TransitRouteStop egressStopId = transitRoute.getStop(egressFacility);
        int index = transitRoute.getStops().lastIndexOf(egressStopId);
        double arrivalOffset = egressStopId.getArrivalOffset().seconds();
        TransitRouteStop substitute = transitRoute.getStops().get(index - 1);
        double previousArrivalOffset = substitute.getArrivalOffset().or(substitute.getDepartureOffset()).seconds();
        double railTimeSaved = arrivalOffset - previousArrivalOffset;
        return new Tuple<>(substitute.getStopFacility().getId(), railTimeSaved);
    }

    private Tuple<List<TripStructureUtils.Trip>, List<TripStructureUtils.Trip>> findRailTripsStartingOrEndingAtStop(Id<TransitStopFacility> stop) {
        List<TripStructureUtils.Trip> starting = new ArrayList<>();
        List<TripStructureUtils.Trip> ending = new ArrayList<>();
        scenario.getPopulation().getPersons().values().stream()
                .map(HasPlansAndId::getSelectedPlan)
                .flatMap(plan -> TripStructureUtils.getTrips(plan).stream())
                .forEach(trip -> {
                    var od = railTripsAnalyzer.getOriginDestination(trip);
                    if (od != null) {
                        if (od.getFirst().equals(stop)) {
                            starting.add(trip);
                        } else if (od.getSecond().equals(stop)) {
                            ending.add(trip);
                        }
                    }
                });
        LOGGER.info("Stop " + stop.toString()
                + " Trips Starting: " + starting.size()
                + " Trips Ending: " + ending.size()
        );
        return new Tuple<>(starting, ending);

    }

    private Map<Id<TransitStopFacility>, Long> calculateFlowPassingThroughStops() {
        Map<Id<Link>, Id<TransitStopFacility>> stopLinks = stopsCandidateList.stream()
                .collect(Collectors.toMap(stopFacilityId -> scenario.getTransitSchedule().getFacilities().get(stopFacilityId).getLinkId(), stopFacilityId -> stopFacilityId));
        //Map<Id<Link>,MutableInt>
        return this.scenario.getPopulation().getPersons().values().stream()
                .map(HasPlansAndId::getSelectedPlan)
                .flatMap(plan -> TripStructureUtils.getLegs(plan).stream())
                .filter(leg -> SBBModes.isPTMode(leg.getMode()))
                .map(leg -> (TransitPassengerRoute) leg.getRoute())
                .flatMap(transitPassengerRoute -> railTripsAnalyzer.getPtLinkIdsTraveledOnExludingAccessEgressStop(transitPassengerRoute).stream())
                .filter(stopLinks::containsKey)
                .map(stopLinks::get)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

    }

    private static class SubstitionData {

        private final Id<TransitStopFacility> stopFacilityId;
        private final DescriptiveStatistics railDistanceLost = new DescriptiveStatistics();
        private final DescriptiveStatistics timeGains = new DescriptiveStatistics();
        private final DescriptiveStatistics rideshareDistanceNeeded = new DescriptiveStatistics();
        private final int[] rideshareTripsPerHour = new int[36];
        private final Map<Id<TransitStopFacility>, MutableInt> substitutionDistribution = new HashMap<>();
        private int startingTrips = 0;
        private int endingTrips = 0;

        private SubstitionData(Id<TransitStopFacility> stopFacilityId) {
            this.stopFacilityId = stopFacilityId;
        }
    }


}
