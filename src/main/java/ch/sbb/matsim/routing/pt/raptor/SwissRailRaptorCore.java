/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData.RRoute;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData.RRouteStop;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData.RTransfer;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * The actual RAPTOR implementation, based on Delling et al, Round-Based Public Transit Routing.
 *
 * This class is <b>NOT</b> thread-safe due to the use of internal state during the route calculation.
 *
 * @author mrieser / SBB
 */
public class SwissRailRaptorCore {

    private final SwissRailRaptorData data;
    private final RaptorConfig config;

    private final PathElement[] arrivalPathPerRouteStop;
    private final double[] egressCostsPerRouteStop;
    private final BitSet improvedRouteStopIndices;
    private final BitSet reachedRouteStopIndices;
    private final BitSet destinationRouteStopIndices;
    private double bestArrivalCost = Double.POSITIVE_INFINITY;

    public SwissRailRaptorCore(SwissRailRaptorData data) {
        this.data = data;
        this.config = data.config;
        this.arrivalPathPerRouteStop = new PathElement[data.countRouteStops];
        this.egressCostsPerRouteStop = new double[data.countRouteStops];
        this.improvedRouteStopIndices = new BitSet(this.data.countRouteStops);
        this.reachedRouteStopIndices = new BitSet(this.data.countRouteStops);
        this.destinationRouteStopIndices = new BitSet(this.data.countRouteStops);
    }

    private void reset() {
        Arrays.fill(this.arrivalPathPerRouteStop, null);
        Arrays.fill(this.egressCostsPerRouteStop, Double.POSITIVE_INFINITY);
        this.improvedRouteStopIndices.clear();
        this.reachedRouteStopIndices.clear();
        this.destinationRouteStopIndices.clear();
        this.bestArrivalCost = Double.POSITIVE_INFINITY;
    }

    public RaptorRoute calcLeastCostRoute(double depTime, List<InitialStop> accessStops, List<InitialStop> egressStops) {
        final int maxTransfers = 10; // TODO make configurable
        final int maxTransfersAfterFirstArrival = 2;

        reset();

        Map<TransitStopFacility, InitialStop> destinationStops = new HashMap<>();
        for (InitialStop egressStop : egressStops) {
            destinationStops.put(egressStop.stop, egressStop);
            int[] routeStopIndices = this.data.routeStopsPerStopFacility.get(egressStop.stop);
            if (routeStopIndices != null) {
                for (int routeStopIndex : routeStopIndices) {
                    this.destinationRouteStopIndices.set(routeStopIndex);
                    this.egressCostsPerRouteStop[routeStopIndex] = egressStop.accessCost;
                }
            }
        }

        for (InitialStop stop : accessStops) {
            int[] routeStopIndices = this.data.routeStopsPerStopFacility.get(stop.stop);
            for (int routeStopIndex : routeStopIndices) {
                this.improvedRouteStopIndices.set(routeStopIndex);
                RRouteStop toRouteStop = this.data.routeStops[routeStopIndex];
                this.arrivalPathPerRouteStop[routeStopIndex] = new PathElement(null, toRouteStop, depTime + stop.accessTime, stop.accessCost, 0, true);
            }
        }

        int allowedTransfersLeft = maxTransfersAfterFirstArrival;
        // the main loop
        for (int k = 0; k <= maxTransfers; k++) {
            // first stage (according to paper) is to set earliestArrivalTime_k(stop) = earliestArrivalTime_k-1(stop)
            // but because we re-use the earliestArrivalTime-array, we don't have to do anything.

            // second stage: process routes
            exploreRoutes();

            if (Double.isFinite(this.bestArrivalCost)) {
                if (allowedTransfersLeft == 0) {
                    // this will force the end of the loop
                    this.reachedRouteStopIndices.clear();
                }
                allowedTransfersLeft--;
            }

            this.improvedRouteStopIndices.clear();

            // third stage (according to paper): handle footpaths / transfers
            handleTransfers();

            // final stage: check stop criterion
            if (this.improvedRouteStopIndices.isEmpty()) {
                break;
            }
        }

        // create RaptorRoute based on PathElements
        PathElement leastCostPath = findLeastCostArrival(destinationStops);
        RaptorRoute raptorRoute = createRaptorRoute(leastCostPath, depTime);
        return raptorRoute;
    }

