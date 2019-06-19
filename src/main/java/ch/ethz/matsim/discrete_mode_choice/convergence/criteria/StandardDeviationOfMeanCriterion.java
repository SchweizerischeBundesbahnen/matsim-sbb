package ch.ethz.matsim.discrete_mode_choice.convergence.criteria;

import ch.ethz.matsim.discrete_mode_choice.convergence.variables.PointVariable;

import java.util.*;

public class StandardDeviationOfMeanCriterion extends AbstractCriterion {
	private final List<Double> values = new LinkedList<>();

	private final int windowSize;
	private final double thresholdValue;
	private final PointVariable variable;

	private double mean = Double.NaN;
	private double meanOfMeans = Double.NaN;
	private double standardDeviationOfMeans = Double.NaN;
	private double cv = Double.NaN;

	public StandardDeviationOfMeanCriterion(String name, PointVariable variable, double thresholdValue,
			int windowSize) {
		super(name);
		this.windowSize = windowSize;
		this.variable = variable;
		this.thresholdValue = thresholdValue;
	}

	public StandardDeviationOfMeanCriterion(String name, PointVariable variable, double thresholdValue) {
		this(name, variable, thresholdValue, -1);
	}

	@Override
	public void update(int iteration) {
		values.add(variable.getValue());

		if (values.size() > windowSize) {
			if (windowSize > 0) {
				values.remove(0);
			}

			List<Double> means = new ArrayList<>(values.size());

			double cumulative = 0.0;
			double meanOfMeans = 0.0;

			for (int i = 0; i < values.size(); i++) {
				cumulative += values.get(i);

				double partialMean = cumulative / (double) (i + 1);
				means.add(partialMean);

				meanOfMeans += partialMean;
			}

			meanOfMeans /= values.size();

			double standardDeviationOfMeans = 0.0;

			for (int i = 0; i < values.size(); i++) {
				standardDeviationOfMeans += Math.pow(means.get(i) - meanOfMeans, 2.0);
			}

			standardDeviationOfMeans /= (values.size() - 1); // Deliberately can be NaN

			this.mean = means.get(means.size() - 1);
			this.meanOfMeans = meanOfMeans;
			this.standardDeviationOfMeans = standardDeviationOfMeans;
			this.cv = standardDeviationOfMeans / meanOfMeans;
		}
	}

	@Override
	public boolean isConverged() {
		if (!Double.isNaN(cv)) {
			return cv < thresholdValue;
		} else {
			return false;
		}
	}

	@Override
	public Map<String, Double> getValues() {
		Map<String, Double> values = new HashMap<>();
		values.put("mean", mean);
		values.put("mean_of_means", meanOfMeans);
		values.put("std_of_mean", standardDeviationOfMeans);
		values.put("cv", cv);
		return values;
	}
}
