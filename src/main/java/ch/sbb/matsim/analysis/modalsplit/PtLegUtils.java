package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.SBBModes.PTSubModes;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.List;

/**
 * Static utility methods for PT leg analysis.
 * Centralizes mode detection and normalization that was previously duplicated across ModalSplitStats.
 */
public final class PtLegUtils {

    private PtLegUtils() {}

    /**
     * Returns true if this leg is a public-transport leg (including routing-mode variants like rail_route).
     */
    public static boolean isPtLeg(Leg leg) {
        if (leg == null) return false;
        String mode = leg.getMode();
        return SBBModes.isPTMode(mode)
                || SBBModes.PT_ROUTING_MODES.contains(mode)
                || leg.getRoute() instanceof TransitPassengerRoute;
    }

    /**
     * Normalizes routing modes (rail_route, bus_route, …) to their passenger-facing equivalents (rail, bus, …).
     * Non-routing modes are returned unchanged.
     */
    public static String normalizePtMode(String mode) {
        return switch (mode) {
            case SBBModes.PTRoutingModes.RAIL_ROUTE -> PTSubModes.RAIL;
            case SBBModes.PTRoutingModes.BUS_ROUTE  -> PTSubModes.BUS;
            case SBBModes.PTRoutingModes.TRAM_ROUTE -> PTSubModes.TRAM;
            case SBBModes.PTRoutingModes.OTHER_ROUTE -> PTSubModes.OTHER;
            default -> mode;
        };
    }

    /** Returns true if the leg is a rail (or rail_route) leg. */
    public static boolean isRailLeg(Leg leg) {
        if (leg == null) return false;
        String mode = leg.getMode();
        return PTSubModes.RAIL.equals(mode) || SBBModes.PTRoutingModes.RAIL_ROUTE.equals(mode);
    }

    /** Returns the stop facility where this PT leg boards. */
    public static TransitStopFacility accessStop(Leg leg, TransitSchedule schedule) {
        return schedule.getFacilities().get(((TransitPassengerRoute) leg.getRoute()).getAccessStopId());
    }

    /** Returns the stop facility where this PT leg alights. */
    public static TransitStopFacility egressStop(Leg leg, TransitSchedule schedule) {
        return schedule.getFacilities().get(((TransitPassengerRoute) leg.getRoute()).getEgressStopId());
    }

    /**
     * Returns the first non-walk leg strictly after {@code fromIndex}, or {@code null} if none exists.
     */
    public static Leg nextNonWalkLeg(List<Leg> legs, int fromIndex) {
        for (int i = fromIndex + 1; i < legs.size(); i++) {
            if (!legs.get(i).getMode().contains(SBBModes.WALK_FOR_ANALYSIS)) {
                return legs.get(i);
            }
        }
        return null;
    }

    /** Index of the first rail leg in the list, or -1. */
    public static int firstRailLegIndex(List<Leg> legs) {
        for (int i = 0; i < legs.size(); i++) {
            if (isRailLeg(legs.get(i))) return i;
        }
        return -1;
    }

    /** Index of the last rail leg in the list, or -1. */
    public static int lastRailLegIndex(List<Leg> legs) {
        int last = -1;
        for (int i = 0; i < legs.size(); i++) {
            if (isRailLeg(legs.get(i))) last = i;
        }
        return last;
    }
}