    private void exploreRoutes() {
        this.reachedRouteStopIndices.clear();

        int routeIndex = -1;
        for (int firstRouteStopIndex = this.improvedRouteStopIndices.nextSetBit(0); firstRouteStopIndex >= 0; firstRouteStopIndex = this.improvedRouteStopIndices.nextSetBit(firstRouteStopIndex+1)) {
            RRouteStop firstRouteStop = this.data.routeStops[firstRouteStopIndex];
            if (firstRouteStop.transitRouteIndex == routeIndex) {
                continue; // we've handled this route already
            }
            routeIndex = firstRouteStop.transitRouteIndex;

            // for each relevant route, step along route and look for new/improved connections
            RRoute route = this.data.routes[routeIndex];

            // firstRouteStop is the first RouteStop in the route we can board in this round
            // figure out which departure we can take
            PathElement boardingPE = this.arrivalPathPerRouteStop[firstRouteStopIndex];
            double agentFirstArrivalTime = boardingPE.arrivalTime;
            int currentDepartureIndex = findNextDepartureIndex(route, firstRouteStop, agentFirstArrivalTime);
            if (currentDepartureIndex >=0) {
                double currentDepartureTime = this.data.departures[currentDepartureIndex];
                double vehicleArrivalTime = currentDepartureTime + firstRouteStop.arrivalOffset;
                double currentAgentBoardingTime = (agentFirstArrivalTime < vehicleArrivalTime) ? vehicleArrivalTime : agentFirstArrivalTime;
                double waitingTime = currentAgentBoardingTime - agentFirstArrivalTime;
                double waitingCost = -this.config.getMarginalUtilityOfWaitingPt_utl_s() * waitingTime;
                double currentCostWhenBoarding = boardingPE.arrivalCost + waitingCost;

                for (int toRouteStopIndex = firstRouteStopIndex + 1; toRouteStopIndex < route.indexFirstRouteStop + route.countRouteStops; toRouteStopIndex++) {
                    RRouteStop toRouteStop = this.data.routeStops[toRouteStopIndex];
                    PathElement toPE = this.arrivalPathPerRouteStop[toRouteStopIndex];
                    double arrivalTime = currentDepartureTime + toRouteStop.arrivalOffset;
                    double inVehicleTime = arrivalTime - currentAgentBoardingTime;
                    double inVehicleCost = inVehicleTime * -this.config.getMarginalUtilityOfTravelTimePt_utl_s();
                    double arrivalCost = currentCostWhenBoarding + inVehicleCost;

                    if (arrivalCost <= this.bestArrivalCost && (toPE == null || arrivalCost < toPE.arrivalCost)) {
                        // we can actually improve the arrival by cost
                        this.arrivalPathPerRouteStop[toRouteStopIndex] = new PathElement(boardingPE, toRouteStop, arrivalTime, arrivalCost, boardingPE.transferCount, false);
                        this.reachedRouteStopIndices.set(toRouteStopIndex);

                        checkForBestArrival(toRouteStopIndex, arrivalCost);
                    } else if (toPE != null && arrivalCost > toPE.arrivalCost) {
                        // we reached this stop at lower cost in an earlier round
                        // continue with this better path
                        int lowerCostDepartureIndex = findNextDepartureIndex(route, toRouteStop, toPE.arrivalTime);
                        if (lowerCostDepartureIndex >= 0) {
                            // update the "first" (boarding) route stop
                            firstRouteStop = toRouteStop;
                            boardingPE = toPE;
                            currentDepartureIndex = lowerCostDepartureIndex;
                            currentDepartureTime = this.data.departures[currentDepartureIndex];
                            agentFirstArrivalTime = boardingPE.arrivalTime;
                            vehicleArrivalTime = currentDepartureTime + firstRouteStop.arrivalOffset;
                            currentAgentBoardingTime = (agentFirstArrivalTime < vehicleArrivalTime) ? vehicleArrivalTime : agentFirstArrivalTime;
                            waitingTime = currentAgentBoardingTime - agentFirstArrivalTime;
                            waitingCost = -this.config.getMarginalUtilityOfWaitingPt_utl_s() * waitingTime;
                            currentCostWhenBoarding = boardingPE.arrivalCost + waitingCost;
                        }
                    }
                    firstRouteStopIndex = toRouteStopIndex; // we've handled this route stop, so we can skip it in the outer loop
                }
            }
        }
    }

