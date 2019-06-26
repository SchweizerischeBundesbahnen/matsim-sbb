package ch.sbb.matsim.preparation.cutter;

import ch.sbb.matsim.csv.CSVWriter;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.*;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.network.NetworkChangeEvent;
import org.matsim.core.network.NetworkChangeEvent.ChangeType;
import org.matsim.core.network.NetworkChangeEvent.ChangeValue;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkChangeEventsWriter;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.misc.Time;
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

public class ScenarioCutter {

    private final static Logger log = Logger.getLogger(ScenarioCutter.class);
    private final Scenario source;

    private final static String CHANGE_EVENTS = "NetworkChangeEvents";
    private final static String MISSING_DEMAND = "HourlyMissingDemand";
    private final static String OUTSIDE_LEG_MODE = "outside";

    public ScenarioCutter(Scenario scenario) {
        this.source = scenario;
    }

    public Scenario performCut(CutExtent extent, double outsideShare, double populationSample) {
        CutContext ctx = new CutContext(this.source, extent);
        double demandFactor = 1 / populationSample;

        filterPersons(ctx);
        calcOutsideBoundary(ctx, outsideShare);
        cutNetwork(ctx);
        cutTransit(ctx);
        cutPersons(ctx);
        calcNetworkCapacityChanges(ctx, demandFactor);

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

    private void calcOutsideBoundary(CutContext ctx, double outsideShare) {
        printStats(ctx);
        double share = ((double) (ctx.relevantPersons.size() - ctx.fullyInsidePersons.size()) / ctx.relevantPersons.size());

        // TODO
        ctx.extendedExtent = new RadialExtent(600_000, 200_000, 15_000);
    }

    private void cutNetwork(CutContext ctx) {
        Network destNet = ctx.dest.getNetwork();
        for (Link link : ctx.source.getNetwork().getLinks().values()) {
            boolean isInside = isNodeInsideExtended(ctx, link.getFromNode()) || isNodeInsideExtended(ctx, link.getToNode());
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
                TransitRouteStop prevStop = null;
                TransitRouteStop fromStop = null;
                TransitRouteStop toStop = null;
                boolean includeNextStop = false;
                for (TransitRouteStop routeStop : route.getStops()) {
                    if (includeNextStop) {
                        toStop = routeStop;
                        includeNextStop = false;
                    }
                    boolean isInside = insideStops.contains(routeStop.getStopFacility());
                    if (isInside) {
                        if (fromStop == null) {
                            fromStop = prevStop == null ? routeStop : prevStop;
                        }
                        toStop = routeStop;
                        includeNextStop = true;
                    }
                    prevStop = routeStop;
                }
                if (fromStop != null) {
                    cutTransitRoute(line, route, fromStop, toStop, source, schedule);
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

    private void cutTransitRoute(TransitLine line, TransitRoute route, TransitRouteStop fromStop, TransitRouteStop toStop, TransitSchedule source, TransitSchedule dest) {
        TransitScheduleFactory f = dest.getFactory();

        TransitLine destLine = dest.getTransitLines().get(line.getId());
        if (destLine == null) {
            destLine = f.createTransitLine(line.getId());
            destLine.setName(line.getName());
            AttributesUtils.copyAttributesFromTo(line, destLine);
            dest.addTransitLine(destLine);
            ObjectAttributesUtils.copyAllAttributes(source.getTransitLinesAttributes(), dest.getTransitLinesAttributes(), line.getId().toString());
        }
        double offset = fromStop.getDepartureOffset();
        if (Time.isUndefinedTime(offset)) {
            offset = fromStop.getArrivalOffset();
        }
        List<TransitRouteStop> destStops = new ArrayList<>(route.getStops().size());
        boolean include = false;
        for (TransitRouteStop srcStop : route.getStops()) {
            boolean isFirst = srcStop == fromStop;
            boolean isLast = srcStop == toStop;
            if (isFirst) {
                include = true;
            }
            if (include) {
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
                TransitRouteStop destStop = f.createTransitRouteStop(destFacility, isFirst ? Time.getUndefinedTime() : adaptOffset(srcStop.getArrivalOffset(), offset), isLast ? Time.getUndefinedTime() : adaptOffset(srcStop.getDepartureOffset(), offset));
                destStop.setAwaitDepartureTime(srcStop.isAwaitDepartureTime());
                destStops.add(destStop);
            }
            if (isLast) {
                break;
            }
        }
        NetworkRoute netRoute = cutNetworkRoute(route, fromStop, toStop);
        TransitRoute destRoute = f.createTransitRoute(route.getId(), netRoute, destStops, route.getTransportMode());
        destRoute.setDescription(route.getDescription());
        AttributesUtils.copyAttributesFromTo(route, destRoute);
        destLine.addRoute(destRoute);

        for (Departure d : route.getDepartures().values()) {
            Departure destD = f.createDeparture(d.getId(), d.getDepartureTime() + offset);
            destD.setVehicleId(d.getVehicleId());
            AttributesUtils.copyAttributesFromTo(d, destD);
            destRoute.addDeparture(destD);
        }
    }

    private static double adaptOffset(double srcOffset, double shift) {
        if (Time.isUndefinedTime(srcOffset)) {
            return srcOffset;
        }
        return srcOffset - shift;
    }

    private NetworkRoute cutNetworkRoute(TransitRoute route, TransitRouteStop fromStop, TransitRouteStop toStop) {
        Iterator<TransitRouteStop> stopIter = route.getStops().iterator();
        TransitRouteStop nextStop = stopIter.next();
        boolean include = false;

        while (nextStop != fromStop && stopIter.hasNext()) {
            nextStop = stopIter.next();
        }
        Id<Link> nextStopLink = nextStop == null ? null : nextStop.getStopFacility().getLinkId();

        List<Id<Link>> requiredLinks = new ArrayList<>();
        NetworkRoute netRoute = route.getRoute();
        Id<Link> startLinkId = netRoute.getStartLinkId();

        if (startLinkId.equals(nextStopLink)) {
            include = true;
            requiredLinks.add(startLinkId);
            do {
                nextStop = stopIter.hasNext() ? stopIter.next() : null;
                nextStopLink = nextStop == null ? null : nextStop.getStopFacility().getLinkId();
            } while (startLinkId.equals(nextStopLink));
        }

        for (Id<Link> linkId : netRoute.getLinkIds()) {
            boolean isStopLink = linkId.equals(nextStopLink);
            if (isStopLink || include) {
                requiredLinks.add(linkId);
            }
            if (isStopLink) {
                include = true;
                if (nextStop == toStop) {
                    include = false;
                    break;
                }
                do {
                    nextStop = stopIter.hasNext() ? stopIter.next() : null;
                    nextStopLink = nextStop == null ? null : nextStop.getStopFacility().getLinkId();
                } while (linkId.equals(nextStopLink));
            }
            if (nextStopLink == null) {
                include = false;
                break;
            }
        }
        if (include) {
            requiredLinks.add(netRoute.getEndLinkId());
        }
        return RouteUtils.createNetworkRoute(requiredLinks, null);
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
                usePerson(ctx, p);
            }
        }
    }

    private void usePerson(CutContext ctx, Person srcP) {
        Person destP = ctx.dest.getPopulation().getFactory().createPerson(srcP.getId());
        destP.addPlan(cutPlan(ctx, srcP.getSelectedPlan()));
        ctx.dest.getPopulation().addPerson(destP);
    }

    private Plan cutPlan(CutContext ctx, Plan srcPlan) {
        Activity fromAct = null;
        Activity toAct = null;
        Leg leg = null;
        boolean fromActInside = false;
        boolean toActInside = false;
        boolean fromActAdded = false;
        boolean toActAdded = false;
        boolean legInside = false;

        Plan plan = ctx.dest.getPopulation().getFactory().createPlan();

        List<PlanElement> srcPlanElements = srcPlan.getPlanElements();
        AgentState state = new AgentState();
        for (PlanElement pe : srcPlanElements) {
            if (pe instanceof Activity) {
                fromAct = toAct;
                fromActInside = toActInside;
                fromActAdded = toActAdded;

                toAct = (Activity) pe;
                toActInside = ctx.extendedExtent.isInside(toAct.getCoord());

                if (!fromActInside && !toActInside) {
                    calcStateExtendedByRoute(ctx, state, leg.getRoute());
                    legInside = state.hasInside;
                }

                if (fromActInside || toActInside || legInside) {
                    addLegToPlan(ctx, plan, fromAct, leg, toAct, fromActAdded);
                    toActAdded = true;
                }
            }
            if (pe instanceof Leg) {
                leg = (Leg) pe;
            }
        }

        // TODO think about network consistency, when moving activities, e.g. if one activity is outside, agent must be able to get there and back with "car"
        //      maybe add some connections to the network for this?

        return plan;
    }

    private void addLegToPlan(CutContext ctx, Plan plan, Activity fromAct, Leg leg, Activity toAct, boolean fromActAdded) {
        if (!fromActAdded) {
            if (!plan.getPlanElements().isEmpty()) {
                // looks like there was another activity outside we skipped, add an outside-travel leg in between
                Leg outsideLeg = PopulationUtils.createLeg(OUTSIDE_LEG_MODE);
                plan.addLeg(outsideLeg);
            }
            // and add the fromAct
            plan.addActivity(fromAct); // TODO adapt location
        }
        plan.addLeg(adaptLeg(ctx, leg));
        plan.addActivity(toAct);
    }

    /**
     * Returns a new leg, with the route potentially shortened to make sure
     * only links and transit stops that are available in the cut scenario
     * are used. Supports adapting NetworkRoute and ExperimentalTransitRoute.
     */
    private Leg adaptLeg(CutContext ctx, Leg leg) {
        Network network = ctx.dest.getNetwork();
        Route route = leg.getRoute();
        if (route instanceof NetworkRoute) {
            List<Id<Link>> linkIds = new ArrayList<>();
            boolean isInside = false;
            if (network.getLinks().containsKey(route.getStartLinkId())) {
                linkIds.add(route.getStartLinkId());
                isInside = true;
            }
            for (Id<Link> linkId : ((NetworkRoute) route).getLinkIds()) {
                boolean hasLink = network.getLinks().containsKey(linkId);
                if (hasLink) {
                    linkIds.add(linkId);
                    isInside = true;
                } else if (isInside) {
                    break; // we left the area
                }
            }
            NetworkRoute cutRoute = RouteUtils.createNetworkRoute(linkIds, null);
            Leg cutLeg = PopulationUtils.createLeg(leg);
            cutLeg.setRoute(cutRoute);
            return cutLeg;
        }
        if (route instanceof ExperimentalTransitRoute) {
            ExperimentalTransitRoute ptRoute = (ExperimentalTransitRoute) route;
            TransitLine line = ctx.dest.getTransitSchedule().getTransitLines().get(ptRoute.getLineId());
            TransitRoute transitRoute = line.getRoutes().get(ptRoute.getRouteId());
            TransitRouteStop fromStop = null;
            TransitRouteStop toStop = null;
            for (TransitRouteStop routeStop : transitRoute.getStops()) {
                if (fromStop == null) {
                    // initially assume we board at the first available stop
                    fromStop = routeStop;
                }
                if (routeStop.getStopFacility().getId().equals(ptRoute.getAccessStopId())) {
                    // okay, here the agent actually boards
                    fromStop = routeStop;
                }
                if (routeStop.getStopFacility().getId().equals(ptRoute.getEgressStopId())) {
                    // okay, here the agent actually alights
                    toStop = routeStop;
                    break;
                }
                toStop = routeStop;
            }
            ExperimentalTransitRoute cutPtRoute = new ExperimentalTransitRoute(fromStop.getStopFacility(), toStop.getStopFacility(), line.getId(), transitRoute.getId());
            Leg cutLeg = PopulationUtils.createLeg(leg);
            cutLeg.setRoute(cutPtRoute);
        }
        return leg;
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
                            Link link = srcNetwork.getLinks().get(route.getStartLinkId());
                            double travelTime = link.getLength() / link.getFreespeed();
                            if (hour >= 0 && hour < 24 && destLinks.contains(link.getId())) {
                                missingHourlyDemand.computeIfAbsent(link.getId(), k -> new int[24])[hour]++;
                            }
                            time += travelTime;
                            for (Id<Link> linkId : route.getLinkIds()) {
                                hour = (int) (time / 3600);
                                link = srcNetwork.getLinks().get(linkId);
                                travelTime = link.getLength() / link.getFreespeed();
                                if (hour >= 0 && hour < 24 && destLinks.contains(link.getId())) {
                                    missingHourlyDemand.computeIfAbsent(link.getId(), k -> new int[24])[hour]++;
                                }
                                time += travelTime;
                            }
                            hour = (int) (time / 3600);
                            link = srcNetwork.getLinks().get(route.getEndLinkId());
                            if (hour >= 0 && hour < 24 && destLinks.contains(link.getId())) {
                                missingHourlyDemand.computeIfAbsent(link.getId(), k -> new int[24])[hour]++;
                            }
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
        private final Map<Id<Node>, Boolean> extendedInsideNodes = new HashMap<>();
        private final Map<Id<Link>, int[]> missingHourlyDemand = new HashMap<>();

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
        if (config.transit().getTransitLinesAttributesFile() != null) {
            new ObjectAttributesXmlReader(scenario.getTransitSchedule().getTransitLinesAttributes()).readFile(config.transit().getTransitLinesAttributesFile());
        }
        new VehicleReaderV1(scenario.getTransitVehicles()).readFile(config.transit().getVehiclesFile());
        BetterPopulationReader.readSelectedPlansOnly(scenario, new File(config.plans().getInputFile()));
        if (config.plans().getInputPersonAttributeFile() != null) {
            new ObjectAttributesXmlReader(scenario.getPopulation().getPersonAttributes()).readFile(config.plans().getInputPersonAttributeFile());
        }

        CutExtent extent = new RadialExtent(600_000, 200_000, 10_000);
        Scenario cutScenario = new ScenarioCutter(scenario).performCut(extent, 0.05, 0.1);

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

        List<NetworkChangeEvent> changeEvents = (List<NetworkChangeEvent>) cutScenario.getScenarioElement(CHANGE_EVENTS);
        new NetworkChangeEventsWriter().write(new File(outputDir, "networkChangeEvents.xml.gz").getAbsolutePath(), changeEvents);

        writeMissingDemand(new File(outputDir, "missingDemand.csv"), cutScenario);

        // TODO write personAttributes (subpopulations)
        // TODO Facilities
    }

    private static void writeMissingDemand(File file, Scenario cutScenario) {
        Map<Id<Link>, int[]> missingHourlyDemand = (Map<Id<Link>, int[]>) cutScenario.getScenarioElement("MissingDemand");
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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
