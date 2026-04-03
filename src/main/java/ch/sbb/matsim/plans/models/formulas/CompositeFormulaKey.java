package ch.sbb.matsim.plans.models.formulas;

import ch.sbb.matsim.plans.synpop.DecisionStrata;

import java.util.Objects;

/**
 * An immutable key using the Activity Name and the DecisionStrata Enum.
 */
public final class CompositeFormulaKey {
    private final String activity;
    private final DecisionStrata strata;

    public CompositeFormulaKey(String activity, DecisionStrata strata) {
        this.activity = activity;
        this.strata = strata;
    }

    // Getters, equals, and hashCode...
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompositeFormulaKey that = (CompositeFormulaKey) o;
        return Objects.equals(activity, that.activity) && strata == that.strata;
    }

    @Override
    public int hashCode() {
        return Objects.hash(activity, strata);
    }

    @Override
    public String toString() {
        return "activity='" + activity + '\'' + ", strata=" + strata;
    }

    public String getActivity() {
        return activity;
    }

    public DecisionStrata getStrata() {
        return strata;
    }
}