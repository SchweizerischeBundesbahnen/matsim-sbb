package ch.ethz.matsim.discrete_mode_choice.convergence.writer;

import ch.ethz.matsim.discrete_mode_choice.convergence.criteria.ConvergenceCriterion;

import java.io.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class CriterionWriter implements ConvergenceWriter {
	private final File path;
	private final ConvergenceCriterion criterion;
	private final List<String> columns = new LinkedList<>();

	private boolean writeHeader = true;

	public CriterionWriter(File basePath, ConvergenceCriterion criterion) {
		this.path = new File(basePath, "crit_" + criterion.getName() + ".csv");
		this.criterion = criterion;
		this.columns.addAll(criterion.getValues().keySet());
	}

	@Override
	public void write(int iteration) throws IOException {
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path, true)));

		if (writeHeader) {
			List<String> columns = new LinkedList<>();
			columns.add("iteration");
			columns.add("converged");
			columns.addAll(this.columns);

			writeHeader = false;
			writer.write(String.join(";", columns) + "\n");
		}

		Map<String, Double> values = criterion.getValues();

		List<String> columns = new LinkedList<>();
		columns.add(String.valueOf(iteration));
		columns.add(String.valueOf(criterion.isConverged()));
		this.columns.forEach(c -> columns.add(String.valueOf(values.get(c))));

		writer.write(String.join(";", columns) + "\n");
		writer.close();
	}
}
