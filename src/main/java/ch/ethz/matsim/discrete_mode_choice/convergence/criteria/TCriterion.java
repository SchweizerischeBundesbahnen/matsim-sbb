package ch.ethz.matsim.discrete_mode_choice.convergence.criteria;

import ch.ethz.matsim.discrete_mode_choice.convergence.variables.PointVariable;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.inference.TTest;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class TCriterion extends AbstractCriterion {
	private final List<Double> values = new LinkedList<>();
	private final PointVariable variable;
	private final double significanceLevel;

	private double t = Double.NaN;
	private double p = Double.NaN;
	private double mean = Double.NaN;

	public TCriterion(String name, PointVariable variable, double significanceLevel) {
		super(name);
		this.variable = variable;
		this.significanceLevel = significanceLevel;
	}

	@Override
	public void update(int iteration) {
		values.add(variable.getValue());

		DescriptiveStatistics statistics = new DescriptiveStatistics();
		values.forEach(statistics::addValue);

		if (values.size() > 1) {
			mean = values.stream().mapToDouble(d -> d).sum() / values.size();
			t = new TTest().t(mean, statistics);
			p = new TTest().tTest(mean, statistics);
		}
	}

	@Override
	public boolean isConverged() {
		return p < significanceLevel;
	}

	@Override
	public Map<String, Double> getValues() {
		Map<String, Double> values = new HashMap<>();
		values.put("mean", mean);
		values.put("t", t);
		values.put("p", p);
		return values;
	}
}
