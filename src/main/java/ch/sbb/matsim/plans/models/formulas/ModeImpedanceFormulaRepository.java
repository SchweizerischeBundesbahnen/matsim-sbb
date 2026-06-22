package ch.sbb.matsim.plans.models.formulas;

import ch.sbb.matsim.plans.models.steps.demandstrata.DemandStrata;

import java.util.Map;
import java.util.Set;

public interface ModeImpedanceFormulaRepository {
        /**
        * Retrieves the map of mode->formula for a given activity and strata.T
        */
        Map<String, String> getModeFormulas(String activity, DemandStrata strata);

        Map<String, Set<DemandStrata>> getStratasPerActivityType();
}
