package ch.sbb.matsim.analysis.convergence;

import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.csv.CSVWriter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.listener.IterationStartsListener;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.io.UncheckedIOException;
import smile.stat.distribution.BetaDistribution;
import smile.stat.distribution.GaussianDistribution;
import smile.stat.hypothesis.CorTest;
import smile.stat.hypothesis.KSTest;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.IntStream;

public class ConvergenceStats implements IterationStartsListener {

    /**
     *
     * Calculates at each iteration the following statistics:
     * <ul>
     * 	<li>Kendall's Trend for each of the global statistics (scores, travel distances and mode shares)</li>
     * 	<li>Stationarity test's p-value with a 5% significance level (test results for trend equals to 0)</li>
     * </ul>
     * To avoid re-calculating all MATSim global stats again, the txt files are parsed for obtaining the data.
     * This is not elegant but if this class one day ends up in Core, the data can then be obtained directly.
     *
     *
     * @author davig
     */

    private Map<Test, CSVWriter> writers;
    private final int windowSize;
    private final int numWindows;
    private List<String> columns;
    private static final String SCORESTATS_FILENAME = "scorestats.txt";
    private static final String TRAVELDISTANCESTATS_FILENAME = "traveldistancestats.txt";
    private static final String MODESTATS_FILENAME = "modestats.txt";
    private static final String COL_STATISTIC = " stat";
    private static final String COL_PVALUE = " p-value";
    private static final String COL_TRAVELDISTANCE = "traveldistances";
    private static final String COL_SCORES = "scores";

    public enum Test {KENDALL, PEARSON, SPEARMAN, KS_NORMAL, KS_UNIFORM}

    @Inject
    public ConvergenceStats(Config config) {
        this(   ConfigUtils.addOrGetModule(config, ConvergenceStatsConfig.class).getWindowSize(),
                ConfigUtils.addOrGetModule(config, ConvergenceStatsConfig.class).getNumWindows());
    }

    public ConvergenceStats(int windowSize, int numWindows) {
        this.numWindows = numWindows;
        this.windowSize = windowSize;
    }

    @Override
    public void notifyIterationStarts(IterationStartsEvent event) {
        if (event.getIteration() >= windowSize * numWindows) {
            if (this.writers == null) {
                setup(event.getServices().getControlerIO());
            }
            calcIteration(event.getServices().getControlerIO());
            for (CSVWriter writer : this.writers.values()) {
                writer.writeRow(true);
            }
        }
    }

