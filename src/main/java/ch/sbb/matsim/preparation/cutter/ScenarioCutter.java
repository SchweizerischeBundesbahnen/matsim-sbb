package ch.sbb.matsim.preparation.cutter;

import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.csv.CSVWriter;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.*;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.network.NetworkChangeEvent;
import org.matsim.core.network.NetworkChangeEvent.ChangeType;
import org.matsim.core.network.NetworkChangeEvent.ChangeValue;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.MultimodalNetworkCleaner;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkChangeEventsWriter;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.FacilitiesWriter;
import org.matsim.facilities.MatsimFacilitiesReader;
import org.matsim.pt.routes.ExperimentalTransitRoute;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.utils.objectattributes.ObjectAttributesUtils;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;
import org.matsim.utils.objectattributes.attributable.AttributesUtils;
import org.matsim.vehicles.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Code to cut out a smaller area from a bigger area in a scenario.
 *
 * @author mrieser
 */
public class ScenarioCutter {

    private final static Logger log = Logger.getLogger(ScenarioCutter.class);
    public static final String CUT_ATTRIBUTE = "cut";
    public static final String OUTSIDE_AGENT_SUBPOP = "outsideAgent";
    public final static String OUTSIDE_LEG_MODE = "outside";
    public final static String OUTSIDE_ACT_TYPE = "outside";
    private final Scenario source;

    private final static String CHANGE_EVENTS = "NetworkChangeEvents";
    private final static String MISSING_DEMAND = "HourlyMissingDemand";
    private final static String RELEVANT_ACT_LOCATIONS = "RelevantActivityLocations";


    public ScenarioCutter(Scenario scenario) {
        this.source = scenario;
    }

