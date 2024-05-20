package ch.sbb.matsim.umlego;

import ch.sbb.matsim.routing.pt.raptor.RaptorRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.HashSet;
import java.util.Set;

public class UmlegoUtils {

	public static boolean isUselessRoute(Umlego.FoundRoute route) {
		/* A Route is considered useless, if:
		 *  - "passes start stop": it initially leads into the opposite direction, and then in the second (or later) part passes through the original start stop
		 *  - "overshoot" it reaches the destination stop, but then continues on and comes back with at least one additional transfer
		 */
		if (passesStartStop(route)) {
			return true;
		}
		if (overshoots(route)) {
			return true;
		}

		return false;
	}

	public static boolean isUselessRoute(Umlego.FoundRoute route, Set<TransitStopFacility> destinationStops) {
		/* A Route is considered useless, if it passes more than one destination stops:
		 */
		if (passesMoreThanOnce(route, destinationStops)) {
			return true;
		}

		return false;
	}

	private static boolean passesStartStop(Umlego.FoundRoute route) {
		var stages = route.routeParts;
		if (stages.isEmpty()) {
			return false;
		}
		TransitStopFacility startStop = route.originStop;
		// there are transfers
		for (int i = 1; i < stages.size(); i++) {
			RaptorRoute.RoutePart stage = stages.get(i);
			if (stage.route == null) {
				// it's a transfer
				continue;
			}
			boolean boarded = false;
			for (TransitRouteStop routeStop : stage.route.getStops()) {
				if (routeStop.getStopFacility() == stage.toStop) {
					break;
				}
				if (boarded && routeStop.getStopFacility() == startStop) {
					return true;
				}
				if (routeStop.getStopFacility() == stage.fromStop) {
					boarded = true;
				}
			}
		}
		return false;
	}

	private static boolean overshoots(Umlego.FoundRoute route) {
		/* we say a route overshoots if a later stage arrives at a stop it already was before */
		Set<TransitStopFacility> reachedStops = new HashSet<>();
		for (RaptorRoute.RoutePart stage : route.routeParts) {
			if (stage.route == null) {
				// it is a transfer
				continue;
			}
			Set<TransitStopFacility> stageReachedStops = new HashSet<>();
			boolean boarded = false;
			for (TransitRouteStop routeStop : stage.route.getStops()) {
				if (boarded && (routeStop.getStopFacility() == stage.toStop)) {
					if (!reachedStops.add(stage.toStop)) {
						// we already started or arrived at this stop in a previous stage
						return true;
					}
				}
				if (routeStop.getStopFacility() == stage.fromStop) {
					boarded = true;
				}
				if (boarded && routeStop.getStopFacility() == stage.toStop) {
					break;
				}
				if (boarded) {
					// collect the reached stops in this stage separately, in case the route contains a loop and serves a stop multiple times
					stageReachedStops.add(routeStop.getStopFacility());
				}
			}
			reachedStops.addAll(stageReachedStops);
		}
		return false;
	}

	private static boolean passesMoreThanOnce(Umlego.FoundRoute route, Set<TransitStopFacility> stops) {
		boolean passedStop = false;
		for (RaptorRoute.RoutePart stage : route.routeParts) {
			if (stage.route == null) {
				// it is a transfer
				continue;
			}
			boolean boarded = false;
			for (TransitRouteStop routeStop : stage.route.getStops()) {
				if (routeStop.getStopFacility() == stage.fromStop) {
					boarded = true;
				}
				if (boarded) {
					if (stops.contains(routeStop.getStopFacility())) {
						if (passedStop) {
							return true;
						}
						passedStop = true;
					}
				}
				if (routeStop.getStopFacility() == stage.toStop) {
					break;
				}
			}
		}
		return false;
	}

}
