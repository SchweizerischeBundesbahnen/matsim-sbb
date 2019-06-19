package ch.ethz.matsim.discrete_mode_choice.convergence.variables;

import java.util.Collection;

public interface DistributionVariable extends ConvergenceVariable {
	Collection<Double> getValues();
}
