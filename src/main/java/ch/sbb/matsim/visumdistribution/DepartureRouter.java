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
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
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
import org.matsim.api.core.v01.population.PlanElement;
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
    final static String assignmentFile = "Z:/99_Playgrounds/MD/Umlegung2/Visum/ZoneToNode.csv";
    final static String visum = "Z:/99_Playgrounds/MD/Umlegung2/routesDepatureVisum.csv";
    Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
    SwissRailRaptorData data;
    RaptorParametersForPerson raptorParametersForPerson;
    RaptorRouteSelector routeSelector = new LeastCostRaptorRouteSelector();
    RaptorInVehicleCostCalculator inVehicleCostCalculator = new DefaultRaptorInVehicleCostCalculator();
    RaptorTransferCostCalculator transferCostCalculator = new DefaultRaptorTransferCostCalculator();
    DefaultRaptorStopFinder defaultRaptorStopFinder = new DefaultRaptorStopFinder(new DefaultRaptorIntermodalAccessEgress(), null);
    static AtomicInteger atomicInteger = new AtomicInteger(0);
    final double coefficientDeltaTime = 1.85 / 60; //in sekunden umgerechnet

    RaptorParameters params = RaptorUtils.createParameters(scenario.getConfig());

    OmxFile omx;
    Set<String> alreadyCalculatedSet = new HashSet<>();
    Map<Id<TransitStopFacility>, List<Map<Id<TransitStopFacility>, TravelInfo>>> stationsTreeMap = new HashMap<>();
    Map<String, Set<TransitStopFacility>> matchingZoneToStops = new HashMap<>();
    Map<TransitStopFacility, Set<String>> matchingStopsToZones = new HashMap<>();
    Map<Id<TransitStopFacility>, Set<TransitStopFacility>> stopsAndDemandLocations = new HashMap<>();
    Map<String, List<TravelInfo>> connectionTravelInfo = new HashMap<>();
    Map<TransitStopFacility, Set<Double>> stopsDepatures = new HashMap<>();
    Map<String, Double> connectionsDemand = new HashMap<>();
    Map<String, List<? extends PlanElement>> connectionsLegs = new HashMap<>();
    List<List<PutSurveyEntry>> entries = new ArrayList<>();
    Map<String, Integer> zoneLookUp = new HashMap<>();

    public static void main(String[] args) {
        long startTime = System.nanoTime();
        DepartureRouter departureRouter = new DepartureRouter();
        departureRouter.readAndPrepareDemand();
        departureRouter.calculateTreeForUniqueDepatureAtAllStations();
        departureRouter.writeOutput();
        System.out.println("It took: " + ((System.nanoTime() - startTime) / 1_000_000_000) + "s");
        System.out.println("Done");
    }

    private void writeOutput() {
        long startTime = System.nanoTime();
        connectionsDemand.keySet().stream().parallel().forEach(this::prepareOutput);
        PutSurveyWriter.writePutSurvey(visum, entries);
        System.out.println("Write output took: " + ((System.nanoTime() - startTime) / 1_000_000_000) + "s");
    }

    private void prepareOutput(String key) {
        String pathid = Integer.toString(atomicInteger.incrementAndGet());
        AtomicInteger legid = new AtomicInteger(0);
        List<PutSurveyEntry> putSurveyEntries = new ArrayList<>();
        for (PlanElement pe : connectionsLegs.get(key)) {
            Leg leg = (Leg) pe;
            if (leg.getMode().equals("pt")) {
                TransitPassengerRoute r = (TransitPassengerRoute) leg.getRoute();
                TransitLine line = scenario.getTransitSchedule().getTransitLines().get(r.getLineId());
                TransitRoute transitRoute = line.getRoutes().get(r.getRouteId());
                String fromstop = String.valueOf(scenario.getTransitSchedule().getFacilities().get(r.getAccessStopId()).getAttributes().getAttribute(STOP_NO));
                String tostop = String.valueOf(scenario.getTransitSchedule().getFacilities().get(r.getEgressStopId()).getAttributes().getAttribute(STOP_NO));

                String vsyscode = String.valueOf(transitRoute.getAttributes().getAttribute(TSYS_CODE));
                String linname = String.valueOf(transitRoute.getAttributes().getAttribute(TRANSITLINE));
                String linroutename = String.valueOf(transitRoute.getAttributes().getAttribute(LINEROUTENAME));
                String richtungscode = String.valueOf(transitRoute.getAttributes().getAttribute(DIRECTION_CODE));

                String fzprofilname = String.valueOf(transitRoute.getAttributes().getAttribute(FZPNAME));

                String teilwegkennung = legid.getAndIncrement() > 0 ? "N" : "E";
                String einhstabfahrtstag = getDayIndex(r.getBoardingTime().seconds());
                String einhstabfahrtszeit = getTime(r.getBoardingTime().seconds());
                putSurveyEntries.add(new PutSurveyEntry(pathid, String.valueOf(legid), fromstop, tostop, vsyscode, linname, linroutename, richtungscode,
                    fzprofilname, teilwegkennung, fromstop, einhstabfahrtstag, einhstabfahrtszeit, connectionsDemand.get(key), "regular", "", ""));
            }
        }
        if (!putSurveyEntries.isEmpty()) {
            entries.add(putSurveyEntries);
        }
    }

    public DepartureRouter() {
        new MatsimNetworkReader(scenario.getNetwork()).readFile(netwoekFile);
        new TransitScheduleReader(scenario).readFile(schedualFile);

        RaptorStaticConfig raptorStaticConfig = RaptorUtils.createStaticConfig(scenario.getConfig());
        raptorStaticConfig.setOptimization(RaptorStaticConfig.RaptorOptimization.OneToAllRouting);

        this.raptorParametersForPerson = new DefaultRaptorParametersForPerson(scenario.getConfig());
        this.data = SwissRailRaptorData.create(scenario.getTransitSchedule(), null, raptorStaticConfig, scenario.getNetwork(), null);
    }

    private void calculateTreeForUniqueDepatureAtAllStations() {
        long startTime = System.nanoTime();
        matchingZoneToStops.entrySet().stream().parallel().forEach(this::calculationZones);
        //scenario.getTransitSchedule().getTransitLines().values().stream().parallel().forEach(this::calculation);
        System.out.println("Calculating trees and distribute demand took: " + ((System.nanoTime() - startTime) / 1_000_000_000) + "s");
    }

    private void calculationZones(Entry<String, Set<TransitStopFacility>> stringSetEntry) {
        long startTime = System.nanoTime();
        var raptor = new SwissRailRaptor(data, raptorParametersForPerson, routeSelector, defaultRaptorStopFinder, inVehicleCostCalculator, transferCostCalculator);

        List<Map<Id<TransitStopFacility>, TravelInfo>> trees = new ArrayList<>();

        for (TransitStopFacility startStop : stringSetEntry.getValue()) {
            for (Double depatureTime : stopsDepatures.get(startStop)) {
                Map<Id<TransitStopFacility>, TravelInfo> tree = raptor.calcTree(startStop, depatureTime, params, null);
                trees.add(tree);
            }
        }
        int count = 0;
        for (TransitStopFacility startStop : stringSetEntry.getValue()) {
            if (stopsAndDemandLocations.get(startStop.getId()) == null) {
                continue;
            }
            for (TransitStopFacility endStop : stopsAndDemandLocations.get(startStop.getId())) {
                List<TravelInfo> oneStopToStop = new ArrayList<>();
                List<Double> pjtList = new ArrayList<>();
                Set<Double> checkUniqueConnections = new HashSet<>();
                for (Map<Id<TransitStopFacility>, TravelInfo> tree : trees) {
                    TravelInfo travelInfo = tree.get(endStop.getId());
                    if (travelInfo != null) {
                        if (checkUniqueConnections.add(travelInfo.ptDepartureTime + travelInfo.ptTravelTime + travelInfo.travelCost)) {
                            double pjt = calculatePJT(travelInfo.getRaptorRoute());
                            pjtList.add(pjt);
                            oneStopToStop.add(travelInfo);
                        }
                    }
                }
                for (int i = 1; i < omx.getMatrixNames().size(); i++) {
                    double[][] matrix = (double[][]) omx.getMatrix(String.valueOf(i)).getData();
                    double[] row = matrix[zoneLookUp.get(stringSetEntry.getKey())];
                    Set<String> endZones = matchingStopsToZones.get(endStop);
                    for(String endZone : endZones) {
                        double demand = row[zoneLookUp.get(endZone)]/stringSetEntry.getValue().size();
                        if (demand != 0) {
                            count++;
                            List<Double> utility = new ArrayList<>();
                            double optimalDepatureTime = (i - 1) * 600;
                            double totalUtility = 0;
                            for (TravelInfo travelInfo : oneStopToStop) {
                                double actualDepatureTime = travelInfo.ptDepartureTime;
                                double timeDiffernce = Math.abs(optimalDepatureTime - actualDepatureTime);
                                double actualPJT = pjtList.get(oneStopToStop.indexOf(travelInfo)) + timeDiffernce * coefficientDeltaTime;
                                double tmpUtility = Math.pow(Math.E, -1.536 * ((Math.pow((actualPJT / 60), 0.5) - 1) / 0.5)); //von visum
                                totalUtility += tmpUtility;
                                utility.add(tmpUtility);
                            }
                            for (TravelInfo travelInfo : oneStopToStop) {
                                double realDemand = demand * (utility.get(oneStopToStop.indexOf(travelInfo)) / totalUtility);
                                String key = "" + travelInfo.transferCount + travelInfo.travelCost + travelInfo.ptTravelTime + travelInfo.departureStop.toString() + travelInfo.ptArrivalTime;
                                var legs = RaptorUtils.convertRouteToLegs(travelInfo.getRaptorRoute(), ConfigUtils.addOrGetModule(scenario.getConfig(), SwissRailRaptorConfigGroup.class).getTransferWalkMargin());
                                if (connectionsDemand.containsKey(key)) {
                                    addDemandContains(realDemand, key);
                                } else {
                                    addDemandNew(realDemand, key, legs);
                                }

                                //addDemand(travelInfo, realDemand);
                            }
                        }
                    }
                    /*for (int y = 0; y < matrix.length; y++) {
                        double demand = row[y] / stringSetEntry.getValue().size();
                        if (demand != 0) {
                            count++;
                            List<Double> utility = new ArrayList<>();
                            double optimalDepatureTime = (i - 1) * 600;
                            double totalUtility = 0;
                            for (TravelInfo travelInfo : oneStopToStop) {
                                double actualDepatureTime = travelInfo.ptDepartureTime;
                                double timeDiffernce = Math.abs(optimalDepatureTime - actualDepatureTime);
                                double actualPJT = pjtList.get(oneStopToStop.indexOf(travelInfo)) + timeDiffernce * coefficientDeltaTime;
                                double tmpUtility = Math.pow(Math.E, -1.536 * ((Math.pow((actualPJT / 60), 0.5) - 1) / 0.5)); //von visum
                                totalUtility += tmpUtility;
                                utility.add(tmpUtility);
                            }
                            for (TravelInfo travelInfo : oneStopToStop) {
                                double realDemand = demand * (utility.get(oneStopToStop.indexOf(travelInfo)) / totalUtility);
                                String key = "" + travelInfo.transferCount + travelInfo.travelCost + travelInfo.ptTravelTime;
                                //addDemand(travelInfo, realDemand);
                            }
                        }
                    } */
                }
            }
        }
        System.out.println("Zone: " + stringSetEntry.getKey() + ";" + trees.size() + ";" + count + ";" + ((System.nanoTime() - startTime) / 1_000_000_000) + "s");
    }

    private synchronized void addDemandNew(double realDemand, String key, List<? extends PlanElement> legs) {
        connectionsDemand.put(key, realDemand);
        connectionsLegs.put(key, legs);
    }

    private synchronized void addDemandContains(double realDemand, String key) {
        double demand = connectionsDemand.get(key);
        connectionsDemand.put(key, demand + realDemand);
    }

    private synchronized void addDemand(TravelInfo travelInfo, double realDemand) {
        String key = "" + travelInfo.transferCount + travelInfo.travelCost + travelInfo.ptTravelTime;
        if (connectionsDemand.containsKey(key)) {
            double demand = connectionsDemand.get(key);
            connectionsDemand.put(key, demand + realDemand);
        } else {
            connectionsDemand.put(key, realDemand);
            connectionsLegs.put(key,
                RaptorUtils.convertRouteToLegs(travelInfo.getRaptorRoute(), ConfigUtils.addOrGetModule(scenario.getConfig(), SwissRailRaptorConfigGroup.class).getTransferWalkMargin()));
        }
    }

    private double calculatePJT(RaptorRoute route) {
        double inVehicleTime = 0; // Reisezeit im Fahrzeug
        double puTAuxRideTime = 0; // Anschlusszeit Nahverkehr, hier immer 0
        double accessTime = 0; // hier immer 0
        double egressTime = 0; // hier imme 0
        double walkTime = 0; // minimale Umstiegzeit
        double transferWaitTime = 0; // Umstiegzeit - minimale Umstiegszeit
        double numberOfTransfers = route.getNumberOfTransfers(); // PT legs
        double extendedImpedance = 0; // jederhalt des Zuges

        double coefficientAccessTime = 2.94 / 60;
        double coefficientEgressTime = 2.94 / 60;
        double coefficientWalkTime = 2.25 / 60;
        double coefficientTransferWaitTime = 1.13 / 60;
        double coefficientExtendedImpedance = 58;

        boolean firstPTLeg = false;
        double time = 0;

        for (RoutePart part : route.getParts()) {
            if (part.mode.equals("pt")) {
                inVehicleTime += part.arrivalTime - part.boardingTime;
                boolean isBetweenStop = false;
                for (TransitRouteStop stop : part.route.getStops()) {
                    if (isBetweenStop) {
                        extendedImpedance += coefficientExtendedImpedance;
                    }
                    if (part.fromStop.getId().equals(stop.getStopFacility().getId())) {
                        isBetweenStop = true;
                    }
                    if (part.toStop.getId().equals(stop.getStopFacility().getId())) {
                        break;
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
        return inVehicleTime + puTAuxRideTime + accessTime * coefficientAccessTime + egressTime * coefficientEgressTime + walkTime * coefficientWalkTime
            + transferWaitTime * coefficientTransferWaitTime
            + numberOfTransfers * 60 + extendedImpedance;
    }

    private void depatureForStops(TransitLine transitLine) {
        for (var routes : transitLine.getRoutes().values()) {
            var stops = routes.getStops();
            var depatures = routes.getDepartures();
            for (var depature : depatures.values()) {
                var startDepatureTime = depature.getDepartureTime();
                for (var stop : stops) {
                    var stopDepatureTime = startDepatureTime;
                    if (stop.getArrivalOffset().isDefined()) {
                        stopDepatureTime += stop.getArrivalOffset().seconds();
                    }
                    if (stopsDepatures.containsKey(stop.getStopFacility())) {
                        stopsDepatures.get(stop.getStopFacility()).add(stopDepatureTime);
                    } else {
                        stopsDepatures.put(stop.getStopFacility(), new HashSet<>());
                        stopsDepatures.get(stop.getStopFacility()).add(stopDepatureTime);
                    }
                }
            }
        }
    }

    /*private void calculation(TransitLine transitLine) {
        long startTime = System.nanoTime();
        var raptor = new SwissRailRaptor(data, raptorParametersForPerson, routeSelector, defaultRaptorStopFinder, inVehicleCostCalculator, transferCostCalculator);
        for (var routes : transitLine.getRoutes().values()) {
            var stops = routes.getStops();
            var depatures = routes.getDepartures();
            for (var depature : depatures.values()) {
                var startDepatureTime = depature.getDepartureTime();
                for (var stop : stops) {
                    var stopDepatureTime = startDepatureTime;
                    if (stop.getArrivalOffset().isDefined()) {
                        stopDepatureTime += stop.getArrivalOffset().seconds();
                    }
                    var alreadyCalculated = stop.getStopFacility().getId().toString() + "_" + startDepatureTime;
                    if (alreadyCalculatedSet.add(alreadyCalculated)) {
                        var tree = raptor.calcTree(stop.getStopFacility(), stopDepatureTime, (Person) null, null);
                        var demandList = stopsAndDemandLocations.get(stop.getStopFacility().getId());
                        for (TransitStopFacility transitStopFacility : demandList) {
                            if (connectionTravelInfo.containsKey(stop.getStopFacility().getId() + "_" + transitStopFacility.getId())) {
                                connectionTravelInfo.get(stop.getStopFacility().getId() + "_" + transitStopFacility.getId()).add(tree.get(transitStopFacility.getId()));
                            } else {
                                connectionTravelInfo.put(stop.getStopFacility().getId() + "_" + transitStopFacility.getId(), new ArrayList<>());
                                connectionTravelInfo.get(stop.getStopFacility().getId() + "_" + transitStopFacility.getId()).add(tree.get(transitStopFacility.getId()));
                            }
                        }
                    }
                }
            }
        }
        System.out.println("Line: " + transitLine.getId() + "; " + ((System.nanoTime() - startTime) / 1_000_000_000) + "s");
    }
     */

    private void readAndPrepareDemand() {

        long startTime = System.nanoTime();
        omx = new OmxFile(omxFile);
        omx.openReadOnly();
        omx.summary();
        System.out.println("Reading OMX file took: " + ((System.nanoTime() - startTime) / 1_000_000_000) + "s");

        startTime = System.nanoTime();
        Set<String> stopsNotFound = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(assignmentFile))) {
            List<String> header = List.of(reader.readLine().split(";"));// 0 is the zone, as in omx file and 1 is the stop point number, id in transit file
            String line;
            while ((line = reader.readLine()) != null) {
                String[] splitLine = line.split(";");
                TransitStopFacility stop = scenario.getTransitSchedule().getFacilities().get(Id.create(splitLine[1], TransitStopFacility.class));
                if (stop == null) {
                    stopsNotFound.add(splitLine[1]);
                    continue;
                }
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
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Warning: " + stopsNotFound.size() + " stops not found in matsim");

        Set<Integer> zonesWithNoStops = new HashSet<>();
        int[] lookUp = (int[]) omx.getLookup("NO").getLookup();

        int count = 0;
        for (int i : lookUp) {
            zoneLookUp.put(String.valueOf(i), count++);
        }

        for (int i = 1; i < omx.getMatrixNames().size(); i++) {
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
                        for (TransitStopFacility startStop : startList) {
                            if (stopsAndDemandLocations.containsKey(startStop.getId())) {
                                stopsAndDemandLocations.get(startStop.getId()).addAll(endList);
                            } else {
                                stopsAndDemandLocations.put(startStop.getId(), new HashSet<>());
                                stopsAndDemandLocations.get(startStop.getId()).addAll(endList);
                            }
                        }
                    }
                }
            }
        }
        scenario.getTransitSchedule().getTransitLines().values().forEach(this::depatureForStops);

        System.out.println("Warning: " + zonesWithNoStops.size() + " zones with no stops");
        System.out.println("Matching zones and stops took: " + ((System.nanoTime() - startTime) / 1_000_000_000) + "s");
    }

}
