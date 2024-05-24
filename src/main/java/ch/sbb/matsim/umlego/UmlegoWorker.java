package ch.sbb.matsim.umlego;

import ch.sbb.matsim.projects.synpop.OMXODParser;
import ch.sbb.matsim.routing.pt.raptor.RaptorParameters;
import ch.sbb.matsim.routing.pt.raptor.RaptorRoute;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
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
import java.util.stream.Collectors;

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
		calculateRouteCharacteristics(foundRoutes);
		filterRoutes(foundRoutes);
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
		this.raptorParams.setMaxTransfers(this.params.maxTransfers());
		this.raptor.calcTreesObservable(
				originStop,
				0,
				Double.POSITIVE_INFINITY,
				this.raptorParams,
				null,
				(departureTime, arrivalStop, arrivalTime, transferCount, route) -> {
					if (this.relevantStops.contains(arrivalStop)) {
						Umlego.FoundRoute foundRoute = new Umlego.FoundRoute(route.get());
						if (!UmlegoUtils.isUselessRoute(foundRoute)) {
							foundRoutes
									.computeIfAbsent(foundRoute.originStop, stop -> new ConcurrentHashMap<>())
									.computeIfAbsent(foundRoute.destinationStop, stop -> new ConcurrentHashMap<>())
									.put(foundRoute, Boolean.TRUE);
						}
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
									route.travelTimeWithAccess = route.travelTimeWithoutAccess + originConnectedStop.walkTime() + destinationConnectedStop.walkTime();
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
		for (Map.Entry<String, List<Umlego.FoundRoute>> e : foundRoutes.entrySet()) {
			String destinationZoneId = e.getKey();
			List<Umlego.FoundRoute> routes = e.getValue();
			filterRoutes(destinationZoneId, routes);
		}
	}

	private void filterRoutes(String destinationZoneId, List<Umlego.FoundRoute> routes) {
		List<Umlego.ConnectedStop> stops = this.stopsPerZone.get(destinationZoneId);
		if (stops != null) {
			Set<TransitStopFacility> destinationStops = stops.stream().map(Umlego.ConnectedStop::stopFacility).collect(Collectors.toSet());
			routes.removeIf(route -> UmlegoUtils.isUselessRoute(route, destinationStops));
		}

		removeDominatedRoutes(routes);
		preselectRoute(routes);
	}

	private void removeDominatedRoutes(List<Umlego.FoundRoute> routes) {
		// sort ascending by departure time, then descending by arrival time
		// if a later route is fully contained in an earlier route, remove the earlier route except it is a direct route (no transfers)
		routes.sort((o1, o2) -> {
			if (o1.depTime < o2.depTime) {
				return -1;
			}
			if (o1.depTime > o2.depTime) {
				return +1;
			}
			if (o1.arrTime < o2.arrTime) {
				return +1; // descending
			}
			if (o1.arrTime > o2.arrTime) {
				return -1; // descending
			}
			return Integer.compare(o1.transfers, o2.transfers);
		});

		List<Umlego.FoundRoute> dominatedRoutes = new ArrayList<>();
		for (int i = 0, n = routes.size(); i < n; i++) {
			Umlego.FoundRoute route1 = routes.get(i);
			if (route1.transfers == 0) {
				// always keep direct routes
				continue;
			}
			for (int j = i + 1; j < n; j++) {
				Umlego.FoundRoute route2 = routes.get(j);

				if (route2.depTime > route1.arrTime) {
					// no further route can be contained in route 1
					break;
				}

				if (route2DominatesRoute1(route2, route1)) {
					dominatedRoutes.add(route1);
				}
			}
		}
		routes.removeAll(dominatedRoutes);
	}

	private void preselectRoute(List<Umlego.FoundRoute> routes) {
		int minTransfers = Integer.MAX_VALUE;
		double minSearchImpedance = Double.POSITIVE_INFINITY;
		double minTraveltime = Double.POSITIVE_INFINITY;
		for (Umlego.FoundRoute route : routes) {
			if (route.transfers < minTransfers) {
				minTransfers = route.transfers;
			}
			if (route.searchImpedance < minSearchImpedance) {
				minSearchImpedance = route.searchImpedance;
			}
			if (route.travelTimeWithAccess < minTraveltime) {
				minTraveltime = route.travelTimeWithAccess;
			}
		}

		ListIterator<Umlego.FoundRoute> it = routes.listIterator();
		while (it.hasNext()) {
			Umlego.FoundRoute route = it.next();
			boolean remove = false;
			if (route.searchImpedance > (this.params.preselection().betaMinImpedance() * minSearchImpedance + this.params.preselection().constImpedance())) {
				remove = true;
			}
//			if (route.travelTimeWithAccess > (2.0 * minTraveltime + + 21.0 * 60) && (route.transfers > minTransfers)) {
//				remove = true;
//			}
			if (route.transfers > (minTransfers + 3) && (route.travelTimeWithAccess > minTraveltime)) {
				remove = true;
			}
			if (remove) {
				it.remove();
			}
		}
	}

	private static boolean route2DominatesRoute1(Umlego.FoundRoute route2, Umlego.FoundRoute route1) {
		boolean isContained = route2.depTime >= route1.depTime && route2.arrTime <= route1.arrTime;
		boolean isStrictlyContained = isContained && (route2.depTime > route1.depTime || route2.arrTime < route1.arrTime);

		boolean equalOrLessTransfers = route2.transfers <= route1.transfers;
		boolean lessTransfers = route2.transfers < route1.transfers;

		boolean equalOrBetterSearchImpedance = route1.searchImpedance >= 1.0 * route2.searchImpedance + 0.0;
		boolean betterSearchImpedance = route1.searchImpedance > 1.0 * route2.searchImpedance + 0.0;

		boolean hasStrictInequality = isStrictlyContained || lessTransfers || betterSearchImpedance;

		return isContained && equalOrLessTransfers && equalOrBetterSearchImpedance && hasStrictInequality;
	}

	private void sortRoutesByDepartureTime(Map<String, List<Umlego.FoundRoute>> foundRoutes) {
		for (List<Umlego.FoundRoute> routes : foundRoutes.values()) {
			routes.sort(UmlegoWorker::compareFoundRoutesByDepartureTime);
		}
	}

	private void calculateRouteCharacteristics(Map<String, List<Umlego.FoundRoute>> foundRoutes) {
		for (List<Umlego.FoundRoute> routes : foundRoutes.values()) {
			for (Umlego.FoundRoute route : routes) {
				calculateRouteCharacteristics(route);
			}
		}
	}

	private void calculateRouteCharacteristics(Umlego.FoundRoute route) {
		double inVehicleTime = 0;
		double accessTime = route.originConnectedStop.walkTime();
		double egressTime = route.destinationConnectedStop.walkTime();
		double walkTime = 0;
		double transferWaitTime = 0;
		double transferCount = route.transfers;

		boolean hadTransferBefore = false;
		int additionalStopCount = 0;
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
				int startIndex = -1;
				int endIndex = -1;
				List<TransitRouteStop> stops = part.route.getStops();
				for (int i = 0; i < stops.size(); i++) {
					TransitRouteStop routeStop = stops.get(i);
					if (routeStop.getStopFacility().getId().equals(part.toStop.getId()) && startIndex >= 0) {
						endIndex = i;
						break;
					}
					if (routeStop.getStopFacility().getId().equals(part.fromStop.getId())) {
						startIndex = i;
					}
				}
				if (startIndex >= 0 && endIndex >= 0) {
					additionalStopCount += (endIndex - startIndex - 1);
				}
			}
		}

		double expectedTotalTime = route.routeParts.get(route.routeParts.size() - 1).arrivalTime - route.routeParts.get(0).vehicleDepTime;
		if ((walkTime + transferWaitTime + inVehicleTime) != expectedTotalTime) {
			System.err.println("INCONSISTENT TIMES " + route.getRouteAsString());
		}
		double totalTravelTime = expectedTotalTime + accessTime + egressTime;

		Umlego.PerceivedJourneyTimeParameters pjtParams = this.params.pjt();
		route.perceivedJourneyTime_min = pjtParams.betaInVehicleTime() * (inVehicleTime / 60.0)
				+ pjtParams.betaAccessTime() * (accessTime / 60.0)
				+ pjtParams.betaEgressTime() * (egressTime / 60.0)
				+ pjtParams.betaWalkTime() * (walkTime / 60.0)
				+ pjtParams.betaTransferWaitTime() * (transferWaitTime / 60.0)
				+ transferCount * (pjtParams.transferFix() + pjtParams.transferTraveltimeFactor() * (totalTravelTime / 60.0))
				+ (pjtParams.secondsPerAdditionalStop() / 60.0) * additionalStopCount;

		Umlego.SearchImpedanceParameters searchParams = this.params.search();
		route.searchImpedance =
				searchParams.betaInVehicleTime() * (inVehicleTime / 60.0)
						+ searchParams.betaAccessTime() * (accessTime / 60.0)
						+ searchParams.betaEgressTime() * (egressTime / 60.0)
						+ searchParams.betaWalkTime() * (walkTime / 60.0)
						+ searchParams.betaTransferWaitTime() * (transferWaitTime / 60.0)
						+ searchParams.betaTransferCount() * transferCount;
	}

	private WorkResult assignDemand(String originZone, Map<String, List<Umlego.FoundRoute>> foundRoutes) {
		sortRoutesByDepartureTime(foundRoutes);
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

		boolean limit = this.params.routeSelection().limitSelectionToTimewindow();
		double earliestDeparture = limit ? (startTime - this.params.routeSelection().beforeTimewindow()) : Integer.MIN_VALUE;
		double latestDeparture = limit ? (endTime + this.params.routeSelection().afterTimewindow()) : Integer.MAX_VALUE;
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
		double[] impedances = new double[potentialRoutes.length];
		double minImpedance = Double.POSITIVE_INFINITY;
		double[] routeUtilities = new double[potentialRoutes.length];
		double betaPJT = this.params.impedance().betaPerceivedJourneyTime();
		double betaDeltaTEarly = this.params.impedance().betaDeltaTEarly();
		double betaDeltaTLate = this.params.impedance().betaDeltaTLate();
		RouteUtilityCalculator utilityCalculator = this.params.routeSelection().utilityCalculator();
		for (int sample = 0; sample < samples; sample++) {
			double time = startTime + sample * stepSize;
			double utilitiesSum = 0;
			for (int i = 0; i < potentialRoutes.length; i++) {
				Umlego.FoundRoute route = potentialRoutes[i];
				double routeDepTime = route.depTime - route.originConnectedStop.walkTime();
				double deltaTEarly = (routeDepTime < time) ? (time - routeDepTime) : 0.0;
				double deltaTLate = (routeDepTime > time) ? (routeDepTime - time) : 0.0;
				double impedance = betaPJT * route.perceivedJourneyTime_min + betaDeltaTEarly * (deltaTEarly / 60.0) + betaDeltaTLate * (deltaTLate / 60.0);
				impedances[i] = impedance;
				if (impedance < minImpedance) {
					minImpedance = impedance;
				}
			}
			for (int i = 0; i < potentialRoutes.length; i++) {
				double impedance = impedances[i];
				double utility = utilityCalculator.calculateUtility(impedance, minImpedance);
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
		if (o1.travelTimeWithoutAccess < o2.travelTimeWithoutAccess) {
			return -1;
		}
		if (o1.travelTimeWithoutAccess > o2.travelTimeWithoutAccess) {
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
