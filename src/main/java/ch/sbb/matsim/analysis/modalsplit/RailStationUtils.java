package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.config.variables.SBBModes;
import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.Set;
import java.util.stream.Collectors;

import static ch.sbb.matsim.analysis.modalsplit.MSVariables.STOP_NO;

/**
 * Utilities to derive rail-served stops and stations from the transit schedule.
 */
public final class RailStationUtils {

    private RailStationUtils() {
    }

    public static boolean isRailTransportMode(String transportMode) {
        return SBBModes.PTSubModes.RAIL.equals(transportMode)
                || SBBModes.PTRoutingModes.RAIL_ROUTE.equals(transportMode);
    }

    public static Set<Id<TransitStopFacility>> buildRailStopIds(TransitSchedule schedule) {
        return schedule.getTransitLines().values().stream()
                .flatMap(line -> line.getRoutes().values().stream())
                .filter(route -> isRailTransportMode(route.getTransportMode()))
                .flatMap(route -> route.getStops().stream())
                .map(stop -> stop.getStopFacility().getId())
                .collect(Collectors.toSet());
    }

    public static Set<String> buildRailStationIds(TransitSchedule schedule) {
        return schedule.getTransitLines().values().stream()
                .flatMap(line -> line.getRoutes().values().stream())
                .filter(route -> isRailTransportMode(route.getTransportMode()))
                .flatMap(route -> route.getStops().stream())
                .map(stop -> String.valueOf(stop.getStopFacility().getAttributes().getAttribute(STOP_NO)))
                .collect(Collectors.toSet());
    }
}

