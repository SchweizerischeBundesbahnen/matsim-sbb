package ch.sbb.matsim.visumdistribution;

import static ch.sbb.matsim.analysis.tripsandlegsanalysis.PutSurveyWriter.DIRECTION_CODE;
import static ch.sbb.matsim.analysis.tripsandlegsanalysis.PutSurveyWriter.FZPNAME;
import static ch.sbb.matsim.analysis.tripsandlegsanalysis.PutSurveyWriter.LINEROUTENAME;
import static ch.sbb.matsim.analysis.tripsandlegsanalysis.PutSurveyWriter.STOP_NO;
import static ch.sbb.matsim.analysis.tripsandlegsanalysis.PutSurveyWriter.TRANSITLINE;
import static ch.sbb.matsim.analysis.tripsandlegsanalysis.PutSurveyWriter.TSYS_CODE;
import static ch.sbb.matsim.analysis.tripsandlegsanalysis.PutSurveyWriter.getDayIndex;
import static ch.sbb.matsim.analysis.tripsandlegsanalysis.PutSurveyWriter.getTime;

import ch.sbb.matsim.analysis.tripsandlegsanalysis.PutSurveyWriter;
import ch.sbb.matsim.analysis.tripsandlegsanalysis.PutSurveyWriter.PutSurveyEntry;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorInVehicleCostCalculator;
import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorParametersForPerson;
import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorStopFinder;
import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorTransferCostCalculator;
import ch.sbb.matsim.routing.pt.raptor.LeastCostRaptorRouteSelector;
import ch.sbb.matsim.routing.pt.raptor.RaptorInVehicleCostCalculator;
import ch.sbb.matsim.routing.pt.raptor.RaptorParameters;
import ch.sbb.matsim.routing.pt.raptor.RaptorParametersForPerson;
import ch.sbb.matsim.routing.pt.raptor.RaptorRoute;
import ch.sbb.matsim.routing.pt.raptor.RaptorRoute.RoutePart;
import ch.sbb.matsim.routing.pt.raptor.RaptorRouteSelector;
import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig;
import ch.sbb.matsim.routing.pt.raptor.RaptorTransferCostCalculator;
import ch.sbb.matsim.routing.pt.raptor.RaptorUtils;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorCore.TravelInfo;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import omx.OmxFile;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class DepartureRouter {

    final static String schedualFile = "Z:/99_Playgrounds/MD/Umlegung2/2018/transitSchedule.xml.gz";
    final static String netwoekFile = "Z:/99_Playgrounds/MD/Umlegung2/2018/transitNetwork.xml.gz";
    final static String omxFile = "Z:/99_Playgrounds/MD/Umlegung2/visum/Demand2018.omx";
    final static String assignmentFile = "Z:/99_Playgrounds/MD/Umlegung2/visum/ZoneToNode.csv";
    final static String visum = "routesDepatureVisumLines.csv";
    final static String csvLines = "treeRoutesvLines.csv";
    Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
    SwissRailRaptorData data;
    RaptorParametersForPerson raptorParametersForPerson;
    RaptorRouteSelector routeSelector = new LeastCostRaptorRouteSelector();
    RaptorInVehicleCostCalculator inVehicleCostCalculator = new DefaultRaptorInVehicleCostCalculator();
    //RaptorInVehicleCostCalculator inVehicleCostCalculator = new VisumRaptorInVehicleCostCalculator();
    RaptorTransferCostCalculator transferCostCalculator = new DefaultRaptorTransferCostCalculator();
    //RaptorTransferCostCalculator transferCostCalculator = new VisumRaptorTransferCostCalculator();
    DefaultRaptorStopFinder defaultRaptorStopFinder = new DefaultRaptorStopFinder(new DefaultRaptorIntermodalAccessEgress(), null);
    static AtomicInteger atomicInteger = new AtomicInteger(0);
    static AtomicInteger depatureInteger = new AtomicInteger(0);
    static AtomicInteger lines = new AtomicInteger(0);
    static AtomicInteger calcInteger = new AtomicInteger(0);
    final double coefficientDeltaTime = 1.85 / 60; //in sekunden umgerechnet
    final double transferWalkinMargin;
    int matrixNames;
    int distribution = 0;

    RaptorParameters params = RaptorUtils.createParameters(scenario.getConfig());
    OmxFile omx;
    Map<String, Set<TransitStopFacility>> matchingZoneToStops = new HashMap<>();
    Map<TransitStopFacility, Set<String>> matchingStopsToZones = new HashMap<>();
    Map<String, Double> connectionTime = new HashMap<>();
    Map<String, Set<String>> zonesAndDemandLocations = new HashMap<>();
    Map<Id<TransitStopFacility>, Set<Double>> stopsDepatures = new HashMap<>();
    Map<String, Set<TransitRoute>> stopsRoutes = new HashMap<>();
    Map<String, Map<String, Double>> connectionsDemandZones = new HashMap<>();
    Map<String, Map<String, List<MyTransitPassangerRoute>>> connectionsLegsZones = new HashMap<>();
    List<List<PutSurveyEntry>> entries = Collections.synchronizedList(new ArrayList<>());
    Map<Id<TransitRoute>, Id<TransitLine>> routeToLine = new HashMap<>();
    Map<String, Integer> zoneLookUp = new HashMap<>();
    AtomicInteger zoneWithNoDemand = new AtomicInteger(0);
    Set<Id<TransitRoute>> doubleStop = new HashSet<>();

    public static void main(String[] args) {
        long startTime = System.nanoTime();
        DepartureRouter departureRouter = new DepartureRouter(0);
        departureRouter.distribution = 0;
        departureRouter.readAndPrepareDemand();
        departureRouter.calculateTreeForUniqueDepatureAtAllStations();
        departureRouter.writeOutput();
        System.out.println("It took: " + ((System.nanoTime() - startTime) / 1_000_000_000) + "s");
        System.out.println("Done");
    }

    private void calculationZones(Entry<String, Set<TransitStopFacility>> stringSetEntry) {
        long startTime = System.nanoTime();
        var raptor = new SwissRailRaptor(data, raptorParametersForPerson, routeSelector, defaultRaptorStopFinder, inVehicleCostCalculator, transferCostCalculator);
        Map<String, List<MyTransitPassangerRoute>> linesRouteMap = connectionsLegsZones.get(stringSetEntry.getKey());
        Map<String, Double> linesDemandMap = connectionsDemandZones.get(stringSetEntry.getKey());

        if (zonesAndDemandLocations.get(stringSetEntry.getKey()) == null) {
            System.out.println("Warning no demand in Zone: " + stringSetEntry.getKey());
            zoneWithNoDemand.incrementAndGet();
            return;
        }

        Map<Double, Map<Id<TransitStopFacility>, TravelInfo>> trees = new HashMap<>();
        for (TransitStopFacility startStop : stringSetEntry.getValue()) {
            for (Double depatureTime : stopsDepatures.get(startStop.getId())) {
                Map<Id<TransitStopFacility>, TravelInfo> tree = raptor.calcTree(startStop, depatureTime, params, null);
                trees.put(depatureTime, tree);
                calcInteger.incrementAndGet();
            }
        }

        int count = 0;
        for (String destinationZone : zonesAndDemandLocations.get(stringSetEntry.getKey())) {
            Map<TravelInfo, String> oneStopToStop = new HashMap<>();
            List<Double> pjtList = new ArrayList<>();
            Set<String> checkUniqueConnections = new HashSet<>();
            Set<TransitStopFacility> stops = matchingZoneToStops.get(destinationZone);
            Set<DirectConnection> directConnections = new HashSet<>();
            List<Double> directConnectionsPJT = new ArrayList<>();
            for (TransitStopFacility stop : stops) {
                for (Entry<Double, Map<Id<TransitStopFacility>, TravelInfo>> tree : trees.entrySet()) {
                    TravelInfo travelInfo = tree.getValue().get(stop.getId());
                    if (travelInfo == null) {
                        continue;
                    }
                    if (travelInfo.departureStop.equals(stop.getId())) {
                        continue;
                    }
                    double conTime = connectionTime.get(stringSetEntry.getKey() + travelInfo.departureStop) + connectionTime.get(destinationZone + stop.getId());

                    List<DirectConnection> tmpDirectConnections = checkDirectConnection(travelInfo, stop, tree.getKey(), stringSetEntry.getKey());
                    for (DirectConnection directConnection : tmpDirectConnections) {
                        directConnections.add(directConnection);
                        directConnectionsPJT.add(calculatePJT(directConnection, conTime));
                        lines.incrementAndGet();
                    }

                    if (travelInfo.transferCount > 0 || directConnectionsPJT.size() == 0) {
                        RaptorRoute raptorRoute = travelInfo.getRaptorRoute();
                        String line = generateLineInfo(raptorRoute);
                        if (checkUniqueConnections.add(line)) {
                            pjtList.add(calculatePJT(raptorRoute, conTime));
                            oneStopToStop.put(travelInfo, line);
                            lines.incrementAndGet();
                        }
                    }
                }
            }
            if (checkUniqueConnections.size() != pjtList.size()) {
                System.out.println("Warning the found connection and the calculateted pjt are not the same (tree)");
            }
            if (directConnections.size() != directConnectionsPJT.size()) {
                System.out.println("Warning the found connection and the calculateted pjt are not the same (direct)");
            }
            for (int i = 1; i < matrixNames; i++) {
                double[][] matrix = (double[][]) omx.getMatrix(String.valueOf(i)).getData();
                double[] row = matrix[zoneLookUp.get(stringSetEntry.getKey())];
                double demand = row[zoneLookUp.get(destinationZone)];
                if (demand != 0) {
                    if (demand < distribution) {
                        double optimalDepartureTime = (i - 1) * 600;
                        double timDiff = Double.POSITIVE_INFINITY;
                        TravelInfo closestTravelInfo = null;
                        DirectConnection closestDirectConnection = null;
                        for (Entry<TravelInfo, String> entry : oneStopToStop.entrySet()) {
                            double tmp = Math.abs(entry.getKey().ptDepartureTime - optimalDepartureTime);
                            if (tmp < timDiff) {
                                timDiff = tmp;
                                closestTravelInfo = entry.getKey();
                            }
                        }
                        for (DirectConnection directConnection : directConnections) {
                            double tmp = Math.abs(directConnection.boardingTime() - optimalDepartureTime);
                            if (tmp < timDiff) {
                                timDiff = tmp;
                                closestDirectConnection = directConnection;
                            }
                        }
                        if (closestTravelInfo == null && closestDirectConnection == null) {
                            continue;
                        }
                        String line;
                        List<MyTransitPassangerRoute> legs = new ArrayList<>();
                        if (closestDirectConnection == null) {
                            line = oneStopToStop.get(closestTravelInfo);
                            legs = RaptorUtils.convertRouteToLegs(closestTravelInfo.getRaptorRoute(), transferWalkinMargin).stream()
                                .filter(pe -> pe instanceof Leg leg && leg.getMode().equals("pt"))
                                .map(pe -> (TransitPassengerRoute) ((Leg) pe).getRoute())
                                .map(tPR -> new MyTransitPassangerRoute(tPR.getLineId(), tPR.getRouteId(), tPR.getAccessStopId(), tPR.getEgressStopId(), tPR.getBoardingTime().seconds())).toList();

                        } else {
                            line = generateLineInfo(closestDirectConnection);
                            legs.add(new MyTransitPassangerRoute(closestDirectConnection.lineId(), closestDirectConnection.transitRoute().getId(), closestDirectConnection.startId(),
                                closestDirectConnection.endId(), closestDirectConnection.boardingTime()));
                        }
                        if (linesDemandMap.containsKey(line)) {
                            double newDemand = linesDemandMap.get(line) + demand;
                            linesDemandMap.put(line, newDemand);
                        } else {
                            linesDemandMap.put(line, demand);
                            linesRouteMap.put(line, legs);
                        }
                    } else {
                        List<Double> utility = new ArrayList<>();
                        List<Double> utilityDirect = new ArrayList<>();
                        double optimalDepatureTime = (i - 1) * 600;
                        double totalUtility = 0;
                        int index = 0;
                        for (Entry<TravelInfo, String> entry : oneStopToStop.entrySet()) {
                            double actualDerpatureTime = entry.getKey().ptDepartureTime;
                            double timeDiffernce = Math.abs(optimalDepatureTime - actualDerpatureTime);
                            double actualPJT = pjtList.get(index++) + timeDiffernce * coefficientDeltaTime;
                            double tmpUtility = Math.pow(Math.E, -1.536 * ((Math.pow(actualPJT, 0.5) - 1) / 0.5)); //von visum
                            totalUtility += tmpUtility;
                            utility.add(tmpUtility);
                            count++;
                        }
                        index = 0;
                        for (DirectConnection directConnection : directConnections) {
                            double actualDerpatureTime = directConnection.boardingTime;
                            double timeDiffernce = Math.abs(optimalDepatureTime - actualDerpatureTime);
                            double actualPJT = directConnectionsPJT.get(index++) + timeDiffernce * coefficientDeltaTime;
                            double tmpUtility = Math.pow(Math.E, -1.536 * ((Math.pow(actualPJT, 0.5) - 1) / 0.5)); //von visum
                            totalUtility += tmpUtility;
                            utilityDirect.add(tmpUtility);
                            count++;
                        }
                        index = 0;
                        for (Entry<TravelInfo, String> entry : oneStopToStop.entrySet()) {
                            double realDemand = demand * (utility.get(index++) / totalUtility);
                            RaptorRoute raptorRoute = entry.getKey().getRaptorRoute();
                            List<MyTransitPassangerRoute> legs = RaptorUtils.convertRouteToLegs(raptorRoute, transferWalkinMargin).stream()
                                .filter(pe -> pe instanceof Leg leg && leg.getMode().equals("pt"))
                                .map(pe -> (TransitPassengerRoute) ((Leg) pe).getRoute())
                                .map(tPR -> new MyTransitPassangerRoute(tPR.getLineId(), tPR.getRouteId(), tPR.getAccessStopId(), tPR.getEgressStopId(), tPR.getBoardingTime().seconds())).toList();
                            String line = entry.getValue();
                            if (linesDemandMap.containsKey(line)) {
                                double newDemand = linesDemandMap.get(line) + realDemand;
                                linesDemandMap.put(line, newDemand);
                            } else {
                                linesDemandMap.put(line, realDemand);
                                linesRouteMap.put(line, legs);
                            }
                        }
                        index = 0;
                        for (DirectConnection directConnection : directConnections) {
                            double realDemand = demand * (utilityDirect.get(index++) / totalUtility);
                            String line = generateLineInfo(directConnection);
                            List<MyTransitPassangerRoute> legs = new ArrayList<>();
                            legs.add(new MyTransitPassangerRoute(directConnection.lineId(), directConnection.transitRoute().getId(), directConnection.startId(), directConnection.endId(),
                                directConnection.boardingTime()));
                            if (linesDemandMap.containsKey(line)) {
                                double newDemand = linesDemandMap.get(line) + realDemand;
                                linesDemandMap.put(line, newDemand);
                            } else {
                                linesDemandMap.put(line, realDemand);
                                linesRouteMap.put(line, legs);
                            }
                        }
                    }
                }
            }
        }
        System.out.println("Zone: " + stringSetEntry.getKey() + ";" + trees.size() + ";" + count + ";" + ((System.nanoTime() - startTime) / 1_000_000_000) + "s");
    }

    private List<DirectConnection> checkDirectConnection(TravelInfo travelInfo, TransitStopFacility endStop, double depatureTime, String zone) {
        List<DirectConnection> connections = new ArrayList<>();
        Set<TransitRoute> departureLines = stopsRoutes.get(zone + "_" + depatureTime);
        // alle Abfahrten auf direkte Verbindungen prüfen
        for (TransitRoute transitRoute : departureLines) {
            TransitStopFacility startStop = scenario.getTransitSchedule().getFacilities().get(travelInfo.departureStop);
            TransitRouteStop routeStartStop = transitRoute.getStop(startStop);
            TransitRouteStop routeEndStop = transitRoute.getStop(endStop);
            int stratStopId = transitRoute.getStops().indexOf(routeStartStop);
            int endStopId = transitRoute.getStops().indexOf(routeEndStop);
            // skip, wenn es keine direkte Verbindung gibt
            if (stratStopId == -1 || endStopId == -1) {
                continue;
            }
            // skip, wenn es die falsche Richtung ist
            if (stratStopId > endStopId) {
                if (doubleStop.contains(transitRoute.getId())) {
                   DirectConnection directConnection = directWithDoubleStop(startStop, endStop, transitRoute, depatureTime);
                   if (directConnection != null)  {
                       connections.add(directConnection);
                   }
                }
                continue;
            }
            // erzeugen und speichern der nötigen Informationen zur Weiterverarbeitung
            double travelTime = routeEndStop.getArrivalOffset().seconds() - routeStartStop.getDepartureOffset().seconds();
            // normalerweise sollte immer mit der Aknuftszeit gerechnet werden, da für die Berechnung der Abfahrtszeit die tatsächliche Abfahrtszeit genommen wurde muss hier auch die Abfahrtszeit genommen werden
            double depatureRouteTime = depatureTime - routeStartStop.getDepartureOffset().seconds();
            int stopsBetween = endStopId - stratStopId - 1;
            DirectConnection directConnection = new DirectConnection(routeToLine.get(transitRoute.getId()), transitRoute, depatureTime, startStop.getId(), endStop.getId(), travelTime, stopsBetween,
                depatureRouteTime);
            connections.add(directConnection);
            if (travelInfo.transferCount == 0 && travelInfo.ptTravelTime > travelTime && travelInfo.ptDepartureTime == depatureTime) {
                System.out.println("Error by direct connection: the travel time from the router should not be more than from own calculation for the same transit line");
            }
        }
        return connections;
    }

    private DirectConnection directWithDoubleStop(TransitStopFacility start, TransitStopFacility end, TransitRoute transitRoute, double boardingTime) {
        List<TransitRouteStop> stops = transitRoute.getStops();
        Id<TransitStopFacility> startId = start.getId();
        Id<TransitStopFacility> endId = end.getId();
        TransitRouteStop routeStart = null;
        TransitRouteStop routeEnd = null;
        boolean notFoundStart = true;
        int stopsBetween = 0;
        for (var stop : stops ) {
            if (notFoundStart) {
                if (stop.getStopFacility().equals(start)) {
                    routeStart = stop;
                    notFoundStart = false;
                }
            } else {
                if (stop.getStopFacility().equals(end)) {
                    routeEnd = stop;
                    break;
                }
                stopsBetween++;
            }
        }
        if (routeStart == null || routeEnd == null) {
            return null;
        }
        double travelTime = routeEnd.getArrivalOffset().seconds() - routeStart.getDepartureOffset().seconds();
        // normalerweise sollte immer mit der Aknuftszeit gerechnet werden, da für die Berechnung der Abfahrtszeit die tatsächliche Abfahrtszeit genommen wurde muss hier auch die Abfahrtszeit genommen werden
        double depatureRouteTime = boardingTime - routeStart.getDepartureOffset().seconds();
        return new DirectConnection(routeToLine.get(transitRoute.getId()), transitRoute, boardingTime, startId, endId, travelTime,stopsBetween,depatureRouteTime);
    }

    private String generateLineInfo(RaptorRoute raptorRoute) {
        StringBuilder line = new StringBuilder();
        int pathlegindex = 1;
        for (RoutePart routePart : raptorRoute.getParts()) {
            if (routePart.mode.equals("pt")) {
                TransitStopFacility toStop = routePart.toStop;
                TransitRouteStop routeToStop = routePart.route.getStop(toStop);
                double startTime = routePart.arrivalTime;
                double depatureRouteTime;
                if (routeToStop.getArrivalOffset().isUndefined() || doubleStop.contains(routePart.route.getId())) {
                    depatureRouteTime = startTime - calculatedepartureRouteTime(routePart.route, routePart.fromStop, routePart.toStop);

                } else {
                    depatureRouteTime = startTime - routeToStop.getArrivalOffset().seconds();
                }
                line.append(pathlegindex++).append(";")
                    .append(routePart.fromStop.getId()).append(";")
                    .append(routePart.toStop.getId()).append(";")
                    .append(routePart.route.getId().toString()).append(";")
                    .append(routePart.route.getAttributes().getAttribute(LINEROUTENAME)).append(";")
                    .append((int) depatureRouteTime).append(";");
            }
        }
        return line.toString();
    }

    private double calculatedepartureRouteTime(TransitRoute transitRoute, TransitStopFacility start, TransitStopFacility end) {
        List<TransitRouteStop> stops = transitRoute.getStops();
        boolean notFoundStart = true;
        for (var stop : stops ) {
            if (notFoundStart) {
                if (stop.getStopFacility().equals(start)) {
                    notFoundStart = false;
                }
            } else {
                if (stop.getStopFacility().equals(end)) {
                    return stop.getArrivalOffset().seconds();
                }
            }
        }
        return 0;
    }

    private String generateLineInfo(DirectConnection directConnection) {
        return 1 + ";"
            + directConnection.startId() + ";"
            + directConnection.endId() + ";"
            + directConnection.transitRoute().getId() + ";"
            + directConnection.transitRoute().getAttributes().getAttribute(LINEROUTENAME) + ";"
            + (int) directConnection.depatureRouteTime() + ";";
    }

    private void calculateTreeForUniqueDepatureAtAllStations() {
        long startTime = System.nanoTime();
        //matchingZoneToStops.entrySet().stream().filter(e -> e.getKey().equals("763")).forEach(this::calculationZones);
        matchingZoneToStops.entrySet().stream().parallel().forEach(this::calculationZones);
        System.out.println("Lines: " + lines.get());
        //System.out.println("Lines (Map): " + connectionsDemand.size());
        System.out.println("Warining: " + zoneWithNoDemand.get() + " zones with out demand");
        System.out.println("Calculating trees and distribute demand took: " + ((System.nanoTime() - startTime) / 1_000_000_000) + "s");
    }

    public DepartureRouter(double transeferPenalty) {
        new MatsimNetworkReader(scenario.getNetwork()).readFile(netwoekFile);
        new TransitScheduleReader(scenario).readFile(schedualFile);

        RaptorStaticConfig raptorStaticConfig = RaptorUtils.createStaticConfig(scenario.getConfig());
        raptorStaticConfig.setOptimization(RaptorStaticConfig.RaptorOptimization.OneToAllRouting);

        this.raptorParametersForPerson = new DefaultRaptorParametersForPerson(scenario.getConfig());
        this.data = SwissRailRaptorData.create(scenario.getTransitSchedule(), null, raptorStaticConfig, scenario.getNetwork(), null);

        this.transferWalkinMargin = ConfigUtils.addOrGetModule(scenario.getConfig(), SwissRailRaptorConfigGroup.class).getTransferWalkMargin();
        this.params.setTransferPenaltyFixCostPerTransfer(transeferPenalty);
    }

    private void writeOutput() {
        long startTime = System.nanoTime();
        omx = null;
        matchingZoneToStops.clear();
        matchingStopsToZones.clear();
        zonesAndDemandLocations.clear();
        stopsDepatures.clear();
        writeRoute();
        connectionsDemandZones.keySet().forEach(this::prepareOutput);
        PutSurveyWriter.writePutSurvey(visum, entries);
        System.out.println("Write output took: " + ((System.nanoTime() - startTime) / 1_000_000_000) + "s");
    }

    private void writeRoute() {
        int count = 0;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvLines))) {
            for (Map<String, Double> linesdemand : connectionsDemandZones.values()) {
                for (Entry<String, Double> entry : linesdemand.entrySet()) {
                    writer.write(entry.getKey() + entry.getValue());
                    count++;
                    writer.newLine();
                    writer.flush();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Count : " + count);
    }

    private double calculatePJT(RaptorRoute route, Double conTime) {
        double inVehicleTime = 0; // Reisezeit im Fahrzeug
        //double puTAuxRideTime = 0; // Anschlusszeit Nahverkehr, hier immer 0
        double accessEgressTime = conTime; // Anbindungszeit aus Visum
        double walkTime = 0; // minimale Umstiegzeit
        double transferWaitTime = 0; // Umstiegzeit - minimale Umstiegszeit
        double numberOfTransfers = route.getNumberOfTransfers(); // PT legs
        double extendedImpedance = 0; // jederhalt des Zuges

        double coefficientAccessEgressTime = 2.94;
        double coefficientWalkTime = 2.25;
        double coefficientTransferWaitTime = 1.13;
        double coefficientExtendedImpedance = 58;

        boolean firstPTLeg = false;
        double time = 0;

        for (RoutePart part : route.getParts()) {
            if (part.mode.equals("pt")) {
                inVehicleTime += part.arrivalTime - part.boardingTime;
                boolean isBetweenStop = false;
                for (TransitRouteStop stop : part.route.getStops()) {
                    if (part.toStop.getId().equals(stop.getStopFacility().getId())) {
                        break;
                    }
                    if (isBetweenStop) {
                        extendedImpedance += coefficientExtendedImpedance;
                    }
                    if (part.fromStop.getId().equals(stop.getStopFacility().getId())) {
                        isBetweenStop = true;
                    }
                }
                if (firstPTLeg) {
                    double minimalTransferTime = scenario.getTransitSchedule().getMinimalTransferTimes().get(part.fromStop.getId(), part.toStop.getId());
                    if (!Double.isNaN(minimalTransferTime)) {
                        walkTime += minimalTransferTime;
                        transferWaitTime += part.depTime - time - minimalTransferTime;
                    }
                }
                time = part.arrivalTime;
                firstPTLeg = true;
            }
        }
        return inVehicleTime / 60 + (accessEgressTime / 60) * coefficientAccessEgressTime + (walkTime / 60) * coefficientWalkTime
            + (transferWaitTime / 60) * coefficientTransferWaitTime
            + numberOfTransfers + extendedImpedance / 60;
    }

    private double calculatePJT(DirectConnection directConnection, Double conTime) {
        double inVehicleTime = directConnection.travelTime; // Reisezeit im Fahrzeug
        //double puTAuxRideTime = 0; // Anschlusszeit Nahverkehr, hier immer 0
        double accessEgressTime = conTime; // Anbindungszeit aus Visum
        //double walkTime = 0; // minimale Umstiegzeit, bei direkt immer 0
        //double transferWaitTime = 0; // Umstiegzeit - minimale Umstiegszeit, bei direkt immer 0
        //double numberOfTransfers = 0; // PT legs, bei direkt immer 0
        double extendedImpedance = directConnection.soptsBetween; // jederhalt des Zuges

        double coefficientAccessEgressTime = 2.94;
        //double coefficientWalkTime = 2.25;
        //double coefficientTransferWaitTime = 1.13;
        double coefficientExtendedImpedance = 58;

        return inVehicleTime / 60 + (accessEgressTime / 60) * coefficientAccessEgressTime + (extendedImpedance * coefficientExtendedImpedance) / 60;
    }

    private void readAndPrepareDemand() {

        long startTime = System.nanoTime();
        omx = new OmxFile(omxFile);
        omx.openReadOnly();
        omx.summary();
        matrixNames = omx.getMatrixNames().size();
        System.out.println("Reading OMX file took: " + ((System.nanoTime() - startTime) / 1_000_000_000) + "s");

        startTime = System.nanoTime();
        Set<String> stopsNotFound = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(assignmentFile))) {
            List<String> header = List.of(reader.readLine().split(";"));// 0 is the zone, as in omx file and 1 is the stop point number, id in transit file
            String line;
            Map<String, List<TransitStopFacility>> stopToArea = new HashMap<>();
            for (TransitStopFacility stop : scenario.getTransitSchedule().getFacilities().values()) {
                String stopNo = stop.getAttributes().getAttribute(STOP_NO).toString();
                List<TransitStopFacility> tmp = stopToArea.getOrDefault(stopNo, new ArrayList<>());
                tmp.add(stop);
                stopToArea.put(stopNo, tmp);
            }
            while ((line = reader.readLine()) != null) {
                String[] splitLine = line.split(";");
                if (stopToArea.get(splitLine[1]) == null) {
                    stopsNotFound.add(splitLine[1]);
                    continue;
                }
                for (TransitStopFacility stop : stopToArea.get(splitLine[1])) {
                    if (matchingZoneToStops.containsKey(splitLine[0])) {
                        matchingZoneToStops.get(splitLine[0]).add(stop);
                    } else {
                        matchingZoneToStops.put(splitLine[0], new HashSet<>());
                        matchingZoneToStops.get(splitLine[0]).add(stop);
                    }
                    if (matchingStopsToZones.containsKey(stop)) {
                        matchingStopsToZones.get(stop).add(splitLine[0]);
                    } else {
                        matchingStopsToZones.put(stop, new HashSet<>());
                        matchingStopsToZones.get(stop).add(splitLine[0]);
                    }
                    String key = splitLine[0] + stop.getId();
                    if (!connectionTime.containsKey(key)) {
                        connectionTime.put(key, Double.parseDouble(splitLine[2]) * 60 + Double.parseDouble(splitLine[3]));
                    }
                    if (!connectionsDemandZones.containsKey(splitLine[0])) {
                        connectionsDemandZones.put(splitLine[0], new HashMap<>());
                        Map<String, List<MyTransitPassangerRoute>> map = new HashMap<>();
                        connectionsLegsZones.put(splitLine[0], map);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Warning: " + stopsNotFound.size() + " stop area not found in matsim");

        Set<Integer> zonesWithNoStops = new HashSet<>();
        int[] lookUp = (int[]) omx.getLookup("NO").getLookup();

        int count = 0;
        for (int i : lookUp) {
            zoneLookUp.put(String.valueOf(i), count++);
        }

        for (int i = 1; i < matrixNames; i++) {
            double[][] matrix = (double[][]) omx.getMatrix(String.valueOf(i)).getData();
            for (int x = 0; x < matrix.length; x++) {
                for (int y = 0; y < matrix.length; y++) {
                    if (matrix[x][y] != 0) {
                        Set<TransitStopFacility> startList = matchingZoneToStops.get(String.valueOf(lookUp[x]));
                        Set<TransitStopFacility> endList = matchingZoneToStops.get(String.valueOf(lookUp[y]));
                        if (startList == null) {
                            zonesWithNoStops.add(lookUp[x]);
                            continue;
                        }
                        if (endList == null) {
                            zonesWithNoStops.add(lookUp[y]);
                            continue;
                        }
                        if (zonesAndDemandLocations.containsKey(String.valueOf(lookUp[x]))) {
                            zonesAndDemandLocations.get(String.valueOf(lookUp[x])).add(String.valueOf(lookUp[y]));
                        } else {
                            zonesAndDemandLocations.put(String.valueOf(lookUp[x]), new HashSet<>());
                            zonesAndDemandLocations.get(String.valueOf(lookUp[x])).add(String.valueOf(lookUp[y]));
                        }
                    }
                }
            }
        }

        scenario.getTransitSchedule().getTransitLines().values().forEach(this::depatureForStops);

        System.out.println("Warning: " + zonesWithNoStops.size() + " zones with no stops");
        System.out.println("Matching zones and stops took: " + ((System.nanoTime() - startTime) / 1_000_000_000) + "s");
    }

    private void depatureForStops(TransitLine transitLine) {
        for (var routes : transitLine.getRoutes().values()) {
            routeToLine.put(routes.getId(), transitLine.getId());
            var stops = routes.getStops();
            checkDoubleStops(stops, routes.getId());
            var depatures = routes.getDepartures();
            for (var depature : depatures.values()) {
                var startDepatureTime = depature.getDepartureTime();
                for (var stop : stops) {
                    // sollte hier auf die arrivalTime gewechselt werden muss auch die Methode checkDirectConnection angepasst werden
                    var stopDepatureTime = startDepatureTime + stop.getDepartureOffset().seconds();
                    var stopFacility = stop.getStopFacility();
                    if (stopsDepatures.containsKey(stopFacility.getId())) {
                        stopsDepatures.get(stopFacility.getId()).add(stopDepatureTime);
                    } else {
                        stopsDepatures.put(stopFacility.getId(), new HashSet<>());
                        stopsDepatures.get(stopFacility.getId()).add(stopDepatureTime);
                    }
                    depatureInteger.incrementAndGet();
                    if (!matchingStopsToZones.containsKey(stopFacility)) {
                        continue;
                    }
                    for (String zone : matchingStopsToZones.get(stopFacility)) {
                        String keyId = zone + "_" + stopDepatureTime;
                        if (stopsRoutes.containsKey(keyId)) {
                            stopsRoutes.get(keyId).add(routes);
                        } else {
                            stopsRoutes.put(keyId, new HashSet<>());
                            stopsRoutes.get(keyId).add(routes);
                        }
                    }
                }
            }
        }
    }

    private void checkDoubleStops(List<TransitRouteStop> stops, Id<TransitRoute> id) {
        if (stops.get(0).getStopFacility().getId().equals(stops.get(stops.size() - 1).getStopFacility().getId())) {
            doubleStop.add(id);
            return;
        }
        List<Id<TransitStopFacility>> unique = new ArrayList<>();
        for (TransitRouteStop transitRouteStop : stops) {
            if (unique.contains(transitRouteStop.getStopFacility().getId())) {
                doubleStop.add(id);
            } else {
                unique.add(transitRouteStop.getStopFacility().getId());
            }
        }
    }

    private void prepareOutput(String key) {
        List<PutSurveyEntry> putSurveyEntries = new ArrayList<>();
        for (Entry<String, List<MyTransitPassangerRoute>> entry : connectionsLegsZones.get(key).entrySet()) {
            String pathid = Integer.toString(atomicInteger.incrementAndGet());
            AtomicInteger legid = new AtomicInteger(0);
            for (MyTransitPassangerRoute myTransitPassangerRoute : entry.getValue()) {
                Id<TransitLine> transitLineId = myTransitPassangerRoute.lineId();
                Id<TransitRoute> transitRouteId = myTransitPassangerRoute.routeId();
                TransitLine line = scenario.getTransitSchedule().getTransitLines().get(transitLineId);
                TransitRoute transitRoute = line.getRoutes().get(transitRouteId);
                String fromstop = String.valueOf(scenario.getTransitSchedule().getFacilities().get(myTransitPassangerRoute.fromStopId()).getAttributes().getAttribute(STOP_NO));
                String tostop = String.valueOf(scenario.getTransitSchedule().getFacilities().get(myTransitPassangerRoute.toStopId).getAttributes().getAttribute(STOP_NO));

                String vsyscode = String.valueOf(transitRoute.getAttributes().getAttribute(TSYS_CODE));
                String linname = String.valueOf(transitRoute.getAttributes().getAttribute(TRANSITLINE));
                String linroutename = String.valueOf(transitRoute.getAttributes().getAttribute(LINEROUTENAME));
                String richtungscode = String.valueOf(transitRoute.getAttributes().getAttribute(DIRECTION_CODE));

                String fzprofilname = String.valueOf(transitRoute.getAttributes().getAttribute(FZPNAME));

                String teilwegkennung = legid.getAndIncrement() > 0 ? "N" : "E";
                String einhstabfahrtstag = getDayIndex(myTransitPassangerRoute.boardingTime());
                String einhstabfahrtszeit = getTime(myTransitPassangerRoute.boardingTime());
                putSurveyEntries.add(new PutSurveyEntry(pathid, String.valueOf(legid), fromstop, tostop, vsyscode, linname, linroutename, richtungscode,
                    fzprofilname, teilwegkennung, fromstop, einhstabfahrtstag, einhstabfahrtszeit, connectionsDemandZones.get(key).get(entry.getKey()), "regular", "", ""));
            }
        }
        if (!putSurveyEntries.isEmpty()) {
            entries.add(putSurveyEntries);
        }
    }

    record DirectConnection(Id<TransitLine> lineId, TransitRoute transitRoute, double boardingTime, Id<TransitStopFacility> startId, Id<TransitStopFacility> endId, double travelTime, int soptsBetween,
                            double depatureRouteTime) {}

    record MyTransitPassangerRoute(Id<TransitLine> lineId, Id<TransitRoute> routeId, Id<TransitStopFacility> fromStopId, Id<TransitStopFacility> toStopId, Double boardingTime) {}
}
