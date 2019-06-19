package ch.ethz.matsim.discrete_mode_choice.convergence.variables;

public interface ConvergenceVariable {
	void update(int iteration);

	String getName();
}
