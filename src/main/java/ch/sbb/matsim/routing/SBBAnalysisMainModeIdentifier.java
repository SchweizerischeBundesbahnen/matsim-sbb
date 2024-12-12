package ch.sbb.matsim.routing;

import ch.sbb.matsim.config.variables.SBBModes;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.router.AnalysisMainModeIdentifier;

import java.util.List;
import java.util.stream.Collectors;


public class SBBAnalysisMainModeIdentifier implements AnalysisMainModeIdentifier {
    /**
     * @param tripElements
     * @return the main mode for analysis purpose, as defined in SBB. This should usually return the routing mode,
     * which is however not defined in experienced plans. Trips that contain only pt feeder legs are considered pt.
     */
    @Override
    public String identifyMainMode(List<? extends PlanElement> tripElements) {
        List<Leg> legs = tripElements.stream().
                filter(planElement -> planElement instanceof Leg).
                map(planElement -> (Leg) planElement).
                collect(Collectors.toList());
        String routingMode = legs.get(0).getRoutingMode();
        if (routingMode != null) {
            return routingMode;
        } else {
            //this fallback should not be needed unless for totally unrouted plans
            if (legs.stream().anyMatch(leg -> SBBModes.PT_PASSENGER_MODES.contains(leg.getMode()))) return SBBModes.PT;
            else if (legs.stream().anyMatch(leg -> SBBModes.CAR.equals(leg.getMode()))) return SBBModes.CAR;
            else if (legs.stream().anyMatch(leg -> SBBModes.RIDE.equals(leg.getMode()))) return SBBModes.RIDE;
            else if (legs.stream().anyMatch(leg -> SBBModes.AVTAXI.equals(leg.getMode()))) return SBBModes.AVTAXI;
            else if (legs.stream().anyMatch(leg -> SBBModes.WALK_MAIN_MAINMODE.equals(leg.getMode())))
                return SBBModes.WALK_MAIN_MAINMODE;
            else if (legs.stream().anyMatch(leg -> SBBModes.PT_FEEDER_MODES.contains(leg.getMode())))
                return SBBModes.PT;
            else if (legs.size() == 1) return legs.get(0).getMode();

        }

        throw new RuntimeException("mode could not be found.");
    }
}
