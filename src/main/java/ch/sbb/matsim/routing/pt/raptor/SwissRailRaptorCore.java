/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData.RRoute;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData.RRouteStop;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData.RTransfer;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.Departure;
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
    private int[] earliestRouteStopIndexPerRoute;

    public SwissRailRaptorCore(SwissRailRaptorData data) {
        this.data = data;
        this.config = data.config;
        this.arrivalPathPerRouteStop = new PathElement[data.countRouteStops];
        this.egressCostsPerRouteStop = new double[data.countRouteStops];
        this.improvedRouteStopIndices = new BitSet(this.data.countRouteStops);
        this.reachedRouteStopIndices = new BitSet(this.data.countRouteStops);
        this.destinationRouteStopIndices = new BitSet(this.data.countRouteStops);
        this.earliestRouteStopIndexPerRoute = new int[this.data.routes.length];
    }

    private void reset() {
        Arrays.fill(this.arrivalPathPerRouteStop, null);
        Arrays.fill(this.egressCostsPerRouteStop, Double.POSITIVE_INFINITY);
        Arrays.fill(this.earliestRouteStopIndexPerRoute, -1);
        this.improvedRouteStopIndices.clear();
        this.reachedRouteStopIndices.clear();
        this.destinationRouteStopIndices.clear();
        this.bestArrivalCost = Double.POSITIVE_INFINITY;
    }

    public RaptorRoute calcLeastCostRoute(double depTime, List<InitialStop> accessStops, List<InitialStop> egressStops) {
        int maxTransfers = 99; // TODO make configurable

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
                this.arrivalPathPerRouteStop[routeStopIndex] = new PathElement(-1, null, toRouteStop, depTime + stop.accessTime, stop.accessCost, 0, true);
                updateEarliestRouteStop(routeStopIndex, this.data.routeStops[routeStopIndex].transitRouteIndex);
            }
        }

        // the main loop
        for (int k = 0; k <= maxTransfers; k++) {
            // first stage (according to paper) is to set earliestArrivalTime_k(stop) = earliestArrivalTime_k-1(stop)
            // but because we re-use the earliestArrivalTime-array, we don't have to do anything.

            // second stage: process routes

            // optimization: find potentially improved routes
            BitSet touchedRoutes = new BitSet(this.data.routes.length);
            for (int routeStopIndex = this.improvedRouteStopIndices.nextSetBit(0); routeStopIndex >= 0; routeStopIndex = improvedRouteStopIndices.nextSetBit(routeStopIndex+1)) {
                RRouteStop routeStop = this.data.routeStops[routeStopIndex];
                touchedRoutes.set(routeStop.transitRouteIndex);
            }
            this.improvedRouteStopIndices.clear();

            this.reachedRouteStopIndices.clear();
            // for each relevant route, step along route and look for new/improved connections
            for (int routeIndex = touchedRoutes.nextSetBit(0); routeIndex >= 0; routeIndex = touchedRoutes.nextSetBit(routeIndex+1)) {
                RRoute route = this.data.routes[routeIndex];

                Departure currentEarliestDeparture = null;
                int currentEarliestDepartureIndex = -1;
                int currentBoardingStopIndex = -1;
                RRouteStop currentBoardingRouteStop = null;
                double currentBoardingRouteStopAgentEnterTime = Time.UNDEFINED_TIME;
                double currentBoardingRouteStopAgentWaitingTime = Time.UNDEFINED_TIME;
                double currentBoardingRouteStopCost = Double.NaN;
                int currentTransferCount = -1;
                int firstRouteStopIndex = this.earliestRouteStopIndexPerRoute[routeIndex];
                if (firstRouteStopIndex < 0) {
                    firstRouteStopIndex = route.indexFirstRouteStop;
                }
                this.earliestRouteStopIndexPerRoute[routeIndex] = -1; // reset for next round
                for (int routeStopIndex = firstRouteStopIndex; routeStopIndex < route.indexFirstRouteStop + route.countRouteStops; routeStopIndex++) {
                    PathElement pe = this.arrivalPathPerRouteStop[routeStopIndex];
                    if (pe != null) {
                        // this means we reached this route stop in an earlier round
                        RRouteStop routeStop = this.data.routeStops[routeStopIndex];
                        double time = pe.arrivalTime;
                        int nextDepartureIndex = findNextDepartureIndex(route, routeStop, time);
                        if (nextDepartureIndex >= 0) {
                            if (currentEarliestDepartureIndex < 0 || nextDepartureIndex < currentEarliestDepartureIndex) {
                                // it's either the first time we can board, or we can board an earlier service
                                currentEarliestDepartureIndex = nextDepartureIndex;
                                currentEarliestDeparture = this.data.departures[nextDepartureIndex];
                                currentBoardingStopIndex = routeStopIndex;
                                currentBoardingRouteStop = routeStop;
                                currentBoardingRouteStopCost = pe.arrivalCost;
                                currentTransferCount = pe.transferCount;
                                double vehicleArrivalTime = currentEarliestDeparture.getDepartureTime() + currentBoardingRouteStop.arrivalOffset;
                                currentBoardingRouteStopAgentEnterTime = (time < vehicleArrivalTime) ? vehicleArrivalTime : time;
                                currentBoardingRouteStopAgentWaitingTime = currentBoardingRouteStopAgentEnterTime - time;
                            } else if (currentEarliestDepartureIndex < nextDepartureIndex) {
                                // we can arrive here earlier by arriving with the current service
                                double arrivalTime = currentEarliestDeparture.getDepartureTime() + routeStop.arrivalOffset;
                                double inVehicleTime = arrivalTime - currentBoardingRouteStopAgentEnterTime;
                                double inVehicleCost = inVehicleTime * -this.config.getMarginalUtilityOfTravelTimePt_utl_s();
                                double waitingCost = -this.config.getMarginalUtilityOfWaitingPt_utl_s() * currentBoardingRouteStopAgentWaitingTime;
                                double arrivalCost = currentBoardingRouteStopCost + waitingCost + inVehicleCost;
                                if (arrivalCost < pe.arrivalCost && arrivalCost <= this.bestArrivalCost) {
                                    // we can actually improve the arrival by cost
                                    this.arrivalPathPerRouteStop[routeStopIndex] = new PathElement(currentBoardingStopIndex, currentBoardingRouteStop, routeStop, arrivalTime, arrivalCost, currentTransferCount, false);
                                    this.reachedRouteStopIndices.set(routeStopIndex);

                                    checkForBestArrival(routeStopIndex, arrivalCost);
                                }
                            }
                        } else if (currentEarliestDepartureIndex >= 0) {
                            // we arrived here earlier, but obviously too late for any service, but now we arrive here with a service!
                            double arrivalTime = currentEarliestDeparture.getDepartureTime() + routeStop.arrivalOffset;
                            double inVehicleTime = arrivalTime - currentBoardingRouteStopAgentEnterTime;
                            double inVehicleCost = inVehicleTime * -this.config.getMarginalUtilityOfTravelTimePt_utl_s();
                            double waitingCost = -this.config.getMarginalUtilityOfWaitingPt_utl_s() * currentBoardingRouteStopAgentWaitingTime;
                            double arrivalCost = currentBoardingRouteStopCost + waitingCost + inVehicleCost;
                            if (arrivalCost < pe.arrivalCost && arrivalCost <= this.bestArrivalCost) {
                                // we can actually improve the arrival by cost
                                this.arrivalPathPerRouteStop[routeStopIndex] = new PathElement(currentBoardingStopIndex, currentBoardingRouteStop, routeStop, arrivalTime, arrivalCost, currentTransferCount, false);
                                this.reachedRouteStopIndices.set(routeStopIndex);

                                checkForBestArrival(routeStopIndex, arrivalCost);
                            }
                        }
                    } else if (currentBoardingStopIndex >= 0) { // it's the first time we reach this route stop
                        RRouteStop routeStop = this.data.routeStops[routeStopIndex];
                        double arrivalTime = currentEarliestDeparture.getDepartureTime() + routeStop.arrivalOffset;
                        double inVehicleTime = arrivalTime - currentBoardingRouteStopAgentEnterTime;
                        double inVehicleCost = inVehicleTime * -this.config.getMarginalUtilityOfTravelTimePt_utl_s();
                        double waitingCost = -this.config.getMarginalUtilityOfWaitingPt_utl_s() * currentBoardingRouteStopAgentWaitingTime;
                        double arrivalCost = currentBoardingRouteStopCost + waitingCost + inVehicleCost;
                        if (arrivalCost <= this.bestArrivalCost) {
                            this.arrivalPathPerRouteStop[routeStopIndex] = new PathElement(currentBoardingStopIndex, currentBoardingRouteStop, routeStop, arrivalTime, arrivalCost, currentTransferCount, false);
                            this.reachedRouteStopIndices.set(routeStopIndex);

                            checkForBestArrival(routeStopIndex, arrivalCost);
                        }

                    }
                }
            }

            // third stage (according to paper): handle footpaths / transfers
            for (int fromRouteStopIndex = this.reachedRouteStopIndices.nextSetBit(0); fromRouteStopIndex >= 0; fromRouteStopIndex = this.reachedRouteStopIndices.nextSetBit(fromRouteStopIndex+1)) {
                PathElement fromPE = this.arrivalPathPerRouteStop[fromRouteStopIndex];
                double cost = fromPE.arrivalCost;
                if (cost <= this.bestArrivalCost) {
                    double time = fromPE.arrivalTime;
                    RRouteStop fromRouteStop = this.data.routeStops[fromRouteStopIndex];
                    int firstTransferIndex = fromRouteStop.indexFirstTransfer;
                    int lastTransferIndex = firstTransferIndex + fromRouteStop.countTransfers;
                    for (int transferIndex = firstTransferIndex; transferIndex < lastTransferIndex; transferIndex++) {
                        RTransfer transfer = this.data.transfers[transferIndex];
                        int toRouteStopIndex = transfer.toRouteStop;
                        double newArrivalCost = cost + transfer.transferCost;
                        PathElement pe = this.arrivalPathPerRouteStop[toRouteStopIndex];
                        if (pe == null || newArrivalCost < pe.arrivalCost) {
                            // it's  the first time we arrive at this stop,or we arrive with better costs
                            RRouteStop toRouteStop = this.data.routeStops[toRouteStopIndex];
                            double newArrivalTime = time + transfer.transferTime;
                            this.improvedRouteStopIndices.set(toRouteStopIndex);
                            this.arrivalPathPerRouteStop[toRouteStopIndex] = new PathElement(fromRouteStopIndex, fromRouteStop, toRouteStop, newArrivalTime, newArrivalCost, fromPE.transferCount + 1, true);
                            updateEarliestRouteStop(toRouteStopIndex, toRouteStop.transitRouteIndex);
                        }
                    }
                }
            }

            // final stage: check stop criterion
            if (this.improvedRouteStopIndices.isEmpty()) {
                break;
            }
        }

        // create RaptorRoute based on PathElements
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
                        leastCostPath = new PathElement(routeStopIndex, routeStop, null, arrivalTime, totalCost, pe.transferCount, true); // this is the egress leg
                    }
                }
            }
        }
        LinkedList<PathElement> pes = new LinkedList<>();
        if (leastCostPath != null) {
            PathElement pe = leastCostPath;
            while (pe.comingFrom >= 0) {
                pes.addFirst(pe);
                PathElement fromPE = this.arrivalPathPerRouteStop[pe.comingFrom];
                pe = fromPE;
            }
            pes.addFirst(pe);
        }

        RaptorRoute raptorRoute = new RaptorRoute(null, null, leastCost);
        double time = depTime;
        for (PathElement pe : pes) {
            TransitStopFacility fromStop = pe.fromRouteStop == null ? null : pe.fromRouteStop.routeStop.getStopFacility();
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
                TransitLine line = pe.fromRouteStop.line;
                TransitRoute route = pe.fromRouteStop.route;
                raptorRoute.addPt(fromStop, toStop, line, route, time, travelTime);
            }
            time = pe.arrivalTime;
        }
        return raptorRoute;
    }

    private void updateEarliestRouteStop(int routeStopIndex, int routeIndex) {
        int currentEarliest = this.earliestRouteStopIndexPerRoute[routeIndex];
        if (currentEarliest < 0 || routeStopIndex < currentEarliest) {
            this.earliestRouteStopIndexPerRoute[routeIndex] = routeStopIndex;
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
        Departure dep = this.data.schedule.getFactory().createDeparture(null, depTimeAtRouteStart);
        int fromIndex = route.indexFirstDeparture;
        int toIndex = fromIndex + route.countDepartures;
        int pos = Arrays.binarySearch(this.data.departures, fromIndex, toIndex, dep, (o1, o2) -> Double.compare(o1.getDepartureTime(), o2.getDepartureTime()));
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
        int comingFrom;
        RRouteStop fromRouteStop;
        RRouteStop toRouteStop;
        double arrivalTime;
        double arrivalCost;
        int transferCount;
        boolean isTransfer;

        public PathElement(int comingFrom, RRouteStop fromRouteStop, RRouteStop toRouteStop, double arrivalTime, double arrivalCost, int transferCount, boolean isTransfer) {
            this.comingFrom = comingFrom;
            this.fromRouteStop = fromRouteStop;
            this.toRouteStop = toRouteStop;
            this.arrivalTime = arrivalTime;
            this.arrivalCost = arrivalCost;
            this.transferCount = transferCount;
            this.isTransfer = isTransfer;
        }
    }
}