    private void checkForBestArrival(int routeStopIndex, double arrivalCost) {
        if (this.destinationRouteStopIndices.get(routeStopIndex)) {
            // this is a destination stop
            double totalCost = arrivalCost + this.egressCostsPerRouteStop[routeStopIndex];
            if (totalCost < this.bestArrivalCost) {
                this.bestArrivalCost = totalCost;
            }
        }
    }

    private int findNextDepartureIndex(RRoute route, RRouteStop routeStop, double time) {
        double depTimeAtRouteStart = time - routeStop.departureOffset;
        int fromIndex = route.indexFirstDeparture;
        int toIndex = fromIndex + route.countDepartures;
        int pos = Arrays.binarySearch(this.data.departures, fromIndex, toIndex, depTimeAtRouteStart);
        if (pos < 0) {
            // binarySearch returns (-(insertion point) - 1) if the element was not found, which will happen most of the times.
            // insertion_point points to the next larger element, which is the next departure in our case
            // This can be transformed as follows:
            // retval = -(insertion point) - 1
            // ==> insertion point = -(retval+1) .
            pos = -(pos + 1);
        }
        if (pos >= toIndex) {
            // there is no later departure time
            return -1;
        }
        return pos;
    }

    private void handleTransfers() {
        for (int fromRouteStopIndex = this.reachedRouteStopIndices.nextSetBit(0); fromRouteStopIndex >= 0; fromRouteStopIndex = this.reachedRouteStopIndices.nextSetBit(fromRouteStopIndex+1)) {
            PathElement fromPE = this.arrivalPathPerRouteStop[fromRouteStopIndex];
            double cost = fromPE.arrivalCost;
            if (cost < this.bestArrivalCost) {
                double time = fromPE.arrivalTime;
                RRouteStop fromRouteStop = this.data.routeStops[fromRouteStopIndex];
                int firstTransferIndex = fromRouteStop.indexFirstTransfer;
                int lastTransferIndex = firstTransferIndex + fromRouteStop.countTransfers;
                for (int transferIndex = firstTransferIndex; transferIndex < lastTransferIndex; transferIndex++) {
                    RTransfer transfer = this.data.transfers[transferIndex];
                    double newArrivalCost = cost + transfer.transferCost;
                    if (newArrivalCost < this.bestArrivalCost) {
                        int toRouteStopIndex = transfer.toRouteStop;
                        PathElement pe = this.arrivalPathPerRouteStop[toRouteStopIndex];
                        if (pe == null || newArrivalCost < pe.arrivalCost) {
                            // it's  the first time we arrive at this stop,or we arrive with better costs
                            RRouteStop toRouteStop = this.data.routeStops[toRouteStopIndex];
                            double newArrivalTime = time + transfer.transferTime;
                            this.improvedRouteStopIndices.set(toRouteStopIndex);
                            this.arrivalPathPerRouteStop[toRouteStopIndex] = new PathElement(fromPE, toRouteStop, newArrivalTime, newArrivalCost, fromPE.transferCount + 1, true);
                        }
                    }
                }
            }
        }
    }