    private void setup(OutputDirectoryHierarchy controlerIO) {
        this.columns = new ArrayList<>(Arrays.asList(COL_SCORES, COL_TRAVELDISTANCE));
        this.writers = new EnumMap<>(Test.class);
        List<String> header = new ArrayList<>(Collections.singletonList("ITERATION"));

        try {
            String msHeader = IOUtils.getBufferedReader(controlerIO.getOutputFilename(MODESTATS_FILENAME)).readLine();
            this.columns.addAll(Arrays.asList(msHeader.substring(10).split("\t")));
            for (String m : this.columns) {
                header.add(m + COL_STATISTIC);
                header.add(m + COL_PVALUE);
            }
            Path dir = Files.createDirectory(Paths.get(controlerIO.getOutputPath(), "convergence"));
            for (Test t : Test.values()) {
                this.writers.put(t, new CSVWriter("", header.toArray(new String[0]),
                        Paths.get(dir.toString(), t.name().toLowerCase() + ".txt").toString(), "\t"));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void calcIteration(OutputDirectoryHierarchy controlerIO) {
        Map.Entry<Double, Double> res;
        try {
            double[] scores = loadGlobalStats(controlerIO.getOutputFilename(SCORESTATS_FILENAME));
            double[] traveldistances = loadGlobalStats(controlerIO.getOutputFilename(TRAVELDISTANCESTATS_FILENAME));
            String[] modes = this.columns.subList(2, this.columns.size()).toArray(new String[0]);
            Map<String, List<Double>> modestats = loadGlobalStats(controlerIO.getOutputFilename(MODESTATS_FILENAME), modes);

            for (Test test : Test.values()) {
                res = runTest(test, scores);
                this.writers.get(test).set(COL_SCORES + COL_STATISTIC, String.format("%.4f", res.getKey()));
                this.writers.get(test).set(COL_SCORES + COL_PVALUE, String.format("%.4f", res.getValue()));

                res = runTest(test, traveldistances);
                this.writers.get(test).set(COL_TRAVELDISTANCE + COL_STATISTIC, String.format("%.4f", res.getKey()));
                this.writers.get(test).set(COL_TRAVELDISTANCE + COL_PVALUE, String.format("%.4f", res.getValue()));

                for (String mode : modes) {
                    res = runTest(test, modestats.get(mode).stream().mapToDouble(Double::doubleValue).toArray());
                    this.writers.get(test).set(mode + COL_STATISTIC, String.format("%.4f", res.getKey()));
                    this.writers.get(test).set(mode + COL_PVALUE, String.format("%.4f", res.getValue()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Map.Entry<Double, Double> runTest(Test test, double[] timeseries) {
        double[] filteredTs = Arrays.copyOfRange(timeseries, timeseries.length-windowSize*numWindows, timeseries.length); // filter iterations
        double[] windowedTs = calcWindows(filteredTs, numWindows);
        double[] tsIndex = IntStream.range(0, numWindows).mapToDouble(i -> i).toArray();

        Object results;
        switch (test) {
            case KENDALL:
                results = CorTest.kendall(tsIndex, windowedTs);
                break;
            case PEARSON:
                results = CorTest.pearson(tsIndex, windowedTs);
                break;
            case SPEARMAN:
                results = CorTest.spearman(tsIndex, windowedTs);
                break;
            case KS_NORMAL:
                results = KSTest.test(standardizeTs(windowedTs), new GaussianDistribution(0, 1));
                break;
            case KS_UNIFORM:
                results = KSTest.test(normalizeTs(windowedTs), new BetaDistribution(1, 1));
                break;
            default:
                throw new IllegalStateException();
        }

        double stat = results instanceof CorTest ?
                ((CorTest) results).t : ((KSTest) results).d;
        double pvalue = results instanceof CorTest ?
                ((CorTest) results).pvalue : ((KSTest) results).pvalue;

        return new AbstractMap.SimpleEntry<>(stat, pvalue);
    }

    public static double[] loadGlobalStats(String path) throws IOException {
        Map<String, List<Double>> valuesMap = loadGlobalStats(path, null);
        return valuesMap.values().stream().findFirst().orElseThrow(IllegalStateException::new)
                .stream().mapToDouble(Double::doubleValue).toArray();
    }

    public static Map<String, List<Double>> loadGlobalStats(String path, String... columns) throws IOException {
        Map<String, List<Double>> values = new HashMap<>();
        try (CSVReader csv = new CSVReader(path, "\t")) {
            if (columns == null) {
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
        double average = Arrays.stream(timeseries).average().orElseThrow(IllegalStateException::new);
        double temp = 0;
        for (double a : timeseries) {
            temp += Math.pow(a - average, 2);
        }
        double std = Math.sqrt(temp/(timeseries.length-1));
        return IntStream.range(0, timeseries.length).mapToDouble(i -> (timeseries[i]-average)/std).toArray();
    }

    private double[] normalizeTs(double[] timeseries) {
        double min = Arrays.stream(timeseries).min().orElseThrow(IllegalStateException::new);
        double max = Arrays.stream(timeseries).max().orElseThrow(IllegalStateException::new);
        double[] normalizedTs = new double[timeseries.length];
        for (int i = 0; i < normalizedTs.length; i++) {
            normalizedTs[i] = (timeseries[i] - min) / (max - min) == 0 ? 0.0 : (max - min);
        }
        return normalizedTs;
    }

    private double[] calcWindows(double[] timeseries, int nWindows) {
        List<Double> windowedTs = new ArrayList<>();
        int wSize = timeseries.length / nWindows;
        for (int i = 0; i < timeseries.length; i = i + wSize) {
            windowedTs.add(IntStream.range(i, i + wSize)
                    .mapToDouble(j -> timeseries[j]).average().orElseThrow(IllegalStateException::new));
        }
        return windowedTs.stream().mapToDouble(d -> d).toArray();
    }
}

