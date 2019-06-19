package ch.ethz.matsim.discrete_mode_choice.convergence.criteria;

import java.util.Collections;
import java.util.Map;

public class NeverCriterion implements ConvergenceCriterion {
	@Override
	public void update(int iteration) {
	}

	@Override
	public boolean isConverged() {
		return false;
	}

	@Override
	public Map<String, Double> getValues() {
		return Collections.emptyMap();
	}

	@Override
	public String getName() {
		return null;
	}
}
