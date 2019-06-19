package ch.ethz.matsim.discrete_mode_choice.convergence.criteria;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompositeCriterion extends AbstractCriterion {
	private final List<ConvergenceCriterion> criteria;
	private final boolean useAnd;

	private CompositeCriterion(boolean useAnd, List<ConvergenceCriterion> criteria) {
		super(null);
		
		this.criteria = criteria;
		this.useAnd = useAnd;
	}

	@Override
	public void update(int iteration) {
		criteria.forEach(c -> c.update(iteration));
	}

	@Override
	public boolean isConverged() {
		boolean isConverged = useAnd ? true : false;

		for (ConvergenceCriterion criterion : criteria) {
			if (useAnd) {
				isConverged &= criterion.isConverged();
			} else {
				isConverged |= criterion.isConverged();
			}
		}

		return isConverged;
	}

	@Override
	public Map<String, Double> getValues() {
		Map<String, Double> values = new HashMap<>();

		for (ConvergenceCriterion criterion : criteria) {
			values.putAll(criterion.getValues());
		}

		return values;
	}

	static public CompositeCriterion combineAnd(List<ConvergenceCriterion> criteria) {
		return new CompositeCriterion(true, criteria);
	}

	static public CompositeCriterion combineOr(List<ConvergenceCriterion> criteria) {
		return new CompositeCriterion(false, criteria);
	}

	static public CompositeCriterion combineAnd(ConvergenceCriterion... criteria) {
		return combineAnd(Arrays.asList(criteria));
	}

	static public CompositeCriterion combineOr(ConvergenceCriterion... criteria) {
		return combineOr(Arrays.asList(criteria));
	}
}
