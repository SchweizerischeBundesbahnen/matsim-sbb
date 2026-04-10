package ch.sbb.matsim.plans.models.formulas;

import ch.sbb.matsim.plans.synpop.DecisionStrata;

import java.util.Map;
import java.util.Set;

public interface ModeImpedanceFormulaRepository {
        /**
        * Retrieves the map of mode->formula for a given activity and strata.T
        */
        Map<String, String> getModeFormulas(String activity, DecisionStrata strata);

        Map<String, Set<DecisionStrata>> getStratasPerActivityType();
}
