package ch.sbb.matsim.analysis.skims;

import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.csv.CSVWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.apache.commons.lang3.mutable.MutableInt;

/**
 * @author jbischoff / SBB
 */
public class AnalyzeSkimDifferences {

	public static final String FROM_1 = "FROM";
	public static final String TO_1 = "TO";
	public static final String REFVALUE_1 = "REFVALUE";
	public static final String DIFF = "DIFF_";
	private final String inputfile1;
	private final String outputFile;
	private final List<String> compareFiles;
	final Map<Integer, Map<Integer, SkimsValue>> skimsCache = new HashMap<>();

	public AnalyzeSkimDifferences(String inputfile1, String outputFile, List<String> compareFiles) {
		this.inputfile1 = inputfile1;
		this.outputFile = outputFile;
		this.compareFiles = compareFiles;
	}

	public static void main(String[] args) throws RuntimeException {

		if (args.length >= 3) {
			String inputfile1 = args[0];
			String outputfile = args[1];
			List<String> comparefiles = Arrays.asList(Arrays.copyOfRange(args, 2, args.length));

			new AnalyzeSkimDifferences(inputfile1, outputfile, comparefiles).run();
		} else {
			throw new RuntimeException("Expected at least three arguments: inputSkims, outputFile, [files to compare with]");
		}

	}

	private void run() {
		readReference();
		int i = 0;
		for (String file : compareFiles) {
			readCompareSkims(file, i);
			i++;
		}
		writeDifferences();
		writeZonalDiffs();

	}

	private void writeZonalDiffs() {
		String[] columns = getColumnsChanges();
		List<Map<Integer, Double>> zoneDiffs = new ArrayList<>();
		for (final MutableInt i = new MutableInt(0); i.intValue() < compareFiles.size(); i.increment()) {
			Map<Integer, Double> zoneDiff = this.skimsCache.entrySet().parallelStream()
					.collect(Collectors.toMap(
							Entry::getKey,
							z ->
									z.getValue().values().stream().mapToDouble(s -> Double.isNaN(s.diffs[i.intValue()]) ? 0 : Math.abs(s.diffs[i.intValue()])).sum())

					);
			zoneDiffs.add(zoneDiff);
		}
		try (CSVWriter writer = new CSVWriter(null, columns, outputFile + "zondediffs.csv")) {
			for (Integer zone : skimsCache.keySet()) {
				writer.set(FROM_1, Integer.toString(zone));
				int i = 0;
				for (Map<Integer, Double> zoneDiff : zoneDiffs) {
					double value = zoneDiff.get(zone);
					writer.set(DIFF + i, Double.toString(value));
					i++;
				}
				writer.writeRow();

			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void writeDifferences() {
		String[] columns = getColumns();
		try (CSVWriter writer = new CSVWriter(null, columns, outputFile + "absolutediffs.csv.gz")) {
			for (Map.Entry<Integer, Map<Integer, SkimsValue>> fromMap : this.skimsCache.entrySet()) {
				String from = Integer.toString(fromMap.getKey());
				for (Map.Entry<Integer, SkimsValue> skimsValueEntry : fromMap.getValue().entrySet()) {
					writer.set(FROM_1, from);
					String to = Integer.toString(skimsValueEntry.getKey());
					writer.set(TO_1, to);
					String refvalue = Double.toString(skimsValueEntry.getValue().origValue);
					writer.set(REFVALUE_1, refvalue);
					double[] diffs = skimsValueEntry.getValue().diffs;
					for (int i = 0; i < diffs.length; i++) {
						writer.set(DIFF + i, Double.toString(diffs[i]));
					}
					writer.writeRow();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String[] getColumns() {
		String[] columns = new String[compareFiles.size() + 3];
		columns[0] = FROM_1;
		columns[1] = TO_1;
		columns[2] = REFVALUE_1;
		for (int i = 0; i < compareFiles.size(); i++) {
			columns[3 + i] = DIFF + i;
		}
		return columns;
	}

	private String[] getColumnsChanges() {
		String[] columns = new String[compareFiles.size() + 1];
		columns[0] = FROM_1;
		for (int i = 0; i < compareFiles.size(); i++) {
			columns[1 + i] = DIFF + i;
		}
		return columns;
	}

	private void readCompareSkims(String file, int count) {
		try (CSVReader reader = new CSVReader(file, CSVWriter.DEFAULT_SEPARATOR)) {
			Map<String, String> line = reader.readLine();
			System.out.println("Reading file " + file);
			int i = 0;
			while (line != null) {
				int from = (int) Double.parseDouble(line.get("FROM"));
				int to = (int) Double.parseDouble(line.get("TO"));
				double value = Double.parseDouble(line.get("VALUE"));
				SkimsValue skimsvalue = skimsCache.get(from).get(to);
				double diff = value - skimsvalue.origValue;
				if (Double.isInfinite(value) && Double.isInfinite(skimsvalue.origValue)) {
					diff = 0;
					//mathematically incorrect, but for the purpose of this analysis appropriate
				}
				skimsvalue.compareValues[count] = value;
				skimsvalue.diffs[count] = diff;
				if (i % 1000000 == 0) {
					System.out.println("Line " + i);
				}
				i++;
				line = reader.readLine();
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void readReference() {
		try (CSVReader reader = new CSVReader(inputfile1, CSVWriter.DEFAULT_SEPARATOR)) {
			Map<String, String> line = reader.readLine();
			int i = 0;
			while (line != null) {
				int from = (int) Double.parseDouble(line.get("FROM"));
				int to = (int) Double.parseDouble(line.get("TO"));
				double value = Double.parseDouble(line.get("VALUE"));
				Map<Integer, SkimsValue> tovalues = skimsCache.computeIfAbsent(from, n -> new HashMap<>());
				tovalues.put(to, new SkimsValue(value, new double[compareFiles.size()], new double[compareFiles.size()]));
				line = reader.readLine();

				if (i % 1000000 == 0) {
					System.out.println("Line " + i);
				}
				i++;
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static class SkimsValue {

		final double origValue;
		final double[] compareValues;
		final double[] diffs;

		public SkimsValue(double origValue, double[] compareValues, double[] diffs) {
			this.origValue = origValue;
			this.compareValues = compareValues;
			this.diffs = diffs;
		}
	}
}
