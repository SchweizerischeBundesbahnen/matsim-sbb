package ch.sbb.matsim.umlego;

import ch.sbb.matsim.projects.synpop.OMXODParser;
import ch.sbb.matsim.routing.pt.raptor.RaptorParameters;
import ch.sbb.matsim.routing.pt.raptor.RaptorRoute;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author mrieser / Simunto
 */
class UmlegoWorker implements Runnable {

	private final BlockingQueue<WorkItem> workerQueue;
	private final Umlego.UmlegoParameters params;
	private final OMXODParser demand;
	private final SwissRailRaptor raptor;
	private final RaptorParameters raptorParams;
	private final Set<TransitStopFacility> relevantStops;
	private final List<String> zoneIds;
	private final Map<String, List<Umlego.ConnectedStop>> stopsPerZone;

	public UmlegoWorker(BlockingQueue<WorkItem> workerQueue,
											Umlego.UmlegoParameters params,
											OMXODParser demand,
											SwissRailRaptor raptor,
											RaptorParameters raptorParams,
											Set<TransitStopFacility> relevantStops,
											List<String> zoneIds,
											Map<String, List<Umlego.ConnectedStop>> stopsPerZone) {
		this.workerQueue = workerQueue;
		this.params = params;
		this.demand = demand;
		this.raptor = raptor;
		this.raptorParams = raptorParams;
		this.relevantStops = relevantStops;
		this.zoneIds = zoneIds;
		this.stopsPerZone = stopsPerZone;
	}

