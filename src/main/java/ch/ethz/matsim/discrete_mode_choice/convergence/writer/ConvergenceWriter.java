package ch.ethz.matsim.discrete_mode_choice.convergence.writer;

import java.io.IOException;

public interface ConvergenceWriter {
	void write(int iteration) throws IOException;
}
