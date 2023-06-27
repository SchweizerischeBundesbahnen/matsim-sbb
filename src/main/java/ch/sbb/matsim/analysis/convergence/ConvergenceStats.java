package ch.sbb.matsim.analysis.convergence;

import static ch.sbb.matsim.analysis.convergence.ConvergenceConfigGroup.Test;

import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.csv.CSVWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import jakarta.inject.Inject;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.listener.IterationStartsListener;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.io.UncheckedIOException;
import smile.stat.distribution.GaussianDistribution;
import smile.stat.hypothesis.CorTest;
import smile.stat.hypothesis.KSTest;

public class ConvergenceStats implements IterationStartsListener {

	/**
	 * Calculates at each iteration the following statistics:
	 * <ul>
	 * 	<li>Kendall's Trend for each of the global statistics (scores, travel distances and mode shares)</li>
	 * 	<li>Stationarity test's p-value with a 5% significance level (test results for trend equals to 0)</li>
	 * </ul>
	 * To avoid re-calculating all MATSim global stats again, the txt files are parsed for obtaining the data.
	 * This is not elegant but if this class one day ends up in Core, the data can then be obtained directly.
	 *
	 * @author davig
	 */

	private static final String SCORESTATS_FILENAME = "scorestats.txt";
	private static final String TRAVELDISTANCESTATS_FILENAME = "traveldistancestats.txt";
	private static final String MODESTATS_FILENAME = "modestats.txt";
	private static final String COL_STATISTIC = "_stat";
	private static final String COL_PVALUE = "_p-value";
	private static final String COL_TRAVELDISTANCE = "traveldistances";
	private static final String COL_SCORES = "scores";
	private static final String COL_ITERATION = "ITERATION";
	private static final String COL_CONVERGENCE_FUNCTION_RESULT = "convergenceFunctionResult";
	private final int iterationWindowSize;
	private final Test[] testsToRun;
	private Map<Test, CSVWriter> writers;
	private List<String> columns;
	private final ConvergenceConfigGroup csConfig;
	private double currentConvergenceFunctionResults = 0.0;
	private CSVWriter convergenceFunctionWriter;

	@Inject
	public ConvergenceStats(Config config) {
		this((int) // if using a share of the total iterations is configured, calculate it. Finally cast to int.
						(ConfigUtils.addOrGetModule(config, ConvergenceConfigGroup.class).getIterationWindowSize() < 1.0 ?
								config.controler().getLastIteration() *
										ConfigUtils.addOrGetModule(config, ConvergenceConfigGroup.class).getIterationWindowSize() :
								ConfigUtils.addOrGetModule(config, ConvergenceConfigGroup.class).getIterationWindowSize()),
				ConfigUtils.addOrGetModule(config, ConvergenceConfigGroup.class).getTestsToRun(),
				config);
	}

	public ConvergenceStats(int iterationWindowSize, Test[] testsToRun, Config config) {
		this.csConfig = ConfigUtils.addOrGetModule(config, ConvergenceConfigGroup.class);
		this.iterationWindowSize = iterationWindowSize;
		this.testsToRun = testsToRun;
	}

	private static boolean functionTermMatches(String configTest, String actualTest, String configStat, String actualStat) {
		boolean testMatches = "all".equalsIgnoreCase(configTest) || actualTest.equalsIgnoreCase(configTest);
		boolean statMatches = "all".equalsIgnoreCase(configStat) || actualStat.equalsIgnoreCase(configStat);
		return testMatches && statMatches;
	}

	public static double[] loadGlobalStats(String path) throws IOException {
		Map<String, List<Double>> valuesMap = loadGlobalStats(path, new String[0]);
		return valuesMap.values().stream().findFirst().orElseThrow(IllegalStateException::new)
				.stream().mapToDouble(Double::doubleValue).toArray();
	}

	public static Map<String, List<Double>> loadGlobalStats(String path, String... columns) throws IOException {
		Map<String, List<Double>> values = new HashMap<>();
		try (CSVReader csv = new CSVReader(path, "\t")) {
			if (columns.length == 0) {
				columns = Arrays.copyOfRange(csv.getColumns(), 1, 2);
			}
			Map<String, String> data;
			while ((data = csv.readLine()) != null) {
				for (String c : columns) {
					values.putIfAbsent(c, new ArrayList<>());
					values.get(c).add(Double.valueOf(data.get(c)));
				}
			}
		}
		return values;
	}


	@Override
	public void notifyIterationStarts(IterationStartsEvent event) {
		this.currentConvergenceFunctionResults = 0.0; // reset value for next iteration
		if (event.getIteration() >= iterationWindowSize) {
			if (this.writers == null) {
				setup(event.getServices().getControlerIO());
			}
			calcIteration(event.getServices().getControlerIO(), event.getIteration());
			for (CSVWriter writer : this.writers.values()) {
				writer.writeRow(true);
			}
			this.convergenceFunctionWriter.writeRow(true);
		}
	}

