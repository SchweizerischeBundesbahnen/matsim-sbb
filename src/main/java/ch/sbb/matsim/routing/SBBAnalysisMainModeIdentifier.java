package ch.sbb.matsim.routing;

import ch.sbb.matsim.config.variables.SBBModes;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.router.AnalysisMainModeIdentifier;

import java.util.List;


public class SBBAnalysisMainModeIdentifier implements AnalysisMainModeIdentifier {
    /**
     * @param tripElements
     * @return the main mode for analysis purpose, as defined in SBB. This should usually return the routing mode,
     * which is however not defined in experienced plans. Trips that contain only pt feeder legs are considered pt.
     */
    @Override
    public String identifyMainMode(List<? extends PlanElement> tripElements) {
        int modeNo = tripElements.stream()
                .filter(t -> t instanceof Leg)
                .map(t -> ((Leg) t))
                .mapToInt(leg -> {
                    Integer i = SBBModes.mode2HierarchalNumber.get(leg.getMode());
                    if (i != null) return i;
                    else throw new NullPointerException(leg.getMode() + " is not a known mode in the mode hierarchy.");
                })
                .min()
                .getAsInt();

        if (modeNo >= 90 && modeNo < 99) return SBBModes.PT;

        String mode = SBBModes.hierarchalNumber2Mode.get(modeNo);
        if (SBBModes.PT_PASSENGER_MODES.contains(mode)) return SBBModes.PT;
        return mode;
    }
}
