package ch.ethz.matsim.discrete_mode_choice.convergence.writer;

import ch.ethz.matsim.discrete_mode_choice.convergence.variables.DistributionVariable;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.io.*;

public class DistributionWriter implements ConvergenceWriter {
	private final File path;
	private final DistributionVariable variable;
	private boolean writeHeader = true;

	public DistributionWriter(File basePath, DistributionVariable variable) {
		this.path = new File(basePath, "var_" + variable.getName() + ".csv");
		this.variable = variable;
	}

	@Override
	public void write(int iteration) throws IOException {
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path, true)));

		if (writeHeader) {
			writeHeader = false;
			writer.write(String.join(";", new String[] { "iteration", "mean", "std", "min", "q10", "q20", "q30", "q40",
					"q50", "q60", "q70", "q80", "q90", "max" }) + "\n");
		}

		DescriptiveStatistics statistics = new DescriptiveStatistics();
		variable.getValues().forEach(statistics::addValue);

		writer.write(String.join(";",
				new String[] { String.valueOf(iteration), String.valueOf(statistics.getMean()),
						String.valueOf(statistics.getStandardDeviation()), String.valueOf(statistics.getMin()),
						String.valueOf(statistics.getPercentile(10.0)), String.valueOf(statistics.getPercentile(20.0)),
						String.valueOf(statistics.getPercentile(30.0)), String.valueOf(statistics.getPercentile(40.0)),
						String.valueOf(statistics.getPercentile(50.0)), String.valueOf(statistics.getPercentile(60.0)),
						String.valueOf(statistics.getPercentile(70.0)), String.valueOf(statistics.getPercentile(80.0)),
						String.valueOf(statistics.getPercentile(90.0)), String.valueOf(statistics.getMax()) })
				+ "\n");
		writer.close();
	}
}
