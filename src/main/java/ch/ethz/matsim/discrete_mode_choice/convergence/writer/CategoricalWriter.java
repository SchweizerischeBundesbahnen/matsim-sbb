package ch.ethz.matsim.discrete_mode_choice.convergence.writer;

import ch.ethz.matsim.discrete_mode_choice.convergence.variables.CategoricalVariable;

import java.io.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class CategoricalWriter implements ConvergenceWriter {
	private final File path;
	private final CategoricalVariable variable;
	private final List<String> orderedCategories = new LinkedList<>();

	private boolean writeHeader = true;

	public CategoricalWriter(File basePath, CategoricalVariable variable) {
		this.path = new File(basePath, "var_" + variable.getName() + ".csv");
		this.variable = variable;
		this.orderedCategories.addAll(variable.getValues().keySet());
	}

	@Override
	public void write(int iteration) throws IOException {
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path, true)));

		if (writeHeader) {
			List<String> columns = new LinkedList<>();
			columns.add("iteration");
			columns.addAll(orderedCategories);

			writeHeader = false;
			writer.write(String.join(";", columns) + "\n");
		}

		Map<String, Double> values = variable.getValues();

		List<String> columns = new LinkedList<>();
		columns.add(String.valueOf(iteration));
		orderedCategories.forEach(c -> columns.add(String.valueOf(values.get(c))));

		writer.write(String.join(";", columns) + "\n");
		writer.close();
	}
}
