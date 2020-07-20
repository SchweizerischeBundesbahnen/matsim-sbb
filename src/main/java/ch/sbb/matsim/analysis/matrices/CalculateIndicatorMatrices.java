/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis.matrices;

import ch.sbb.matsim.analysis.skims.CalculateSkimMatrices;
import ch.sbb.matsim.config.variables.SBBModes;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;

/**
 * @author mrieser / SBB
 */
public class CalculateIndicatorMatrices {

	public static void main(String[] args) throws IOException {
		System.setProperty("matsim.preferLocalDtds", "true");
		String coordinatesFilename = args[0];
		String networkFilename = args[1];
		String transitScheduleFilename = args[2];
		String eventsFilename = args[3].equals("-") ? null : args[3];
		String outputDirectory = args[4];
		int numberOfThreads = Integer.valueOf(args[5]);
		boolean detectTrainLines = Boolean.parseBoolean(args[6]);
		if (!args[6].equalsIgnoreCase("true") || !args[6].equalsIgnoreCase("false")){
			throw new RuntimeException("train filter must be argument must be either true or false");
		}
		BiPredicate<TransitLine, TransitRoute> trainLinePredictor = detectTrainLines ?
				(line, route) -> route.getTransportMode().equals(SBBModes.PTSubModes.RAIL) :
				(line, route) -> false;

		Map<String, double[]> timesCar = new LinkedHashMap<>();
		Map<String, double[]> timesPt = new LinkedHashMap<>();

		for (int argIdx = 7; argIdx < args.length; argIdx++) {
			String arg = args[argIdx];
			String mode = null;
			String data = null;
			if (arg.startsWith("car=")) {
				mode = "car";
				data = arg.substring(4);
			}
			if (arg.startsWith("pt=")) {
				mode = "pt";
				data = arg.substring(3);
			}
			if (data != null) {
				String[] parts = data.split(";");
				String prefix = parts[0];
				double[] times = new double[parts.length - 1];
				for (int timeIndex = 0; timeIndex < times.length; timeIndex++) {
					times[timeIndex] = Time.parseTime(parts[timeIndex + 1]);
				}
				if (mode.equals("car")) {
					timesCar.put(prefix, times);
				}
				if (mode.equals("pt")) {
					timesPt.put(prefix, times);
				}
			}
		}

		Config config = ConfigUtils.createConfig();

		CalculateSkimMatrices skims = new CalculateSkimMatrices(outputDirectory, numberOfThreads);
		skims.loadSamplingPointsFromFile(coordinatesFilename);

		for (Map.Entry<String, double[]> e : timesCar.entrySet()) {
			String prefix = e.getKey();
			double[] times = e.getValue();
			skims.calculateNetworkMatrices(networkFilename, eventsFilename, times, config, prefix, l -> l.getAttributes().getAttribute("accessControlled").toString().equals("0"));
		}

		for (Map.Entry<String, double[]> e : timesPt.entrySet()) {
			String prefix = e.getKey();
			double[] times = e.getValue();
			skims.calculatePTMatrices(networkFilename, transitScheduleFilename, times[0], times[1], config, prefix, trainLinePredictor);
		}

		skims.calculateBeelineMatrix();
	}

}