	public void run() {
		while (true) {
			WorkItem item = null;
			try {
				item = this.workerQueue.take();
				if (item.originZone == null) {
					return;
				}
				WorkResult result = processOriginZone(item);
				item.result.complete(result);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private WorkResult processOriginZone(WorkItem workItem) {
		Map<String, List<Umlego.FoundRoute>> foundRoutes = calculateRoutesForZone(workItem.originZone);
		filterRoutes(foundRoutes);
		sortRoutes(foundRoutes);
		calculatePerceivedJourneyTime(foundRoutes);
		return assignDemand(workItem.originZone, foundRoutes);
	}

	private Map<String, List<Umlego.FoundRoute>> calculateRoutesForZone(String originZone) {
		Map<TransitStopFacility, Map<TransitStopFacility, Map<Umlego.FoundRoute, Boolean>>> foundRoutes = new HashMap<>();
		for (Umlego.ConnectedStop stop : stopsPerZone.getOrDefault(originZone, Collections.emptyList())) {
			calcRoutesFromStop(stop.stopFacility(), foundRoutes);
		}
		return aggregateOnZoneLevel(originZone, foundRoutes);
	}

	private void calcRoutesFromStop(TransitStopFacility originStop, Map<TransitStopFacility, Map<TransitStopFacility, Map<Umlego.FoundRoute, Boolean>>> foundRoutes) {
		this.raptor.calcTreesObservable(
				originStop,
				0,
				Double.POSITIVE_INFINITY,
				this.raptorParams,
				null,
				(departureTime, arrivalStop, arrivalTime, transferCount, route) -> {
					if (this.relevantStops.contains(arrivalStop)) {
						Umlego.FoundRoute foundRoute = new Umlego.FoundRoute(route.get());
						foundRoutes
								.computeIfAbsent(foundRoute.originStop, stop -> new ConcurrentHashMap<>())
								.computeIfAbsent(foundRoute.destinationStop, stop -> new ConcurrentHashMap<>())
								.put(foundRoute, Boolean.TRUE);
					}
				});
	}

	private Map<String, List<Umlego.FoundRoute>> aggregateOnZoneLevel(String originZoneId, Map<TransitStopFacility, Map<TransitStopFacility, Map<Umlego.FoundRoute, Boolean>>> foundRoutesPerStop) {
		List<Umlego.ConnectedStop> emptyList = Collections.emptyList();
		Map<String, List<Umlego.FoundRoute>> foundRoutesPerZone = new HashMap<>();

		List<Umlego.ConnectedStop> stopsPerOriginZone = this.stopsPerZone.getOrDefault(originZoneId, emptyList);
		Map<TransitStopFacility, Umlego.ConnectedStop> originStopLookup = new HashMap<>();
		for (Umlego.ConnectedStop stop : stopsPerOriginZone) {
			originStopLookup.put(stop.stopFacility(), stop);
		}

		for (String destinationZoneId : zoneIds) {
			List<Umlego.ConnectedStop> stopsPerDestinationZone = this.stopsPerZone.getOrDefault(destinationZoneId, emptyList);
			Map<TransitStopFacility, Umlego.ConnectedStop> destinationStopLookup = new HashMap<>();
			for (Umlego.ConnectedStop stop : stopsPerDestinationZone) {
				destinationStopLookup.put(stop.stopFacility(), stop);
			}

			Set<Umlego.FoundRoute> allRoutesFromTo = new HashSet<>();
			for (Umlego.ConnectedStop originStop : stopsPerOriginZone) {
				Map<TransitStopFacility, Map<Umlego.FoundRoute, Boolean>> routesPerDestinationStop = foundRoutesPerStop.get(originStop.stopFacility());
				if (routesPerDestinationStop != null) {
					for (Umlego.ConnectedStop destinationStop : this.stopsPerZone.getOrDefault(destinationZoneId, emptyList)) {
						Map<Umlego.FoundRoute, Boolean> routesPerOriginDestinationStop = routesPerDestinationStop.get(destinationStop.stopFacility());
						if (routesPerOriginDestinationStop != null) {
							for (Umlego.FoundRoute route : routesPerOriginDestinationStop.keySet()) {
								Umlego.ConnectedStop originConnectedStop = originStopLookup.get(route.originStop);
								Umlego.ConnectedStop destinationConnectedStop = destinationStopLookup.get(route.destinationStop);

								if (originConnectedStop != null && destinationConnectedStop != null) {
									// otherwise the route would not be valid, e.g. due to an additional transfer at the start or end
									route.originConnectedStop = originConnectedStop;
									route.destinationConnectedStop = destinationConnectedStop;
									allRoutesFromTo.add(route);
								}
							}
						}
					}
				}
			}
			foundRoutesPerZone.put(destinationZoneId, new ArrayList<>(allRoutesFromTo));
		}
		return foundRoutesPerZone;
	}

	private void filterRoutes(Map<String, List<Umlego.FoundRoute>> foundRoutes) {
		for (List<Umlego.FoundRoute> routes : foundRoutes.values()) {
			routes.sort(UmlegoWorker::compareFoundRoutesByArrivalTimeAndTravelTime);
			// routs are now sorted by arrival time (ascending) and travel time (ascending) and transfers (ascending)
			double lastArrivalTime = -1;
			Umlego.FoundRoute thisBestRoute = null; // best route with
			Umlego.FoundRoute lastBestRoute = null;
			for (ListIterator<Umlego.FoundRoute> iterator = routes.listIterator(); iterator.hasNext(); ) {
				Umlego.FoundRoute route = iterator.next();
				if (route.arrTime != lastArrivalTime || thisBestRoute == null) {
					lastArrivalTime = route.arrTime;
					lastBestRoute = route;
					thisBestRoute = route;
				} else {
					if (route.transfers > thisBestRoute.transfers) {
						if (route.travelTime > lastBestRoute.travelTime) {
							// route has a longer travelTime and more transfers than lastBestRoute, get rid of it
							iterator.remove();
						} else {
							lastBestRoute = thisBestRoute;
							thisBestRoute = route;
						}
					} else {
						if (route.travelTime > lastBestRoute.travelTime) {
							// route has a longer travelTime and more transfers than lastBestRoute, get rid of it
							iterator.remove();
						}
					}
				}
			}
		}
	}

	private void sortRoutes(Map<String, List<Umlego.FoundRoute>> foundRoutes) {
		for (List<Umlego.FoundRoute> routes : foundRoutes.values()) {
			routes.sort(UmlegoWorker::compareFoundRoutesByDepartureTime);
		}
	}

	private void calculatePerceivedJourneyTime(Map<String, List<Umlego.FoundRoute>> foundRoutes) {
		for (List<Umlego.FoundRoute> routes : foundRoutes.values()) {
			for (Umlego.FoundRoute route : routes) {
				calculatePerceivedJourneyTime(route);
			}
		}
	}

	private void calculatePerceivedJourneyTime(Umlego.FoundRoute route) {
		double inVehicleTime = 0;
		double accessTime = route.originConnectedStop.walkTime();
		double egressTime = route.destinationConnectedStop.walkTime();
		double walkTime = 0;
		double transferWaitTime = 0;
		double transferCount = route.transfers;

		boolean hadTransferBefore = false;
		for (RaptorRoute.RoutePart part : route.routeParts) {
			if (part.line == null) {
				// it is a transfer
				walkTime += (part.arrivalTime - part.depTime);
				hadTransferBefore = true;
			} else {
				if (hadTransferBefore) {
					transferWaitTime += (part.vehicleDepTime - part.depTime);
				}
				inVehicleTime += (part.arrivalTime - part.vehicleDepTime);
				hadTransferBefore = false;
			}
		}

		double expectedTotalTime = route.routeParts.get(route.routeParts.size() - 1).arrivalTime - route.routeParts.get(0).vehicleDepTime;
		if ((walkTime + transferWaitTime + inVehicleTime) != expectedTotalTime) {
			System.err.println("INCONSISTENT TIMES " + route.getRouteAsString());
		}

		Umlego.PerceivedJourneyTimeParameters pjtParams = this.params.pjt();
		route.perceivedJourneyTime_min = pjtParams.betaInVehicleTime() * (inVehicleTime / 60.0)
				+ pjtParams.betaAccessTime() * (accessTime / 60.0)
				+ pjtParams.betaEgressTime() * (egressTime / 60.0)
				+ pjtParams.betaWalkTime() * (walkTime / 60.0)
				+ pjtParams.betaTransferWaitTime() * (transferWaitTime / 60.0)
				+ pjtParams.betaTransferCount() * transferCount;
	}

	private WorkResult assignDemand(String originZone, Map<String, List<Umlego.FoundRoute>> foundRoutes) {
		Umlego.UnroutableDemand unroutableDemand = new Umlego.UnroutableDemand();
		for (String destinationZone : this.zoneIds) {
			for (String matrixName : this.demand.getMatrixNames()) {
				double value = this.demand.getMatrixValue(originZone, destinationZone, matrixName);
				if (value > 0) {
					int matrixNumber = Integer.parseInt(matrixName);
					double startTime = (matrixNumber - 1) * 10 * 60; // 10-minute time slots, in seconds
					double endTime = (matrixNumber) * 10 * 60;
					assignDemand(destinationZone, startTime, endTime, value, foundRoutes, unroutableDemand);
				}
			}
		}
		return new WorkResult(originZone, foundRoutes, unroutableDemand);
	}

	private void assignDemand(String destinationZone, double startTime, double endTime, double odDemand, Map<String, List<Umlego.FoundRoute>> foundRoutes, Umlego.UnroutableDemand unroutableDemand) {
		var routes = foundRoutes.get(destinationZone);
		if (routes == null) {
			unroutableDemand.demand += odDemand;
			return;
		}

		double earliestDeparture = startTime - this.params.routeSelection().beforeTimewindow();
		double latestDeparture = endTime + this.params.routeSelection().afterTimewindow();
		Umlego.FoundRoute[] potentialRoutes = routes.stream().filter(route -> ((route.depTime - route.originConnectedStop.walkTime()) >= earliestDeparture)
				&& ((route.depTime - route.originConnectedStop.walkTime() <= latestDeparture))).toList().toArray(new Umlego.FoundRoute[0]);
		if (potentialRoutes.length == 0) {
			unroutableDemand.demand += odDemand;
			return;
		}

		double timeWindow = endTime - startTime;
		double stepSize = 60.0; // sample every minute
		int samples = (int) (timeWindow / stepSize);
		double sharePerSample = 1.0 / ((double) samples);
		double[] routeUtilities = new double[potentialRoutes.length];
		double beta = this.params.boxCox().beta();
		double tau = this.params.boxCox().tau();
		double betaPJT = this.params.impediance().betaPerceivedJourneyTime();
		double betaDeltaTEarly = this.params.impediance().betaDeltaTEarly();
		double betaDeltaTLate = this.params.impediance().betaDeltaTLate();
		for (int sample = 0; sample < samples; sample++) {
			double time = startTime + sample * stepSize;
			double utilitiesSum = 0;
			for (int i = 0; i < potentialRoutes.length; i++) {
				Umlego.FoundRoute route = potentialRoutes[i];
				double routeDepTime = route.depTime - route.originConnectedStop.walkTime();
				double deltaTEarly = (routeDepTime < time) ? (time - routeDepTime) : 0.0;
				double deltaTLate = (routeDepTime > time) ? (routeDepTime - time) : 0.0;
				double impediance = betaPJT * route.perceivedJourneyTime_min + betaDeltaTEarly * (deltaTEarly / 60.0) + betaDeltaTLate * (deltaTLate / 60.0);
				double utility = Math.exp(-beta * (Math.pow(impediance, tau) - 1.0) / tau);
				routeUtilities[i] = utility;
				utilitiesSum += utility;
			}
			for (int i = 0; i < potentialRoutes.length; i++) {
				double routeShare = routeUtilities[i] / utilitiesSum;
				double routeDemand = odDemand * sharePerSample * routeShare;
				Umlego.FoundRoute route = potentialRoutes[i];
				route.demand += routeDemand;
			}
		}
	}

	private static int compareFoundRoutesByDepartureTime(Umlego.FoundRoute o1, Umlego.FoundRoute o2) {
		if (o1.depTime < o2.depTime) {
			return -1;
		}
		if (o1.depTime > o2.depTime) {
			return +1;
		}
		if (o1.travelTime < o2.travelTime) {
			return -1;
		}
		if (o1.travelTime > o2.travelTime) {
			return +1;
		}
		return Integer.compare(o1.transfers, o2.transfers);
	}

	private static int compareFoundRoutesByArrivalTimeAndTravelTime(Umlego.FoundRoute o1, Umlego.FoundRoute o2) {
		if (o1.arrTime < o2.arrTime) {
			return -1;
		}
		if (o1.arrTime > o2.arrTime) {
			return +1;
		}
		if (o1.travelTime < o2.travelTime) {
			return -1;
		}
		if (o1.travelTime > o2.travelTime) {
			return +1;
		}
		return Integer.compare(o1.transfers, o2.transfers);
	}

	public record WorkItem(
			String originZone,
			CompletableFuture<WorkResult> result
	) {
	}

	public record WorkResult(
			String originZone,
			Map<String, List<Umlego.FoundRoute>> routesPerDestinationZone,
			Umlego.UnroutableDemand unroutableDemand
	) {
	}
}
