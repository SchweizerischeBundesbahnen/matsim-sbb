package ch.ethz.matsim.discrete_mode_choice.convergence.writer;

import ch.ethz.matsim.discrete_mode_choice.convergence.variables.PointVariable;

import java.io.*;

public class PointWriter implements ConvergenceWriter {
	private final File path;
	private final PointVariable variable;
	private boolean writeHeader = true;

	public PointWriter(File basePath, PointVariable variable) {
		this.path = new File(basePath, "var_" + variable.getName() + ".csv");
		this.variable = variable;
	}

	@Override
	public void write(int iteration) throws IOException {
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path, true)));

		if (writeHeader) {
			writeHeader = false;
			writer.write(String.join(";", new String[] { "iteration", "value" }) + "\n");
		}

		writer.write(String.join(";", new String[] { String.valueOf(iteration), String.valueOf(variable.getValue()) })
				+ "\n");
		writer.close();
	}
}
