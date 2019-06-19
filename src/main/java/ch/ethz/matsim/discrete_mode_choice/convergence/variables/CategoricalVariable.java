package ch.ethz.matsim.discrete_mode_choice.convergence.variables;

import java.util.Map;

public interface CategoricalVariable extends ConvergenceVariable {
	Map<String, Double> getValues();
}