    private PathElement findLeastCostArrival(Map<TransitStopFacility, InitialStop> destinationStops) {
        double leastCost = Double.POSITIVE_INFINITY;
        PathElement leastCostPath = null;

        for (int routeStopIndex = 0; routeStopIndex < this.arrivalPathPerRouteStop.length; routeStopIndex++) {
            PathElement pe = this.arrivalPathPerRouteStop[routeStopIndex];
            if (pe != null) {
                RRouteStop routeStop = this.data.routeStops[routeStopIndex];
                TransitStopFacility stopFacility = routeStop.routeStop.getStopFacility();
                InitialStop egressStop = destinationStops.get(stopFacility);
                if (egressStop != null) {
                    double arrivalTime = pe.arrivalTime + egressStop.accessTime;
                    double totalCost = pe.arrivalCost + egressStop.accessCost;
                    if ((totalCost < leastCost) || (totalCost == leastCost && pe.transferCount < leastCostPath.transferCount)) {
                        leastCost = totalCost;
                        leastCostPath = new PathElement(pe, null, arrivalTime, totalCost, pe.transferCount, true); // this is the egress leg
                    }
                }
            }
        }
        return leastCostPath;
    }

    public RaptorRoute createRaptorRoute(PathElement destinationPathElement, double departureTime) {
        LinkedList<PathElement> pes = new LinkedList<>();
        double arrivalCost = Double.POSITIVE_INFINITY;
        if (destinationPathElement != null) {
            arrivalCost = destinationPathElement.arrivalCost;
            PathElement pe = destinationPathElement;
            while (pe.comingFrom != null) {
                pes.addFirst(pe);
                pe = pe.comingFrom;
            }
            pes.addFirst(pe);
        }

        RaptorRoute raptorRoute = new RaptorRoute(null, null, arrivalCost);
        double time = departureTime;
        TransitStopFacility fromStop = null;
        for (PathElement pe : pes) {
            TransitStopFacility toStop = pe.toRouteStop == null ? null : pe.toRouteStop.routeStop.getStopFacility();
            double travelTime = pe.arrivalTime - time;
            if (pe.isTransfer) {
                boolean differentFromTo = (fromStop == null || toStop == null) || (fromStop != toStop);
                if (differentFromTo) {
                    // do not create a transfer-leg if we stay at the same stop facility
                    String mode = TransportMode.transit_walk;
                    if (fromStop == null && toStop != null) {
                        mode = TransportMode.access_walk;
                    }
                    if (fromStop != null && toStop == null) {
                        mode = TransportMode.egress_walk;
                    }
                    raptorRoute.addNonPt(fromStop, toStop, time, travelTime, mode);
                }
            } else {
                TransitLine line = pe.toRouteStop.line;
                TransitRoute route = pe.toRouteStop.route;
                raptorRoute.addPt(fromStop, toStop, line, route, time, travelTime);
            }
            time = pe.arrivalTime;
            fromStop = toStop;
        }
        return raptorRoute;
    }

    public RaptorRoute calcLeastCostRoute(double earliestDepTime, double latestDepTime, List<InitialStop> accessStops, List<InitialStop> egressStops) {
        // TODO
        return null;
    }

    public List<RaptorRoute> calcParetoSet(double depTime, List<InitialStop> accessStops, List<InitialStop> egressStops) {
        // TODO
        return null;
    }

    public List<RaptorRoute> calcParetoSet(double earliestDepTime, double latestDepTime, List<InitialStop> accessStops, List<InitialStop> egressStops) {
        // TODO
        return null;
    }

    private static class PathElement {
        PathElement comingFrom;
        RRouteStop toRouteStop;
        double arrivalTime;
        double arrivalCost;
        int transferCount;
        boolean isTransfer;

        PathElement(PathElement comingFrom, RRouteStop toRouteStop, double arrivalTime, double arrivalCost, int transferCount, boolean isTransfer) {
            this.comingFrom = comingFrom;
            this.toRouteStop = toRouteStop;
            this.arrivalTime = arrivalTime;
            this.arrivalCost = arrivalCost;
            this.transferCount = transferCount;
            this.isTransfer = isTransfer;
        }
    }
}
