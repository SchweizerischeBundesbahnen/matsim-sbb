package ch.sbb.matsim.preparation.cutter;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.*;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.routes.ExperimentalTransitRoute;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.utils.objectattributes.ObjectAttributesUtils;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;
import org.matsim.utils.objectattributes.attributable.AttributesUtils;
import org.matsim.vehicles.*;

import java.io.File;
import java.util.*;

public class ScenarioCutter {

    private final static Logger log = Logger.getLogger(ScenarioCutter.class);
    private final Scenario source;

    public ScenarioCutter(Scenario scenario) {
        this.source = scenario;
    }

    public Scenario performCut(CutExtent extent, double outsideShare) {
        CutContext ctx = new CutContext(this.source, extent);

        filterPersons(ctx);
        calcOutsideBoundary(ctx, outsideShare);
        cutNetwork(ctx);
        cutTransit(ctx);
        cutPersons(ctx);
        // TODO: adapt network capacities

        return ctx.dest;
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
                Route route = leg.getRoute();
                if (route instanceof NetworkRoute) {
                    NetworkRoute netRoute = (NetworkRoute) route;
                    calcStateByNetworkRoute(ctx, state, netRoute);
                }
                if (route instanceof ExperimentalTransitRoute) {
                    ExperimentalTransitRoute ptRoute = (ExperimentalTransitRoute) route;
                    calcStateByTransitRoute(ctx, state, ptRoute);
                }
                if (state.hasInside && state.hasOutside) {
                    break;
                }
            }
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