	private void setup(OutputDirectoryHierarchy controlerIO) {
		this.columns = new ArrayList<>(Arrays.asList(COL_SCORES, COL_TRAVELDISTANCE));
		this.writers = new EnumMap<>(Test.class);
		List<String> header = new ArrayList<>(Collections.singletonList(COL_ITERATION));

		try {
			String msHeader = IOUtils.getBufferedReader(controlerIO.getOutputFilename(MODESTATS_FILENAME)).readLine();
			this.columns.addAll(Arrays.asList(msHeader.substring(10).split("\t")));
			for (String m : this.columns) {
				header.add(m + COL_STATISTIC);
				header.add(m + COL_PVALUE);
			}
			Path dir = Files.createDirectory(Paths.get(controlerIO.getOutputPath(), "convergence"));
			for (Test t : this.testsToRun) {
				this.writers.put(t, new CSVWriter("", header.toArray(new String[0]),
						Paths.get(dir.toString(), t.name().toLowerCase() + ".txt").toString(), "\t"));
			}
			this.convergenceFunctionWriter = new CSVWriter("", new String[]{COL_ITERATION, COL_CONVERGENCE_FUNCTION_RESULT},
					Paths.get(dir.toString(), COL_CONVERGENCE_FUNCTION_RESULT + ".txt").toString(), "\t");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void calcIteration(OutputDirectoryHierarchy controlerIO, int iteration) {
		Map.Entry<Double, Double> res;
		try {
			double[] scores = loadGlobalStats(controlerIO.getOutputFilename(SCORESTATS_FILENAME));
			double[] traveldistances = loadGlobalStats(controlerIO.getOutputFilename(TRAVELDISTANCESTATS_FILENAME));
			String[] modes = this.columns.subList(2, this.columns.size()).toArray(new String[0]);
			Map<String, List<Double>> modestats = loadGlobalStats(controlerIO.getOutputFilename(MODESTATS_FILENAME), modes);

			for (Test test : this.testsToRun) {
				this.writers.get(test).set(COL_ITERATION, String.valueOf(iteration));
				res = runTest(test, scores);
				this.writers.get(test).set(COL_SCORES + COL_STATISTIC, String.format("%.4f", res.getKey()));
				this.writers.get(test).set(COL_SCORES + COL_PVALUE, String.format("%.4f", res.getValue()));
				this.currentConvergenceFunctionResults += calcConvergenceFuntionTerm(test, "scores", res.getKey());

				res = runTest(test, traveldistances);
				this.writers.get(test).set(COL_TRAVELDISTANCE + COL_STATISTIC, String.format("%.4f", res.getKey()));
				this.writers.get(test).set(COL_TRAVELDISTANCE + COL_PVALUE, String.format("%.4f", res.getValue()));
				this.currentConvergenceFunctionResults += calcConvergenceFuntionTerm(test, "traveldistances", res.getKey());

				for (String mode : modes) {
					res = runTest(test, modestats.get(mode).stream().mapToDouble(Double::doubleValue).toArray());
					this.writers.get(test).set(mode + COL_STATISTIC, String.format("%.4f", res.getKey()));
					this.writers.get(test).set(mode + COL_PVALUE, String.format("%.4f", res.getValue()));
					this.currentConvergenceFunctionResults += calcConvergenceFuntionTerm(test, mode, res.getKey());
				}
			}
			this.convergenceFunctionWriter.set(COL_ITERATION, String.valueOf(iteration));
			this.convergenceFunctionWriter.set(COL_CONVERGENCE_FUNCTION_RESULT, String.valueOf(this.currentConvergenceFunctionResults));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private double calcConvergenceFuntionTerm(Test test, String globalStat, double testRes) {
		if (Double.isNaN(testRes)) {
			return 0.0; // NaN results don't add to the function (this might bias the results downwards)
		}
		for (ConvergenceConfigGroup.ConvergenceCriterionFunctionWeight weightParam : this.csConfig.getFunctionWeights()) {
			if (functionTermMatches(weightParam.getConvergenceTest(), test.name(), weightParam.getGlobalStatistic(), globalStat)) {
				return Math.abs(testRes * weightParam.getFunctionWeight());
			}
		}
		return Math.abs(testRes); // default weight is 1.0
	}

	public Map.Entry<Double, Double> runTest(Test test, double[] timeseries) {
		double[] filteredTs = Arrays.copyOfRange(timeseries, timeseries.length - iterationWindowSize, timeseries.length); // filter iterations
		double[] tsIndex = IntStream.range(0, iterationWindowSize).mapToDouble(i -> i).toArray();

		double stat;
		double pvalue = Double.NaN;
		switch (test) {
			case KENDALL:
				CorTest corrRes = CorTest.kendall(tsIndex, filteredTs);
				stat = corrRes.cor;
				pvalue = corrRes.pvalue;
				break;
			case KS_NORMAL:
				KSTest ksRes = KSTest.test(standardizeTs(filteredTs), new GaussianDistribution(0, 1));
				stat = ksRes.d;
				pvalue = ksRes.pvalue;
				break;
			case CV:
				DescriptiveStatistics ds = new DescriptiveStatistics(filteredTs);
				stat = ds.getStandardDeviation() / ds.getMean();
				break;
			default:
				throw new IllegalArgumentException("Unrecognized option " + test.name());
		}

		return new AbstractMap.SimpleEntry<>(stat, pvalue);
	}

	public void close() {
		try {
			for (CSVWriter writer : this.writers.values()) {
				writer.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private double[] standardizeTs(double[] timeseries) {
		DescriptiveStatistics ds = new DescriptiveStatistics(timeseries);
		return Arrays.stream(timeseries).map(timesery -> (timesery - ds.getMean()) / ds.getStandardDeviation()).toArray();
	}

}

