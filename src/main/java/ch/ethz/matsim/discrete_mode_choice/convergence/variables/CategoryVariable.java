package ch.ethz.matsim.discrete_mode_choice.convergence.variables;

public class CategoryVariable implements PointVariable {
	private final CategoricalVariable delegate;
	private final String category;

	public CategoryVariable(CategoricalVariable delegate, String category) {
		this.delegate = delegate;
		this.category = category;
	}

	@Override
	public void update(int iteration) {
		delegate.update(iteration);
	}

	@Override
	public String getName() {
		return String.format("%s_%s", delegate.getName(), category);
	}

	@Override
	public double getValue() {
		return delegate.getValues().get(category);
	}
}