    private boolean isNodeInside(CutContext ctx, Node node) {
        return ctx.insideNodes.computeIfAbsent(node.getId(), id -> ctx.extent.isInside(node.getCoord()));
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

    private void calcOutsideBoundary(CutContext ctx, double outsideShare) {
        printStats(ctx);
        double share = ((double) (ctx.relevantPersons.size() - ctx.fullyInsidePersons.size()) / ctx.relevantPersons.size());

        // TODO
        ctx.extendedExtent = ctx.extent;
    }

    private void cutNetwork(CutContext ctx) {
        Network destNet = ctx.dest.getNetwork();
        for (Link link : ctx.source.getNetwork().getLinks().values()) {
            boolean isInside = isNodeInside(ctx, link.getFromNode()) || isNodeInside(ctx, link.getToNode());
            if (isInside) {
                copyLink(link, destNet);
            }
        }
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
        cutSchedule(ctx);
        filterTransitVehicles(ctx);
        filterMinTransferTimes(ctx);
        addMissingTransitLinks(ctx);
    }

    private void cutSchedule(CutContext ctx) {
        TransitSchedule schedule = ctx.dest.getTransitSchedule();

        TransitSchedule source = ctx.source.getTransitSchedule();
        Set<TransitStopFacility> insideStops = new HashSet<>();
        for (TransitStopFacility stop : source.getFacilities().values()) {
            if (ctx.extendedExtent.isInside(stop.getCoord())) {
                insideStops.add(stop);
            }
        }

        for (TransitLine line : source.getTransitLines().values()) {
            for (TransitRoute route : line.getRoutes().values()) {
                boolean hasInsideStops = false;
                for (TransitRouteStop routeStop : route.getStops()) {
                    if (insideStops.contains(routeStop.getStopFacility())) {
                        hasInsideStops = true;
                        break;
                    }
                }
                if (hasInsideStops) {
                    copyTransitRoute(line, route, source, schedule);
                }
            }
        }
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

    private void copyTransitRoute(TransitLine line, TransitRoute route, TransitSchedule source, TransitSchedule dest) {
        TransitScheduleFactory f = dest.getFactory();

        TransitLine destLine = dest.getTransitLines().get(line.getId());
        if (destLine == null) {
            destLine = f.createTransitLine(line.getId());
            destLine.setName(line.getName());
            AttributesUtils.copyAttributesFromTo(line, destLine);
            dest.addTransitLine(destLine);
            ObjectAttributesUtils.copyAllAttributes(source.getTransitLinesAttributes(), dest.getTransitLinesAttributes(), line.getId().toString());
        }
        List<TransitRouteStop> destStops = new ArrayList<>(route.getStops().size());
        for (TransitRouteStop srcStop : route.getStops()) {
            TransitStopFacility srcFacility = srcStop.getStopFacility();
            TransitStopFacility destFacility = dest.getFacilities().get(srcFacility.getId());
            if (destFacility == null) {
                destFacility = f.createTransitStopFacility(srcFacility.getId(), srcFacility.getCoord(), srcFacility.getIsBlockingLane());
                destFacility.setLinkId(srcFacility.getLinkId());
                destFacility.setName(srcFacility.getName());
                destFacility.setStopAreaId(srcFacility.getStopAreaId());
                AttributesUtils.copyAttributesFromTo(srcFacility, destFacility);
                dest.addStopFacility(destFacility);
                ObjectAttributesUtils.copyAllAttributes(source.getTransitStopsAttributes(), dest.getTransitStopsAttributes(), srcFacility.getId().toString());
            }
            TransitRouteStop destStop = f.createTransitRouteStop(destFacility, srcStop.getArrivalOffset(), srcStop.getDepartureOffset());
            destStop.setAwaitDepartureTime(srcStop.isAwaitDepartureTime());
            destStops.add(destStop);
        }
        TransitRoute destRoute = f.createTransitRoute(route.getId(), route.getRoute(), destStops, route.getTransportMode());
        destRoute.setDescription(route.getDescription());
        AttributesUtils.copyAttributesFromTo(route, destRoute);
        destLine.addRoute(destRoute);

        for (Departure d : route.getDepartures().values()) {
            Departure destD = f.createDeparture(d.getId(), d.getDepartureTime());
            destD.setVehicleId(d.getVehicleId());
            AttributesUtils.copyAttributesFromTo(d, destD);
            destRoute.addDeparture(destD);
        }
        // TODO cut route to extended extent
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
        Population dest = ctx.dest.getPopulation();
        for (Person p : ctx.source.getPopulation().getPersons().values()) {
            if (ctx.relevantPersons.containsKey(p.getId())) {
                dest.addPerson(p); // TODO actually cut the persons
            }
        }
    }

    private void printStats(CutContext ctx) {
        log.info("relevant persons = " + ctx.relevantPersons.size() +
                ", fully inside persons = " + ctx.fullyInsidePersons.size() +
                ", outside share = " + ((double) (ctx.relevantPersons.size() - ctx.fullyInsidePersons.size()) / ctx.relevantPersons.size()));
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
        private CutExtent extendedExtent = null;
        private final Map<Id<Person>, Person> relevantPersons = new HashMap<>();
        private final Map<Id<Person>, Person> fullyInsidePersons = new HashMap<>();
        private final Map<Id<Person>, Person> partiallyInsidePersons = new HashMap<>();
        private final Map<Id<Person>, Person> cutPersons = new HashMap<>();
        private final Map<Id<Node>, Boolean> insideNodes = new HashMap<>();

        CutContext(Scenario source, CutExtent extent) {
            this.source = source;
            this.dest = ScenarioUtils.createScenario(source.getConfig());
            this.extent = extent;
        }
    }

    public static void main(String[] args) {
        System.setProperty("matsim.preferLocalDtds", "true");

        args = new String[] {
            "C:\\devsbb\\codes\\_data\\CH2016_1.2.17\\CH.10pct.2016.output_config_cutter.xml",
            "0.1",
            "C:\\devsbb\\codes\\_data\\CH2016_1.2.17_cut"
        };

        String configFilename = args[0];
        double scenarioSampleSize = Double.parseDouble(args[1]);
        String outputDirectoryname = args[2];

        Thread ramObserver = new Thread(() -> {
            while (true) {
                Gbl.printMemoryUsage();
                try {
                    Thread.sleep(15_000);
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
        if (config.transit().getTransitLinesAttributesFile() != null) {
            new ObjectAttributesXmlReader(scenario.getTransitSchedule().getTransitLinesAttributes()).readFile(config.transit().getTransitLinesAttributesFile());
        }
        new VehicleReaderV1(scenario.getTransitVehicles()).readFile(config.transit().getVehiclesFile());
        BetterPopulationReader.readSelectedPlansOnly(scenario, new File(config.plans().getInputFile()));
        if (config.plans().getInputPersonAttributeFile() != null) {
            new ObjectAttributesXmlReader(scenario.getPopulation().getPersonAttributes()).readFile(config.plans().getInputPersonAttributeFile());
        }

        CutExtent extent = new RadialExtent(600_000, 200_000, 10_000);
        Scenario cutScenario = new ScenarioCutter(scenario).performCut(extent, 0.05);

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

        // TODO write network change events, personAttributes (subpopulations)
    }
}
