package ch.sbb.matsim.analysis.modalsplit;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.routing.SBBAnalysisMainModeIdentifier;
import java.util.Comparator;
import java.util.List;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.router.MainModeIdentifier;

public class LongestMainModeIdentifier implements MainModeIdentifier {

    @Override
    public String identifyMainMode(List<? extends PlanElement> tripElements) {
        if (SBBModes.PT.equals(new SBBAnalysisMainModeIdentifier().identifyMainMode(tripElements))) {
            return SBBModes.PT;
        }
        Leg leg = tripElements.stream()
            .filter(t -> t instanceof Leg)
            .map(t -> ((Leg) t))
            .max(Comparator.comparing(tmpLeg -> tmpLeg.getRoute().getDistance())).orElse(null);
        if (leg == null) {
            throw new NullPointerException("The tripElements should contain at least one leg");
        }
        return new SBBAnalysisMainModeIdentifier().identifyMainMode(List.of(leg));
    }
}
