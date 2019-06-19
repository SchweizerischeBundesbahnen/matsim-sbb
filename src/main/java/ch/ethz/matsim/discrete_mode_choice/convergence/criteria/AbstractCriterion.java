package ch.ethz.matsim.discrete_mode_choice.convergence.criteria;

public abstract class AbstractCriterion implements ConvergenceCriterion {
	private final String name;

	public AbstractCriterion(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return name;
	}
}
