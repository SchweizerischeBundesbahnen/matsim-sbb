package ch.ethz.matsim.discrete_mode_choice.convergence.criteria;

import java.util.Map;

public interface ConvergenceCriterion {
	void update(int iteration);

	boolean isConverged();

	Map<String, Double> getValues();
	
	String getName();
}
