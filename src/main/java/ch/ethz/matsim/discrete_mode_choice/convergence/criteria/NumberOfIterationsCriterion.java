package ch.ethz.matsim.discrete_mode_choice.convergence.criteria;

import java.util.Collections;
import java.util.Map;

public class NumberOfIterationsCriterion extends AbstractCriterion {
	private final long maximumNumberOfIterations;
	private long currentNumberOfIterations = 0;

	public NumberOfIterationsCriterion(String name, long maximumNumberOfIterations) {
		super(name);
		this.maximumNumberOfIterations = maximumNumberOfIterations;
	}

	@Override
	public void update(int iteration) {
		currentNumberOfIterations++;
	}

	@Override
	public boolean isConverged() {
		return currentNumberOfIterations >= maximumNumberOfIterations;
	}

	@Override
	public Map<String, Double> getValues() {
		return Collections.emptyMap();
	}
}