    /**
     * @param runDirectory        MATSim Output Directory of a finished MATSim run
     * @param runId               MATSim RunId
     * @param outputDirectoryname Folder where cut scenario is written to
     * @param scenarioSampleSize  Sample Size
     * @param parseEvents         whether Events file of run should be parsed to generate more accurate network travel times
     * @param extent              Inner Cut Extent
     * @param extended            Outer Cut extend
     * @throws IOException
     */
    public static void run(String runDirectory, String runId, String outputDirectoryname, double scenarioSampleSize, boolean parseEvents, CutExtent extent, CutExtent extended, CutExtent networkExtent) throws IOException {
        System.setProperty("matsim.preferLocalDtds", "true");
        String outputPrefix = runDirectory + "/" + runId + ".";

        String eventsFilename = parseEvents ? outputPrefix + "output_events.xml.gz" : null;

        Thread ramObserver = new Thread(() -> {
            //noinspection InfiniteLoopStatement
            while (true) {
                Gbl.printMemoryUsage();
                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, "MemoryObserver");
        ramObserver.setDaemon(true);
        ramObserver.start();

        File outputDir = new File(outputDirectoryname);
        log.info("ScenarioCutter: output directory = " + outputDir.getAbsolutePath());
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        Config config = ConfigUtils.createConfig();
        Scenario scenario = ScenarioUtils.createScenario(config);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(outputPrefix + "output_network.xml.gz");
        new TransitScheduleReader(scenario).readFile(outputPrefix + "output_transitSchedule.xml.gz");
        new VehicleReaderV1(scenario.getTransitVehicles()).readFile(outputPrefix + "output_transitVehicles.xml.gz");
        BetterPopulationReader.readSelectedPlansOnly(scenario, new File(outputPrefix + "output_plans.xml.gz"));
        new ObjectAttributesXmlReader(scenario.getPopulation().getPersonAttributes()).readFile(outputPrefix + "output_personAttributes.xml.gz");
        new VehicleReaderV1(scenario.getVehicles()).readFile(outputPrefix + "output_vehicles.xml.gz");
        new MatsimFacilitiesReader(scenario).readFile(outputPrefix + "output_facilities.xml.gz");

        log.info("clean network");
        simpleCleanNetwork(scenario.getNetwork());

        TravelTime travelTime = new FreeSpeedTravelTime();
        if (eventsFilename != null) {
            log.info("Extracting link travel times from Events file " + eventsFilename);
            TravelTimeCalculator.Builder ttBuilder = new TravelTimeCalculator.Builder(scenario.getNetwork());
            ttBuilder.setAnalyzedModes(Collections.singleton(TransportMode.car));
            ttBuilder.setCalculateLinkTravelTimes(true);
            TravelTimeCalculator ttCalculator = ttBuilder.build();
            EventsManager eventsManager = EventsUtils.createEventsManager(scenario.getConfig());
            eventsManager.addHandler(ttCalculator);
            new MatsimEventsReader(eventsManager).readFile(eventsFilename);
            travelTime = ttCalculator.getLinkTravelTimes();
        }

        log.info("Analyzing scenario...");
        Scenario analysisScenario = new ScenarioCutter(scenario).analyzeCut(extent, travelTime);

        log.info("Writing relevant activity locations...");
        List<Coord> relevantActLocations = (List<Coord>) analysisScenario.getScenarioElement(RELEVANT_ACT_LOCATIONS);
        writeRelevantLocations(new File(outputDir, "relevantActivityLocations.csv.gz"), relevantActLocations);
        relevantActLocations.clear(); // free the memory

        log.info("Cutting scenario...");
        Scenario cutScenario = new ScenarioCutter(scenario).performCut(extent, extended, networkExtent, travelTime, scenarioSampleSize);

        log.info("Writing cut scenario...");

        new NetworkWriter(cutScenario.getNetwork()).write(new File(outputDir, "network.xml.gz").getAbsolutePath());
        new PopulationWriter(cutScenario.getPopulation()).write(new File(outputDir, "population.xml.gz").getAbsolutePath());
        new VehicleWriterV1(cutScenario.getTransitVehicles()).writeFile(new File(outputDir, "transitVehicles.xml.gz").getAbsolutePath());
        new VehicleWriterV1(cutScenario.getVehicles()).writeFile(new File(outputDir, "vehicles.xml.gz").getAbsolutePath());
        new TransitScheduleWriter(cutScenario.getTransitSchedule()).writeFile(new File(outputDir, "schedule.xml.gz").getAbsolutePath());
        new ObjectAttributesXmlWriter(cutScenario.getPopulation().getPersonAttributes()).writeFile(new File(outputDir, "personAttributes.xml.gz").getAbsolutePath());
        new FacilitiesWriter(cutScenario.getActivityFacilities()).write(new File(outputDir, "facilities.xml.gz").getAbsolutePath());

        List<NetworkChangeEvent> changeEvents = (List<NetworkChangeEvent>) cutScenario.getScenarioElement(CHANGE_EVENTS);
        new NetworkChangeEventsWriter().write(new File(outputDir, "networkChangeEvents.xml.gz").getAbsolutePath(), changeEvents);

        writeMissingDemand(new File(outputDir, "missingDemand.csv"), cutScenario);

    }

    public static void main(String[] args) throws IOException {
        System.setProperty("matsim.preferLocalDtds", "true");

        args = new String[]{
                "C:\\devsbb\\codes\\_data\\CH2016_1.2.17\\CH.10pct.2016.output_config_cutter.xml",
                "C:\\devsbb\\codes\\_data\\CH2016_1.2.17\\CH.10pct.2016.output_events.xml.gz",
                "0.1",
                "C:\\devsbb\\codes\\_data\\CH2016_1.2.17_cut"
        };

        String configFilename = args[0];
        String eventsFilename = (args[1] == null || args[1].isEmpty()) ? null : args[1];
        double scenarioSampleSize = Double.parseDouble(args[2]);
        String outputDirectoryname = args[3];

        Thread ramObserver = new Thread(() -> {
            //noinspection InfiniteLoopStatement
            while (true) {
                Gbl.printMemoryUsage();
                try {
                    Thread.sleep(20_000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, "MemoryObserver");
        ramObserver.setDaemon(true);
        ramObserver.start();

        File outputDir = new File(outputDirectoryname);
        log.info("ScenarioCutter: output directory = " + outputDir.getAbsolutePath());
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        Config config = ConfigUtils.loadConfig(configFilename);
        Scenario scenario = ScenarioUtils.createScenario(config);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(config.network().getInputFile());
        new TransitScheduleReader(scenario).readFile(config.transit().getTransitScheduleFile());
        if (config.transit().getTransitLinesAttributesFile() != null) {
            new ObjectAttributesXmlReader(scenario.getTransitSchedule().getTransitLinesAttributes()).readFile(config.transit().getTransitLinesAttributesFile());
        }
        if (config.transit().getTransitStopsAttributesFile() != null) {
            new ObjectAttributesXmlReader(scenario.getTransitSchedule().getTransitStopsAttributes()).readFile(config.transit().getTransitStopsAttributesFile());
        }
        new VehicleReaderV1(scenario.getTransitVehicles()).readFile(config.transit().getVehiclesFile());
        BetterPopulationReader.readSelectedPlansOnly(scenario, new File(config.plans().getInputFile()));
        if (config.plans().getInputPersonAttributeFile() != null) {
            new ObjectAttributesXmlReader(scenario.getPopulation().getPersonAttributes()).readFile(config.plans().getInputPersonAttributeFile());
        }
        new MatsimFacilitiesReader(scenario).readFile(config.facilities().getInputFile());

        log.info("clean network");
        simpleCleanNetwork(scenario.getNetwork());

        CutExtent extent = new RadialExtent(600_000, 200_000, 10_000);
        CutExtent extended = new RadialExtent(600_000, 200_000, 15_000);
        CutExtent networkExtent = new RadialExtent(600_000, 200_000, 30_000);

        TravelTime travelTime = new FreeSpeedTravelTime();
        if (eventsFilename != null) {
            log.info("Extracting link travel times from Events file " + eventsFilename);
            TravelTimeCalculator.Builder ttBuilder = new TravelTimeCalculator.Builder(scenario.getNetwork());
            ttBuilder.setAnalyzedModes(Collections.singleton(TransportMode.car));
            ttBuilder.setCalculateLinkTravelTimes(true);
            TravelTimeCalculator ttCalculator = ttBuilder.build();
            EventsManager eventsManager = EventsUtils.createEventsManager(scenario.getConfig());
            eventsManager.addHandler(ttCalculator);
            new MatsimEventsReader(eventsManager).readFile(eventsFilename);
            travelTime = ttCalculator.getLinkTravelTimes();
        }

        log.info("Analyzing scenario...");
        Scenario analysisScenario = new ScenarioCutter(scenario).analyzeCut(extent, travelTime);

        log.info("Writing relevant activity locations...");
        List<Coord> relevantActLocations = (List<Coord>) analysisScenario.getScenarioElement(RELEVANT_ACT_LOCATIONS);
        writeRelevantLocations(new File(outputDir, "relevantActivityLocations.csv.gz"), relevantActLocations);
        relevantActLocations.clear(); // free the memory

        log.info("Cutting scenario...");
        Scenario cutScenario = new ScenarioCutter(scenario).performCut(extent, extended, networkExtent, travelTime, scenarioSampleSize);

        log.info("Writing cut scenario...");

        new NetworkWriter(cutScenario.getNetwork()).write(new File(outputDir, "network.xml.gz").getAbsolutePath());
        new PopulationWriter(cutScenario.getPopulation()).write(new File(outputDir, "population.xml.gz").getAbsolutePath());
        new VehicleWriterV1(cutScenario.getTransitVehicles()).writeFile(new File(outputDir, "transitVehicles.xml.gz").getAbsolutePath());
        new TransitScheduleWriter(cutScenario.getTransitSchedule()).writeFile(new File(outputDir, "schedule.xml.gz").getAbsolutePath());
        if (config.transit().getTransitLinesAttributesFile() != null) {
            new ObjectAttributesXmlWriter(cutScenario.getTransitSchedule().getTransitLinesAttributes()).writeFile(new File(outputDir, "transitLinesAttributes.xml.gz").getAbsolutePath());
        }
        if (config.transit().getTransitStopsAttributesFile() != null) {
            new ObjectAttributesXmlWriter(cutScenario.getTransitSchedule().getTransitStopsAttributes()).writeFile(new File(outputDir, "transitStopsAttributes.xml.gz").getAbsolutePath());
        }


        new FacilitiesWriter(cutScenario.getActivityFacilities()).write(new File(outputDir, "facilities.xml.gz").getAbsolutePath());

        List<NetworkChangeEvent> changeEvents = (List<NetworkChangeEvent>) cutScenario.getScenarioElement(CHANGE_EVENTS);
        new NetworkChangeEventsWriter().write(new File(outputDir, "networkChangeEvents.xml.gz").getAbsolutePath(), changeEvents);

        writeMissingDemand(new File(outputDir, "missingDemand.csv"), cutScenario);
    }

    private void filterPersons(CutContext ctx) {
        AgentState state = new AgentState();
        for (Person person : ctx.source.getPopulation().getPersons().values()) {
            Plan plan = person.getSelectedPlan();
            state.reset();
            calcStateByActivities(ctx, plan, state);
            if (!state.hasInside || !state.hasOutside) {
                // agent has activities only inside or only outside, check if routes intersect area
                calcStateByRoutes(ctx, plan, state);
            }
            if (state.hasInside) {
                ctx.relevantPersons.put(person.getId(), person);
                if (state.hasOutside) {
                    ctx.partiallyInsidePersons.put(person.getId(), person);
                } else {
                    ctx.fullyInsidePersons.put(person.getId(), person);
                }
                collectActivityLocations(ctx, plan);
            }
        }
    }

    private void calcStateByActivities(CutContext ctx, Plan plan, AgentState state) {
        for (PlanElement pe : plan.getPlanElements()) {
            if (pe instanceof Activity) {
                Activity act = (Activity) pe;
                if (ctx.extent.isInside(act.getCoord())) {
                    state.hasInside = true;
                } else {
                    state.hasOutside = true;
                }
                if (state.hasInside && state.hasOutside) {
                    return;
                }
            }
        }
    }

    private void calcStateByRoutes(CutContext ctx, Plan plan, AgentState state) {
        for (PlanElement pe : plan.getPlanElements()) {
            if (pe instanceof Leg) {
                Leg leg = (Leg) pe;
                calcStateByRoute(ctx, state, leg.getRoute());
                if (state.hasInside && state.hasOutside) {
                    break;
                }
            }
        }
    }

    private void calcStateByRoute(CutContext ctx, AgentState state, Route route) {
        if (route instanceof NetworkRoute) {
            NetworkRoute netRoute = (NetworkRoute) route;
            calcStateByNetworkRoute(ctx, state, netRoute);
        }
        if (route instanceof ExperimentalTransitRoute) {
            ExperimentalTransitRoute ptRoute = (ExperimentalTransitRoute) route;
            calcStateByTransitRoute(ctx, state, ptRoute);
        }
    }

    private void calcStateByNetworkRoute(CutContext ctx, AgentState state, NetworkRoute netRoute) {
        Network network = ctx.source.getNetwork();
        Id<Link> linkId = netRoute.getStartLinkId();
        Link link = network.getLinks().get(linkId);
        calcStateByNode(ctx, state, link.getFromNode());
        calcStateByNode(ctx, state, link.getToNode());
        for (Id<Link> linkId2 : netRoute.getLinkIds()) {
            link = network.getLinks().get(linkId2);
            calcStateByNode(ctx, state, link.getToNode());
        }
        linkId = netRoute.getEndLinkId();
        link = network.getLinks().get(linkId);
        calcStateByNode(ctx, state, link.getToNode());
    }

    private void calcStateByNode(CutContext ctx, AgentState state, Node node) {
        if (isNodeInside(ctx, node)) {
            state.hasInside = true;
        } else {
            state.hasOutside = true;
        }
    }

    private void collectActivityLocations(CutContext ctx, Plan plan) {
        for (PlanElement pe : plan.getPlanElements()) {
            if (pe instanceof Activity) {
                Activity act = (Activity) pe;
                Coord coord = act.getCoord();
                if (coord == null) {
                    coord = ctx.source.getActivityFacilities().getFacilities().get(act.getFacilityId()).getCoord();
                }
                if (coord != null) {
                    ctx.relevantActivityCoords.add(coord);
                }
            }
        }
    }

    private void calcStateExtendedByRoute(CutContext ctx, AgentState state, Route route) {
        if (route instanceof NetworkRoute) {
            NetworkRoute netRoute = (NetworkRoute) route;
            calcStateExtendedByNetworkRoute(ctx, state, netRoute);
        }
        if (route instanceof ExperimentalTransitRoute) {
            ExperimentalTransitRoute ptRoute = (ExperimentalTransitRoute) route;
            calcStateExtendedByTransitRoute(ctx, state, ptRoute);
        }
    }

    private void calcStateExtendedByNetworkRoute(CutContext ctx, AgentState state, NetworkRoute netRoute) {
        Network network = ctx.source.getNetwork();
        Id<Link> linkId = netRoute.getStartLinkId();
        Link link = network.getLinks().get(linkId);
        calcStateExtendedByNode(ctx, state, link.getFromNode());
        calcStateExtendedByNode(ctx, state, link.getToNode());
        for (Id<Link> linkId2 : netRoute.getLinkIds()) {
            link = network.getLinks().get(linkId2);
            calcStateExtendedByNode(ctx, state, link.getToNode());
        }
        linkId = netRoute.getEndLinkId();
        link = network.getLinks().get(linkId);
        calcStateExtendedByNode(ctx, state, link.getToNode());
    }

    private void calcStateExtendedByNode(CutContext ctx, AgentState state, Node node) {
        if (isNodeInsideExtended(ctx, node)) {
            state.hasInside = true;
        } else {
            state.hasOutside = true;
        }
    }

    private boolean isNodeInside(CutContext ctx, Node node) {
        return ctx.insideNodes.computeIfAbsent(node.getId(), id -> ctx.extent.isInside(node.getCoord()));
    }

    private boolean isNodeInsideExtended(CutContext ctx, Node node) {
        return ctx.extendedInsideNodes.computeIfAbsent(node.getId(), id -> ctx.extendedExtent.isInside(node.getCoord()));
    }

    public Scenario analyzeCut(CutExtent extent, TravelTime travelTime) {
        CutContext ctx = new CutContext(this.source, travelTime, extent, extent, extent);
        filterPersons(ctx);
        printStats(ctx);
        return ctx.dest;
    }

    private void calcStateByTransitRoute(CutContext ctx, AgentState state, ExperimentalTransitRoute ptRoute) {
        AgentState tmpState = new AgentState();
        Network network = ctx.source.getNetwork();
        TransitSchedule schedule = ctx.source.getTransitSchedule();

        Id<Link> startLinkId = ptRoute.getStartLinkId();
        Id<Link> endLinkId = ptRoute.getEndLinkId();
        TransitRoute route = schedule.getTransitLines().get(ptRoute.getLineId()).getRoutes().get(ptRoute.getRouteId());
        NetworkRoute netRoute = route.getRoute();
        boolean isPassenger = startLinkId.equals(netRoute.getStartLinkId());
        Link link;
        if (isPassenger) {
            link = network.getLinks().get(startLinkId);
            calcStateByNode(ctx, tmpState, link.getFromNode());
            calcStateByNode(ctx, tmpState, link.getToNode());
        }
        for (Id<Link> linkId : netRoute.getLinkIds()) {
            if (startLinkId.equals(linkId)) {
                tmpState.reset(); // reset, looks like the agent can enter at a later time, so ignore what happened before
                isPassenger = true;
            }
            if (isPassenger) {
                link = network.getLinks().get(startLinkId);
                calcStateByNode(ctx, tmpState, link.getToNode());
            }
            if (endLinkId.equals(linkId)) {
                isPassenger = false;
                break;
            }
        }
        if (isPassenger) {
            link = network.getLinks().get(netRoute.getEndLinkId());
            calcStateByNode(ctx, tmpState, link.getToNode());
        }
        state.hasOutside = tmpState.hasOutside;
        state.hasInside = tmpState.hasInside;
    }

    private void calcStateExtendedByTransitRoute(CutContext ctx, AgentState state, ExperimentalTransitRoute ptRoute) {
        AgentState tmpState = new AgentState();
        Network network = ctx.source.getNetwork();
        TransitSchedule schedule = ctx.source.getTransitSchedule();

        Id<Link> startLinkId = ptRoute.getStartLinkId();
        Id<Link> endLinkId = ptRoute.getEndLinkId();
        TransitRoute route = schedule.getTransitLines().get(ptRoute.getLineId()).getRoutes().get(ptRoute.getRouteId());
        NetworkRoute netRoute = route.getRoute();
        boolean isPassenger = startLinkId.equals(netRoute.getStartLinkId());
        Link link;
        if (isPassenger) {
            link = network.getLinks().get(startLinkId);
            calcStateExtendedByNode(ctx, tmpState, link.getFromNode());
            calcStateExtendedByNode(ctx, tmpState, link.getToNode());
        }
        for (Id<Link> linkId : netRoute.getLinkIds()) {
            if (startLinkId.equals(linkId)) {
                tmpState.reset(); // reset, looks like the agent can enter at a later time, so ignore what happened before
                isPassenger = true;
            }
            if (isPassenger) {
                link = network.getLinks().get(startLinkId);
                calcStateExtendedByNode(ctx, tmpState, link.getToNode());
            }
            if (endLinkId.equals(linkId)) {
                isPassenger = false;
                break;
            }
        }
        if (isPassenger) {
            link = network.getLinks().get(netRoute.getEndLinkId());
            calcStateExtendedByNode(ctx, tmpState, link.getToNode());
        }
        state.hasOutside = tmpState.hasOutside;
        state.hasInside = tmpState.hasInside;
    }

    public Scenario performCut(CutExtent extent, CutExtent extendedExtent, CutExtent networkExtent, TravelTime travelTime, double populationSample) {
        CutContext ctx = new CutContext(this.source, travelTime, extent, extendedExtent, networkExtent);
        double demandFactor = 1 / populationSample;

        filterPersons(ctx);
        cutNetwork(ctx);
        cutTransit(ctx);
        cutPersons(ctx);

        calcNetworkCapacityChanges(ctx, demandFactor);
        filterFacilities(ctx);
        copyVehicleTypes(ctx);
        printStats(ctx);

        return ctx.dest;
    }

    private void copyVehicleTypes(CutContext ctx) {
        ctx.source.getVehicles().getVehicleTypes().values().stream()
                .forEach(vehicleType -> ctx.dest.getVehicles().addVehicleType(vehicleType));

    }

    private void copyLink(Link link, Network dest) {
        NetworkFactory f = dest.getFactory();
        Node fromNode = dest.getNodes().get(link.getFromNode().getId());
        if (fromNode == null) {
            fromNode = f.createNode(link.getFromNode().getId(), link.getFromNode().getCoord());
            dest.addNode(fromNode);
        }
        Node toNode = dest.getNodes().get(link.getToNode().getId());
        if (toNode == null) {
            toNode = f.createNode(link.getToNode().getId(), link.getToNode().getCoord());
            dest.addNode(toNode);
        }
        Link newLink = f.createLink(link.getId(), fromNode, toNode);
        newLink.setAllowedModes(link.getAllowedModes());
        newLink.setCapacity(link.getCapacity());
        newLink.setFreespeed(link.getFreespeed());
        newLink.setLength(link.getLength());
        newLink.setNumberOfLanes(link.getNumberOfLanes());
        dest.addLink(newLink);
    }

    private void cutTransit(CutContext ctx) {
        cutScheduleWithallRoutes(ctx);
        filterTransitVehicles(ctx);
        filterMinTransferTimes(ctx);
        addMissingTransitLinks(ctx);
    }


    private void cutScheduleWithallRoutes(CutContext ctx) {
        TransitSchedule dest = ctx.dest.getTransitSchedule();
        TransitSchedule schedule = ctx.dest.getTransitSchedule();

        TransitSchedule source = ctx.source.getTransitSchedule();
        Set<TransitStopFacility> insideStops = new HashSet<>();
        for (TransitStopFacility stop : source.getFacilities().values()) {
            if (ctx.networkExtent.isInside(stop.getCoord())) {
                insideStops.add(stop);
            }
        }

        for (TransitLine line : source.getTransitLines().values()) {
            for (TransitRoute route : line.getRoutes().values()) {
                boolean keepRoute = route.getStops().stream()
                        .map(TransitRouteStop::getStopFacility)
                        .anyMatch(stop -> insideStops.contains(stop));
                if (keepRoute) {
                    TransitLine destLine = dest.getTransitLines().get(line.getId());
                    if (destLine == null) {
                        destLine = dest.getFactory().createTransitLine(line.getId());
                        destLine.setName(line.getName());
                        AttributesUtils.copyAttributesFromTo(line, destLine);
                        dest.addTransitLine(destLine);
                        ObjectAttributesUtils.copyAllAttributes(source.getTransitLinesAttributes(), dest.getTransitLinesAttributes(), line.getId().toString());
                    }
                    destLine.addRoute(route);
                }
            }
        }
        Set<TransitStopFacility> usedStops = new HashSet<>();
        for (TransitLine l : dest.getTransitLines().values()) {
            usedStops.addAll(l.getRoutes().values().stream()
                    .flatMap(transitRoute -> transitRoute.getStops().stream().map(TransitRouteStop::getStopFacility))
                    .collect(Collectors.toSet()));

        }
        usedStops.stream().forEach(stop -> dest.addStopFacility(stop));
    }

    private void filterTransitVehicles(CutContext ctx) {
        Vehicles destVehicles = ctx.dest.getTransitVehicles();
        VehiclesFactory f = destVehicles.getFactory();
        Vehicles srcVehicles = ctx.source.getTransitVehicles();
        for (TransitLine line : ctx.dest.getTransitSchedule().getTransitLines().values()) {
            for (TransitRoute route : line.getRoutes().values()) {
                for (Departure d : route.getDepartures().values()) {
                    Id<Vehicle> vehicleId = d.getVehicleId();
                    Vehicle vehicle = destVehicles.getVehicles().get(vehicleId);
                    if (vehicle == null) {
                        Vehicle srcVehicle = srcVehicles.getVehicles().get(vehicleId);
                        VehicleType srcVehicleType = srcVehicle.getType();
                        VehicleType destVehicleType = destVehicles.getVehicleTypes().get(srcVehicleType.getId());
                        if (destVehicleType == null) {
                            destVehicleType = destVehicles.getFactory().createVehicleType(srcVehicleType.getId());
                            destVehicleType.setCapacity(srcVehicleType.getCapacity());
                            destVehicleType.setPcuEquivalents(srcVehicleType.getPcuEquivalents());
                            destVehicleType.setMaximumVelocity(srcVehicleType.getMaximumVelocity());
                            destVehicleType.setDoorOperationMode(srcVehicleType.getDoorOperationMode());
                            destVehicleType.setDescription(srcVehicleType.getDescription());
                            destVehicleType.setLength(srcVehicleType.getLength());
                            destVehicleType.setWidth(srcVehicleType.getWidth());
                            destVehicleType.setFlowEfficiencyFactor(srcVehicleType.getFlowEfficiencyFactor());
                            destVehicleType.setAccessTime(srcVehicleType.getAccessTime());
                            destVehicleType.setEgressTime(srcVehicleType.getEgressTime());
                            destVehicleType.setEngineInformation(srcVehicleType.getEngineInformation());
                            destVehicles.addVehicleType(destVehicleType);
                        }
                        vehicle = f.createVehicle(srcVehicle.getId(), destVehicleType);
                        destVehicles.addVehicle(vehicle);
                    }
                }
            }
        }
    }



    private void filterMinTransferTimes(CutContext ctx) {
        TransitSchedule src = ctx.source.getTransitSchedule();
        TransitSchedule dest = ctx.dest.getTransitSchedule();

        MinimalTransferTimes.MinimalTransferTimesIterator iter = src.getMinimalTransferTimes().iterator();
        while (iter.hasNext()) {
            iter.next();
            Id<TransitStopFacility> fromStopId = iter.getFromStopId();
            Id<TransitStopFacility> toStopId = iter.getToStopId();
            double seconds = iter.getSeconds();
            boolean hasFromStop = dest.getFacilities().containsKey(fromStopId);
            boolean hasToStop = dest.getFacilities().containsKey(toStopId);
            if (hasFromStop && hasToStop) {
                dest.getMinimalTransferTimes().set(fromStopId, toStopId, seconds);
            }

        }
    }

    private void addMissingTransitLinks(CutContext ctx) {
        Network src = ctx.source.getNetwork();
        Network dest = ctx.dest.getNetwork();
        for (TransitLine line : ctx.dest.getTransitSchedule().getTransitLines().values()) {
            for (TransitRoute route : line.getRoutes().values()) {
                NetworkRoute netRoute = route.getRoute();
                if (!dest.getLinks().containsKey(netRoute.getStartLinkId())) {
                    Link link = src.getLinks().get(netRoute.getStartLinkId());
                    copyLink(link, dest);
                }
                for (Id<Link> linkId : netRoute.getLinkIds()) {
                    if (!dest.getLinks().containsKey(linkId)) {
                        Link link = src.getLinks().get(linkId);
                        copyLink(link, dest);
                    }
                }
                if (!dest.getLinks().containsKey(netRoute.getEndLinkId())) {
                    Link link = src.getLinks().get(netRoute.getEndLinkId());
                    copyLink(link, dest);
                }
            }
        }
    }

    private void cutPersons(CutContext ctx) {
        ctx.source.getPopulation().getPersons().values()
                .stream()
                .filter(p -> ctx.relevantPersons.containsKey(p.getId()))
                .forEach(p -> usePerson(ctx, p));
    }

    private boolean isNodeInsideNetworkExtent(CutContext ctx, Node node) {
        return ctx.networkInsideNodes.computeIfAbsent(node.getId(), id -> ctx.networkExtent.isInside(node.getCoord()));
    }

    private boolean planWasCut(Plan plan) {
        for (PlanElement pe : plan.getPlanElements()) {
            if (pe instanceof Activity) {
                if (((Activity) pe).getType().equals(OUTSIDE_ACT_TYPE)) {
                    return true;
                }
            }
            if (pe instanceof Leg) {
                if (((Leg) pe).getMode().equals(OUTSIDE_LEG_MODE)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void cutNetwork(CutContext ctx) {
        Network destNet = ctx.dest.getNetwork();
        for (Link link : ctx.source.getNetwork().getLinks().values()) {
            boolean isInside = isNodeInsideNetworkExtent(ctx, link.getFromNode()) || isNodeInsideNetworkExtent(ctx, link.getToNode());
            if (isInside) {
                copyLink(link, destNet);
            }
        }

        MultimodalNetworkCleaner networkCleaner = new MultimodalNetworkCleaner(destNet);
        Set<String> cleanedModes = Collections.singleton(TransportMode.car);
        Set<String> ptModes = Collections.singleton("detPt");
        networkCleaner.run(cleanedModes, ptModes);

    }

    public static Predicate<Person> isCut() {
        return person -> person.getAttributes().getAsMap().containsKey(ScenarioCutter.CUT_ATTRIBUTE) && (boolean) person.getAttributes().getAttribute(ScenarioCutter.CUT_ATTRIBUTE);
    }

    private Plan cutPlan(CutContext ctx, Person destPerson, Plan srcPlan) {
        approximateEndtimesForInteractionActivities(srcPlan);

        Activity fromAct;
        Activity toAct = null;
        Leg leg = null;
        boolean fromActInside;
        boolean toActInside = false;
        boolean legInside;

        Plan plan = ctx.dest.getPopulation().getFactory().createPlan();
        destPerson.addPlan(plan);

        List<PlanElement> srcPlanElements = srcPlan.getPlanElements();
        AgentState state = new AgentState();
        for (PlanElement pe : srcPlanElements) {
            if (pe instanceof Activity) {
                fromAct = toAct;
                fromActInside = toActInside;

                toAct = (Activity) pe;
                toActInside = ctx.extendedExtent.isInside(toAct.getCoord());

                if (leg != null) {
                    if (!fromActInside && !toActInside) {
                        state.reset();
                        calcStateExtendedByRoute(ctx, state, leg.getRoute());
                        legInside = state.hasInside;
                    } else {
                        legInside = true; // leg must be inside when either fromAct or toAct are inside
                    }

                    if (fromActInside || toActInside || legInside) {
                        addLegToPlan(ctx, plan, fromAct, fromActInside, leg, toAct, toActInside);
                    }
                }
            }
            if (pe instanceof Leg) {
                leg = (Leg) pe;
            }
        }
        renameInitialOrFinalInteractions(plan);
        removeEndTimesFromInteractionActivities(plan);
        removeDurationWhenBothAreSet(plan);
        modifyLoneSomeAccessEgressWalks(plan);
        removeRoutesWithLinksThatDoNotExist(plan, ctx);
        return plan;
    }

    private void removeRoutesWithLinksThatDoNotExist(Plan plan, CutContext ctx) {
        TripStructureUtils.getLegs(plan).stream()
                .filter(leg -> leg.getRoute() instanceof NetworkRoute)
                .forEach(leg -> {
                    if (!linksCoveredInNetwork((NetworkRoute) leg.getRoute(), ctx.dest.getNetwork())) {
                        leg.setRoute(null);
                    }
                });
    }

    private boolean linksCoveredInNetwork(NetworkRoute route, Network network) {
        if (!network.getLinks().containsKey(route.getEndLinkId()) || !network.getLinks().containsKey(route.getStartLinkId())) {
            return false;
        }
        for (Id<Link> link : route.getLinkIds()) {
            if (!network.getLinks().containsKey(link)) {
                return false;
            }
        }
        return true;
    }

    private void removeDurationWhenBothAreSet(Plan plan) {
        plan.getPlanElements().stream()
                .filter(Activity.class::isInstance)
                .forEach(a -> {
                    Activity activity = (Activity) a;
                    if (!Time.isUndefinedTime(activity.getEndTime()) && !Time.isUndefinedTime(activity.getMaximumDuration())) {
                        activity.setMaximumDuration(Time.getUndefinedTime());
                    }
                });
    }

    /**
     * These may occur if an activity is inside, but its interaction activity is outside
     *
     * @param plan
     */
    private void modifyLoneSomeAccessEgressWalks(Plan plan) {

        Activity previousAct = null;
        Leg previousLeg = null;
        for (PlanElement planElement : plan.getPlanElements()) {
            if (planElement instanceof Activity) {
                Activity current = (Activity) planElement;
                if (previousAct != null) {
                    if (!previousAct.getType().endsWith("interaction") && !current.getType().endsWith("interaction")) {
                        if (previousLeg.getMode().equals(TransportMode.access_walk) || previousLeg.getMode().equals(TransportMode.egress_walk)) {
                            previousLeg.setMode(TransportMode.walk);
                        }
                    }

                }
                previousAct = current;
            } else if (planElement instanceof Leg) {
                previousLeg = (Leg) planElement;
            }
        }
    }


    private void renameInitialOrFinalInteractions(Plan plan) {
        Activity first = (Activity) plan.getPlanElements().get(0);
        Activity last = (Activity) plan.getPlanElements().get(plan.getPlanElements().size() - 1);
        renameInteractionActivity(first);
        renameInteractionActivity(last);

    }

    private void renameInteractionActivity(Activity act) {
        if (SBBActivities.stageActivityTypeList.contains(act.getType())) {
            act.setType(OUTSIDE_ACT_TYPE);
        }
    }


    private void addLegToPlan(CutContext ctx, Plan plan, Activity fromAct, boolean fromActInside, Leg leg, Activity toAct, boolean toActInside) {
        Route route = leg.getRoute();
        if (route instanceof NetworkRoute) {
            addNetworkLegToPlan(ctx, plan, fromAct, fromActInside, leg, (NetworkRoute) route, toAct, toActInside);
        } else if (route instanceof ExperimentalTransitRoute) {
            addTransitLegToPlan(ctx, plan, fromAct, fromActInside, leg, (ExperimentalTransitRoute) route, toAct, toActInside);
        } else {
            addTeleportationLegToPlan(ctx, plan, fromAct, fromActInside, leg, toAct, toActInside);
        }
    }

    private void addNetworkLegToPlan(CutContext ctx, Plan plan, Activity fromAct, boolean fromActInside, Leg leg, NetworkRoute route, Activity toAct, boolean toActInside) {
        boolean isEmptyPlan = plan.getPlanElements().isEmpty();
        boolean comingInside = !fromActInside && toActInside;
        boolean goingOutside = fromActInside && !toActInside;
        boolean throughTraffic = !fromActInside && !toActInside;
        boolean hasOutsideLinks = hasOutsideLinks(ctx, route);

        if (hasOutsideLinks) {
            if (comingInside) {
                NetworkRoute newRoute = findAvailableRouteEnd(ctx, route);
                Leg newLeg = PopulationUtils.createLeg(leg);
                newLeg.setRoute(newRoute);
                if (!isEmptyPlan) {
                    Id<Link> lastLinkId = getLinkId(ctx, getLastActivity(ctx, plan));
                    Leg teleportLeg = createOutsideLeg(ctx, lastLinkId, newRoute.getStartLinkId());
                    plan.addLeg(teleportLeg);
                }
                double delay = calcDelay(ctx, route, newRoute, fromAct.getEndTime(), plan.getPerson());
                Activity outsideAct = createOutsideActivity(ctx, newRoute.getStartLinkId(), fromAct.getEndTime() + delay);
                plan.addActivity(outsideAct);
                plan.addLeg(newLeg);
                plan.addActivity(toAct);
            } else if (goingOutside) {
                NetworkRoute newRoute = findAvailableRouteStart(ctx, route);
                Leg newLeg = PopulationUtils.createLeg(leg);
                newLeg.setRoute(newRoute);
                if (isEmptyPlan) {
                    plan.addActivity(fromAct);
                }
                plan.addLeg(newLeg);
                Activity outsideAct = createOutsideActivity(ctx, newRoute.getEndLinkId(), toAct.getEndTime());
                plan.addActivity(outsideAct);
            } else if (throughTraffic) {
                NetworkRoute newRoute = findAvailableRoutePart(ctx, route);
                Leg newLeg = PopulationUtils.createLeg(leg);
                newLeg.setRoute(newRoute);
                if (!isEmptyPlan) {
                    Id<Link> lastLinkId = getLinkId(ctx, getLastActivity(ctx, plan));
                    Leg teleportLeg = createOutsideLeg(ctx, lastLinkId, newRoute.getStartLinkId());
                    plan.addLeg(teleportLeg);
                }
                double delay = calcDelay(ctx, route, newRoute, fromAct.getEndTime(), plan.getPerson());
                Activity outsideAct1 = createOutsideActivity(ctx, newRoute.getStartLinkId(), fromAct.getEndTime() + delay);
                plan.addActivity(outsideAct1);
                plan.addLeg(newLeg);
                Activity outsideAct2 = createOutsideActivity(ctx, newRoute.getEndLinkId(), toAct.getEndTime());
                plan.addActivity(outsideAct2);
            } else { // start and end inside, but going outside
                NetworkRoute routeStart = findAvailableRouteStart(ctx, route);
                NetworkRoute routeEnd = findAvailableRouteEnd(ctx, route);
                Leg newLeg1 = PopulationUtils.createLeg(leg);
                newLeg1.setRoute(routeStart);
                Leg newLeg2 = PopulationUtils.createLeg(leg);
                newLeg2.setRoute(routeEnd);
                if (isEmptyPlan) {
                    plan.addActivity(fromAct);
                }
                plan.addLeg(newLeg1);
                double delay = calcDelay(ctx, route, routeEnd, fromAct.getEndTime(), plan.getPerson());
                Activity outsideAct1 = createOutsideActivity(ctx, routeStart.getEndLinkId(), fromAct.getEndTime()); // time does not much matter here, better early than late
                Activity outsideAct2 = createOutsideActivity(ctx, routeEnd.getStartLinkId(), fromAct.getEndTime() + delay);

                plan.addActivity(outsideAct1);
                Leg teleportLeg = createOutsideLeg(ctx, routeStart.getEndLinkId(), routeEnd.getStartLinkId());
                plan.addLeg(teleportLeg);
                plan.addActivity(outsideAct2);
                plan.addLeg(newLeg2);
                plan.addActivity(toAct);
            }
        } else { // no outside links, so everything is inside
            if (isEmptyPlan) {
                plan.addActivity(fromAct);
            }
            plan.addLeg(leg);
            plan.addActivity(toAct);
        }
    }

    private Activity getLastActivity(CutContext ctx, Plan plan) {
        List<PlanElement> elements = plan.getPlanElements();
        return (Activity) elements.get(elements.size() - 1);
    }

    private void approximateEndtimesForInteractionActivities(Plan srcPlan) {
        double lastKnownActivityEndTime = Double.NaN;
        double timePassed = 0.0;
        for (PlanElement planElement : srcPlan.getPlanElements()) {
            if (planElement instanceof Activity) {
                Activity activity = (Activity) planElement;
                if (Time.isUndefinedTime(activity.getEndTime())) {
                    activity.setEndTime(lastKnownActivityEndTime + timePassed + activity.getMaximumDuration());
                }
                lastKnownActivityEndTime = activity.getEndTime();
                timePassed = 0.0;
            } else if (planElement instanceof Leg) {
                timePassed += ((Leg) planElement).getTravelTime();
            }
        }
    }

    private Leg createOutsideLeg(CutContext ctx, Id<Link> startLinkId, Id<Link> endLinkId) {
        Link startLink = ctx.source.getNetwork().getLinks().get(startLinkId); // need to use source, as we need the actual link for the coord
        Link endLink = ctx.source.getNetwork().getLinks().get(endLinkId);
        Leg leg = PopulationUtils.createLeg(OUTSIDE_LEG_MODE);
        Route teleportRoute = RouteUtils.createGenericRouteImpl(startLinkId, endLinkId);
        teleportRoute.setTravelTime(0);
        teleportRoute.setDistance(CoordUtils.calcEuclideanDistance(startLink.getCoord(), endLink.getCoord()));
        leg.setRoute(teleportRoute);
        return leg;
    }

    private NetworkRoute findAvailableRouteEnd(CutContext ctx, NetworkRoute route) {
        Network network = ctx.dest.getNetwork();
        List<Id<Link>> linkIds = new ArrayList<>();

        if (network.getLinks().containsKey(route.getEndLinkId())) {
            linkIds.add(route.getEndLinkId());
        }
        List<Id<Link>> srcLinkIds = route.getLinkIds();
        for (int i = srcLinkIds.size() - 1; i >= 0; i--) {
            Id<Link> linkId = srcLinkIds.get(i);
            if (network.getLinks().containsKey(linkId)) {
                linkIds.add(linkId);
            } else {
                break;
            }
        }
        // ignore startLink, we look for the route end and know that the route does not start inside
        Collections.reverse(linkIds);

        return RouteUtils.createNetworkRoute(linkIds, network);
    }

    private NetworkRoute findAvailableRouteStart(CutContext ctx, NetworkRoute route) {
        Network network = ctx.dest.getNetwork();
        List<Id<Link>> linkIds = new ArrayList<>();

        if (network.getLinks().containsKey(route.getStartLinkId())) {
            linkIds.add(route.getStartLinkId());
        }
        for (Id<Link> linkId : route.getLinkIds()) {
            if (network.getLinks().containsKey(linkId)) {
                linkIds.add(linkId);
            } else {
                break;
            }
        }
        // ignore endLink, we look for the route start and know that the route does not end inside
        return RouteUtils.createNetworkRoute(linkIds, network);
    }

    private NetworkRoute findAvailableRoutePart(CutContext ctx, NetworkRoute route) {
        List<Id<Link>> linkIds = new ArrayList<>();
        Network network = ctx.dest.getNetwork();

        if (network.getLinks().containsKey(route.getStartLinkId())) {
            linkIds.add(route.getStartLinkId());
        }
        for (Id<Link> linkId : route.getLinkIds()) {
            boolean isInside = network.getLinks().containsKey(linkId);
            if (isInside) {
                linkIds.add(linkId);
            } else if (!linkIds.isEmpty()) {
                return RouteUtils.createNetworkRoute(linkIds, network);
            }
        }
        if (network.getLinks().containsKey(route.getEndLinkId())) {
            linkIds.add(route.getEndLinkId());
        }

        return RouteUtils.createNetworkRoute(linkIds, network);
    }

    private void addTransitLegToPlan(CutContext ctx, Plan plan, Activity fromAct, boolean fromActInside, Leg leg, ExperimentalTransitRoute route, Activity toAct, boolean toActInside) {
        boolean isEmptyPlan = plan.getPlanElements().isEmpty();
        boolean comingInside = !fromActInside && toActInside;
        boolean goingOutside = fromActInside && !toActInside;
        boolean throughTraffic = !fromActInside && !toActInside;
        boolean hasOutsideLinks = hasOutsideLinks(ctx, route);

        if (hasOutsideLinks) {
            if (comingInside) {
                ExperimentalTransitRoute newRoute = findAvailableRouteStart(ctx, route);
                Leg newLeg = PopulationUtils.createLeg(leg);
                newLeg.setRoute(newRoute);
                if (!isEmptyPlan) {
                    Id<Link> lastLinkId = getLinkId(ctx, getLastActivity(ctx, plan));
                    Leg teleportLeg = createOutsideLeg(ctx, lastLinkId, newRoute.getStartLinkId());
                    plan.addLeg(teleportLeg);
                }
                double delay = calcDelay(ctx, route, newRoute, fromAct.getEndTime(), plan.getPerson());
                Activity outsideAct = createOutsideActivity(ctx, newRoute.getStartLinkId(), fromAct.getEndTime() + delay);
                plan.addActivity(outsideAct);
                plan.addLeg(newLeg);
                plan.addActivity(toAct);
            } else if (goingOutside) {
                ExperimentalTransitRoute newRoute = findAvailableRouteEnd(ctx, route);
                Leg newLeg = PopulationUtils.createLeg(leg);
                newLeg.setRoute(newRoute);
                if (isEmptyPlan) {
                    plan.addActivity(fromAct);
                }
                plan.addLeg(newLeg);
                if (!ctx.dest.getNetwork().getLinks().containsKey(newRoute.getEndLinkId())){
                    throw new RuntimeException(newRoute.getEndLinkId() + "  is not part of the cut network, but part of transit route:\n " + newRoute.toString());
                }
                Activity outsideAct = createOutsideActivity(ctx, newRoute.getEndLinkId(), toAct.getEndTime());
                plan.addActivity(outsideAct);
            } else if (throughTraffic) {
                ExperimentalTransitRoute newRoute = findAvailableRoutePart(ctx, route);
                Leg newLeg = PopulationUtils.createLeg(leg);
                newLeg.setRoute(newRoute);
                if (!isEmptyPlan) {
                    Id<Link> lastLinkId = getLinkId(ctx, getLastActivity(ctx, plan));
                    Leg teleportLeg = createOutsideLeg(ctx, lastLinkId, newRoute.getStartLinkId());
                    plan.addLeg(teleportLeg);
                }
                double delay = calcDelay(ctx, route, newRoute, fromAct.getEndTime(), plan.getPerson());
                Activity outsideAct1 = createOutsideActivity(ctx, newRoute.getStartLinkId(), fromAct.getEndTime() + delay);
                plan.addActivity(outsideAct1);
                plan.addLeg(newLeg);
                Activity outsideAct2 = createOutsideActivity(ctx, newRoute.getEndLinkId(), toAct.getEndTime());
                plan.addActivity(outsideAct2);
            } else { // start and end inside, but going outside
                ExperimentalTransitRoute routeStart = findAvailableRouteStart(ctx, route);
                ExperimentalTransitRoute routeEnd = findAvailableRouteEnd(ctx, route);
                Leg newLeg1 = PopulationUtils.createLeg(leg);
                newLeg1.setRoute(routeStart);
                Leg newLeg2 = PopulationUtils.createLeg(leg);
                newLeg2.setRoute(routeEnd);
                if (isEmptyPlan) {
                    plan.addActivity(fromAct);
                }
                plan.addLeg(newLeg1);
                double delay = calcDelay(ctx, route, routeEnd, fromAct.getEndTime(), plan.getPerson());
                Activity outsideAct1 = createOutsideActivity(ctx, routeStart.getEndLinkId(), fromAct.getEndTime());
                Activity outsideAct2 = createOutsideActivity(ctx, routeEnd.getStartLinkId(), fromAct.getEndTime() + delay);

                plan.addActivity(outsideAct1);
                Leg teleportLeg = createOutsideLeg(ctx, routeStart.getEndLinkId(), routeEnd.getStartLinkId());
                plan.addLeg(teleportLeg);
                plan.addActivity(outsideAct2);
                plan.addLeg(newLeg2);
                plan.addActivity(toAct);
            }
        } else { // no outside links, so everything is inside
            if (isEmptyPlan) {
                plan.addActivity(fromAct);
            }
            plan.addLeg(leg);
            plan.addActivity(toAct);
        }
    }

    private ExperimentalTransitRoute findAvailableRouteEnd(CutContext ctx, ExperimentalTransitRoute ptRoute) {
        TransitSchedule srcSchedule = ctx.source.getTransitSchedule();
        TransitLine srcLine = srcSchedule.getTransitLines().get(ptRoute.getLineId());
        TransitRoute srcRoute = srcLine.getRoutes().get(ptRoute.getRouteId());

        TransitSchedule destSchedule = ctx.dest.getTransitSchedule();
        TransitLine destLine = destSchedule.getTransitLines().get(ptRoute.getLineId());
        TransitRoute destRoute = destLine.getRoutes().get(ptRoute.getRouteId());

        Id<TransitStopFacility> accessStop = ptRoute.getAccessStopId();
        Id<TransitStopFacility> egressStop = ptRoute.getEgressStopId();

        TransitRouteStop destAccessStop = null;
        TransitRouteStop destEgressStop = null;
        boolean isRoutePart = false;

        ListIterator<TransitRouteStop> stopIter = srcRoute.getStops().listIterator(srcRoute.getStops().size());
        while (stopIter.hasPrevious()) {
            TransitRouteStop stop = stopIter.previous();

            Id<TransitStopFacility> stopId = stop.getStopFacility().getId();
            if (stopId.equals(egressStop)) {
                isRoutePart = true;
            }
            if (isRoutePart) {
                boolean isAvailable = destSchedule.getFacilities().containsKey(stopId);
                if (isAvailable && destEgressStop == null) {
                    destEgressStop = stop;
                }
                destAccessStop = stop;
            }
            if (stopId.equals(accessStop)) {
                isRoutePart = false;
                if (destEgressStop != null) {
                    break;
                }
            }
        }

        if (destEgressStop != null) {
            return new ExperimentalTransitRoute(destAccessStop.getStopFacility(), destEgressStop.getStopFacility(), destLine.getId(), destRoute.getId());
        }

        return null;
    }

    private ExperimentalTransitRoute findAvailableRouteStart(CutContext ctx, ExperimentalTransitRoute ptRoute) {
        TransitSchedule srcSchedule = ctx.source.getTransitSchedule();
        TransitLine srcLine = srcSchedule.getTransitLines().get(ptRoute.getLineId());
        TransitRoute srcRoute = srcLine.getRoutes().get(ptRoute.getRouteId());

        TransitSchedule destSchedule = ctx.dest.getTransitSchedule();
        TransitLine destLine = destSchedule.getTransitLines().get(ptRoute.getLineId());
        TransitRoute destRoute = destLine.getRoutes().get(ptRoute.getRouteId());

        Id<TransitStopFacility> accessStop = ptRoute.getAccessStopId();
        Id<TransitStopFacility> egressStop = ptRoute.getEgressStopId();

        TransitRouteStop destAccessStop = null;
        TransitRouteStop destEgressStop = null;
        boolean isRoutePart = false;
        for (TransitRouteStop stop : srcRoute.getStops()) {
            Id<TransitStopFacility> stopId = stop.getStopFacility().getId();
            if (stopId.equals(accessStop)) {
                isRoutePart = true;
            }
            if (isRoutePart) {
                boolean isAvailable = destSchedule.getFacilities().containsKey(stopId);
                if (isAvailable && destAccessStop == null) {
                    destAccessStop = stop;
                }
                destEgressStop = stop;
            }
            if (stopId.equals(egressStop)) {
                isRoutePart = false;
                if (destAccessStop != null) {
                    break;
                }
            }
        }

        if (destAccessStop != null) {
            return new ExperimentalTransitRoute(destAccessStop.getStopFacility(), destEgressStop.getStopFacility(), destLine.getId(), destRoute.getId());
        }

        return null;
    }

    private ExperimentalTransitRoute findAvailableRoutePart(CutContext ctx, ExperimentalTransitRoute route) {
        // just take the first part we find, `findAvailableRouteStart` is flexible enough to handle even non-start cases.
        return findAvailableRouteStart(ctx, route);
    }

    private void removeEndTimesFromInteractionActivities(Plan plan) {
        plan.getPlanElements().stream()
                .filter(Activity.class::isInstance)
                .filter(a -> SBBActivities.stageActivityTypeList.contains(((Activity) a).getType()))
                .forEach(a -> ((Activity) a).setEndTime(Time.getUndefinedTime()));
    }

    private double calcDelay(CutContext ctx, ExperimentalTransitRoute fullRoute, ExperimentalTransitRoute shortenedRoute, double departureTime, Person p) {
        Id<TransitStopFacility> fullAccessId = fullRoute.getAccessStopId();
        Id<TransitStopFacility> shortenedAccessId = shortenedRoute.getAccessStopId();

        TransitRouteStop fullAccessStop = null;
        TransitRouteStop shortenedAccessStop = null;

        TransitLine line = ctx.source.getTransitSchedule().getTransitLines().get(fullRoute.getLineId());
        TransitRoute ptRoute = line.getRoutes().get(fullRoute.getRouteId());

        for (TransitRouteStop stop : ptRoute.getStops()) {
            Id<TransitStopFacility> stopId = stop.getStopFacility().getId();
            if (stopId.equals(fullAccessId)) {
                fullAccessStop = stop;
            }
            if (stopId.equals(shortenedAccessId)) {
                shortenedAccessStop = stop;
                break;
            }
        }

        if (fullAccessStop != null && shortenedAccessStop != null) {
            double shortenedDepartureOffset = Time.isUndefinedTime(shortenedAccessStop.getDepartureOffset()) ? shortenedAccessStop.getArrivalOffset() : shortenedAccessStop.getDepartureOffset();
            double fullDepartureOffset = Time.isUndefinedTime(fullAccessStop.getDepartureOffset()) ? fullAccessStop.getArrivalOffset() : fullAccessStop.getDepartureOffset();
            return shortenedDepartureOffset - fullDepartureOffset;
        }
        return 0;
    }

    private void addTeleportationLegToPlan(CutContext ctx, Plan plan, Activity fromAct, boolean fromActInside, Leg leg, Activity toAct, boolean toActInside) {
        boolean comingInside = !fromActInside && toActInside;
        boolean isPlanEmpty = plan.getPlanElements().isEmpty();

        if (comingInside) {
            Activity newFromAct = fromActInside ? fromAct : createOutsideActivity(ctx, fromAct);
            if (!isPlanEmpty) {
                Id<Link> fromLinkId = getLinkId(ctx, getLastActivity(ctx, plan));
                Id<Link> toLinkId = getLinkId(ctx, fromAct);
                plan.addLeg(createOutsideLeg(ctx, fromLinkId, toLinkId));
            }
            plan.addActivity(newFromAct);
        } else if (isPlanEmpty) {
            plan.addActivity(fromAct);
        }
        plan.addLeg(leg);
        Activity newToAct = toActInside ? toAct : createOutsideActivity(ctx, toAct);
        plan.addActivity(newToAct);
    }

    private Activity createOutsideActivity(CutContext ctx, Activity act) {
        Activity newAct = PopulationUtils.createActivity(act);
        newAct.setType(OUTSIDE_ACT_TYPE);
        newAct.setFacilityId(null);
        newAct.setLinkId(NetworkUtils.getNearestLink(ctx.dest.getNetwork(), act.getCoord()).getId());
        return newAct;
    }

    private Id<Link> getLinkId(CutContext ctx, Activity act) {
        Id<Link> linkId = act.getLinkId();
        Coord coord = act.getCoord();
        if (linkId == null) {
            ActivityFacility fac = ctx.source.getActivityFacilities().getFacilities().get(act.getFacilityId());
            coord = fac.getCoord();
            linkId = fac.getLinkId();
        }
        if (linkId == null) {
            linkId = NetworkUtils.getNearestLink(ctx.source.getNetwork(), coord).getId();
        }
        return linkId;
    }

    private boolean hasOutsideLinks(CutContext ctx, NetworkRoute route) {
        Network network = ctx.dest.getNetwork();
        if (!network.getLinks().containsKey(route.getStartLinkId())) {
            return true;
        }
        if (!network.getLinks().containsKey(route.getEndLinkId())) {
            return true;
        }
        for (Id<Link> linkId : route.getLinkIds()) {
            if (!network.getLinks().containsKey(linkId)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasOutsideLinks(CutContext ctx, ExperimentalTransitRoute route) {
        AgentState state = new AgentState();
        calcStateExtendedByTransitRoute(ctx, state, route);
        return state.hasOutside;
    }

    private void calcNetworkCapacityChanges(CutContext ctx, double demandFactor) {
        Map<Id<Link>, int[]> missingHourlyDemand = calcMissingHourlyDemand(ctx);
        List<NetworkChangeEvent> changeEvents = createNetworkChangeEvents(ctx, missingHourlyDemand, demandFactor);
        ctx.dest.addScenarioElement(MISSING_DEMAND, missingHourlyDemand);
        ctx.dest.addScenarioElement(CHANGE_EVENTS, changeEvents);
    }

    private Map<Id<Link>, int[]> calcMissingHourlyDemand(CutContext ctx) {
        Network srcNetwork = ctx.source.getNetwork();
        Set<Id<Link>> destLinks = ctx.dest.getNetwork().getLinks().keySet();
        Map<Id<Link>, int[]> missingHourlyDemand = ctx.missingHourlyDemand;
        for (Person p : ctx.source.getPopulation().getPersons().values()) {
            boolean isIncluded = ctx.relevantPersons.containsKey(p.getId());
            if (!isIncluded) {
                Activity lastAct = null;
                for (PlanElement pe : p.getSelectedPlan().getPlanElements()) {
                    if (pe instanceof Activity) {
                        lastAct = (Activity) pe;
                    }
                    if (pe instanceof Leg) {
                        Leg leg = (Leg) pe;
                        if (leg.getRoute() instanceof NetworkRoute) {
                            NetworkRoute route = (NetworkRoute) leg.getRoute();
                            double time = lastAct.getEndTime();
                            if (Time.isUndefinedTime(time)) {
                                time = leg.getDepartureTime();
                            }
                            int hour = (int) (time / 3600);
                            double travelTime = 2;// assume 2 seconds travel time on the start link, as agents basically only have to pass the to-node
                            if (hour >= 0 && hour < 24 && destLinks.contains(route.getStartLinkId())) {
                                missingHourlyDemand.computeIfAbsent(route.getStartLinkId(), k -> new int[24])[hour]++;
                            }
                            time += travelTime;
                            for (Id<Link> linkId : route.getLinkIds()) {
                                hour = (int) (time / 3600);
                                Link link = srcNetwork.getLinks().get(linkId);
                                travelTime = ctx.travelTime.getLinkTravelTime(link, time, p, null);
                                if (hour >= 0 && hour < 24 && destLinks.contains(link.getId())) {
                                    missingHourlyDemand.computeIfAbsent(link.getId(), k -> new int[24])[hour]++;
                                }
                                time += travelTime;
                            }
                            // ignore the end link, as a vehicle does not consume any flow-capacity on the last link of a leg
                        }
                    }
                }
            }
        }
        return missingHourlyDemand;
    }

    private List<NetworkChangeEvent> createNetworkChangeEvents(CutContext ctx, Map<Id<Link>, int[]> missingHourlyDemand, double missingDemandFactor) {
        List<NetworkChangeEvent> changeEvents = new ArrayList<>();

        Network srcNetwork = ctx.source.getNetwork();
        int lastValue = 0;
        for (Map.Entry<Id<Link>, int[]> e : missingHourlyDemand.entrySet()) {
            Id<Link> linkId = e.getKey();
            Link link = srcNetwork.getLinks().get(linkId);
            int[] values = e.getValue();
            for (int hour = 0, n = values.length; hour < n; hour++) {
                int value = values[hour];
                if (value != lastValue) {
                    double newCapacity = link.getCapacity() - value * missingDemandFactor;
                    NetworkChangeEvent event = new NetworkChangeEvent(hour * 3600);
                    event.addLink(link);
                    ChangeValue change = new ChangeValue(ChangeType.ABSOLUTE_IN_SI_UNITS, newCapacity);
                    event.setFlowCapacityChange(change);
                    changeEvents.add(event);
                }
                lastValue = value;
            }
        }

        return changeEvents;
    }

    private void filterFacilities(CutContext ctx) {
        ActivityFacilities srcFacilities = ctx.source.getActivityFacilities();
        ActivityFacilities destFacilities = ctx.dest.getActivityFacilities();
        for (Person p : ctx.dest.getPopulation().getPersons().values()) {
            Plan plan = p.getSelectedPlan();
            for (PlanElement pe : plan.getPlanElements()) {
                if (pe instanceof Activity) {
                    Activity act = (Activity) pe;
                    Id<ActivityFacility> facId = act.getFacilityId();
                    if (facId != null && !destFacilities.getFacilities().containsKey(facId)) {
                        destFacilities.addActivityFacility(srcFacilities.getFacilities().get(facId));
                    }
                }
            }
        }
    }

    private void printStats(CutContext ctx) {
        log.info("all persons = " + ctx.source.getPopulation().getPersons().size() +
                ", relevant persons = " + ctx.relevantPersons.size() +
                ", relevant share = " + ((double) ctx.relevantPersons.size() / ctx.source.getPopulation().getPersons().size()) +
                ", fully inside persons = " + ctx.fullyInsidePersons.size() +
                ", fully inside share = " + (((double) ctx.fullyInsidePersons.size()) / ctx.relevantPersons.size()) +
                ", cut persons = " + ctx.cutPersons.size() +
                ", cut share = " + ((double) ctx.cutPersons.size()) / ctx.relevantPersons.size());
    }

    private static class AgentState {
        boolean hasInside = false;
        boolean hasOutside = false;

        void reset() {
            this.hasInside = false;
            this.hasOutside = false;
        }
    }

    private static class CutContext {
        private final Scenario source;
        private final Scenario dest;
        private final CutExtent extent;
        private final CutExtent extendedExtent;
        private final CutExtent networkExtent;
        private final TravelTime travelTime;
        private final Map<Id<Person>, Person> relevantPersons = new HashMap<>();
        private final Map<Id<Person>, Person> fullyInsidePersons = new HashMap<>();
        private final Map<Id<Person>, Person> partiallyInsidePersons = new HashMap<>();
        private final Map<Id<Person>, Person> cutPersons = new HashMap<>();
        private final Map<Id<Node>, Boolean> insideNodes = new HashMap<>();
        private final Map<Id<Node>, Boolean> extendedInsideNodes = new HashMap<>();
        private final Map<Id<Node>, Boolean> networkInsideNodes = new HashMap<>();
        private final Map<Id<Link>, int[]> missingHourlyDemand = new HashMap<>();
        private final List<Coord> relevantActivityCoords = new ArrayList<>();

        CutContext(Scenario source, TravelTime travelTime, CutExtent extent, CutExtent extendedExtent, CutExtent networkExtent) {
            this.source = source;
            this.travelTime = travelTime;
            this.dest = ScenarioUtils.createScenario(source.getConfig());
            this.extent = extent;
            this.extendedExtent = extendedExtent;
            this.networkExtent = networkExtent;
            this.dest.addScenarioElement(RELEVANT_ACT_LOCATIONS, this.relevantActivityCoords);
        }
    }

    private Activity createOutsideActivity(CutContext ctx, Id<Link> linkId, double endTime) {
        Link link = ctx.dest.getNetwork().getLinks().get(linkId);
        Activity newAct = PopulationUtils.createActivityFromCoordAndLinkId(OUTSIDE_ACT_TYPE, link.getCoord(), linkId);
        newAct.setEndTime(endTime);
        return newAct;
    }

    private void usePerson(CutContext ctx, Person srcP) {
        Thread.currentThread().setName("Person " + srcP.getId());
        Person destP = ctx.dest.getPopulation().getFactory().createPerson(srcP.getId());
        AttributesUtils.copyAttributesFromTo(srcP, destP);
        ObjectAttributesUtils.copyAllAttributes(ctx.source.getPopulation().getPersonAttributes(), ctx.dest.getPopulation().getPersonAttributes(), srcP.getId().toString());
        Plan plan = cutPlan(ctx, destP, srcP.getSelectedPlan());
        if (planWasCut(plan)) {
            ctx.cutPersons.put(destP.getId(), destP);
            destP.getAttributes().putAttribute(CUT_ATTRIBUTE, true);
            ctx.dest.getPopulation().getPersonAttributes().putAttribute(destP.getId().toString(), "subpopulation", OUTSIDE_AGENT_SUBPOP);
        }
        ctx.dest.getPopulation().addPerson(destP);
    }
    private static void simpleCleanNetwork(Network network) {
        List<Node> emptyNodes = new ArrayList<>();
        for (Node node : network.getNodes().values()) {
            if (node.getOutLinks().isEmpty() && node.getInLinks().isEmpty()) {
                emptyNodes.add(node);
            }
        }
        log.info("# Empty nodes: " + emptyNodes.size());
        for (Node node : emptyNodes) {
            network.removeNode(node.getId());
        }
    }

    private static void writeMissingDemand(File file, Scenario cutScenario) throws IOException {
        Map<Id<Link>, int[]> missingHourlyDemand = (Map<Id<Link>, int[]>) cutScenario.getScenarioElement(MISSING_DEMAND);
        String[] columns = new String[] {"LINK", "MISSING"};
        try (CSVWriter out = new CSVWriter(null, columns, file.getAbsolutePath())) {
            for (Map.Entry<Id<Link>, int[]> e : missingHourlyDemand.entrySet()) {
                Id<Link> linkId = e.getKey();
                int[] values = e.getValue();
                int sum = 0;
                for (int v : values) {
                    sum += v;
                }
                out.set("LINK", linkId.toString());
                out.set("MISSING", Integer.toString(sum));
                out.writeRow();
            }
        }
    }

    private static void writeRelevantLocations(File file, List<Coord> coords) throws IOException {
        String[] columns = new String[] { "X", "Y"};
        try (CSVWriter out = new CSVWriter(null, columns, file.getAbsolutePath())) {
            for (Coord c : coords) {
                out.set("X", Double.toString(c.getX()));
                out.set("Y", Double.toString(c.getY()));
                out.writeRow();
            }
        }
    }

    private double calcDelay(CutContext ctx, NetworkRoute fullRoute, NetworkRoute shortenedRoute, double departureTime, Person p) {
        if (Time.isUndefinedTime(departureTime)) {
            throw new RuntimeException("Departure Time is not provided / undefined, but required for travel time calculations");
        }
        double delay = 0;
        Id<Link> startLinkId = shortenedRoute.getStartLinkId();
        if (fullRoute.getStartLinkId().equals(startLinkId)) {
            return delay;
        }
        delay += 2; // the first link is not travelled in QSim, only the to-node has to be crossed, assume 2 seconds
        Network sourceNetwork = ctx.source.getNetwork();
        for (Id<Link> linkId : fullRoute.getLinkIds()) {
            if (linkId.equals(startLinkId)) {
                return delay;
            }
            Link link = sourceNetwork.getLinks().get(linkId);
            delay += ctx.travelTime.getLinkTravelTime(link, departureTime + delay, p, null);
        }
        return delay;
    }
}

