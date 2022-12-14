package ch.sbb.matsim.accessibility;

import ch.sbb.matsim.accessibility.Accessibility.Modes;
import ch.sbb.matsim.analysis.skims.StreamingFacilities;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import ch.sbb.matsim.zones.ZonesQueryCache;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.core.utils.misc.Time;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.MatsimFacilitiesReader;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;

public class CalculateAccessibility {

	private final static Logger log = LogManager.getLogger(CalculateAccessibility.class);

	private static Map<Coord, Double> calculateAttractions(int gridSize, String facilitiesFilename, String populationFilename, double personWeight) {
		Map<Coord, Double> attractions = new HashMap<>();
		log.info("loading facilities from " + facilitiesFilename);
		ToDoubleFunction<ActivityFacility> weightFunction = f -> {
			double weight = 0;
			String fte = (String) f.getAttributes().getAttribute("fte");
			if (fte != null) {
				weight = 2.54 * Double.parseDouble(fte);
			}
			return weight;
		};
		Counter facCounter = new Counter("#");

		StreamingFacilityFixer streamFixer = new StreamingFacilityFixer(
				f -> {
					facCounter.incCounter();
					double weight = weightFunction.applyAsDouble(f);
					if (weight > 0) {
						Coord c = AccessibilityUtils.getGridCoordinate(f.getCoord().getX(), f.getCoord().getY(), gridSize);
						attractions.compute(c, (k, oldVal) -> oldVal == null ? weight : (oldVal + weight));
					}
				}
		);
		new MatsimFacilitiesReader(null, null, new StreamingFacilities(streamFixer)).readFile(facilitiesFilename);
		streamFixer.finish();
		facCounter.printCounter();

		StreamingPopulationReader popReader = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
		popReader.addAlgorithm(p -> {
			Plan plan = p.getSelectedPlan();
			Activity firstAct = (Activity) plan.getPlanElements().get(0);
			if (firstAct.getType().startsWith("home")) {
				Coord c = AccessibilityUtils.getGridCoordinate(firstAct.getCoord().getX(), firstAct.getCoord().getY(), gridSize);
				attractions.compute(c, (k, oldVal) -> oldVal == null ? personWeight : (oldVal + personWeight));
			}
		});
		popReader.readFile(populationFilename);
		return attractions;
	}

	public static void writeAttractions(File file, Map<Coord, Double> attractions) throws IOException {
		try (CSVWriter csv = new CSVWriter(null, new String[]{"x", "y", "attraction"}, file.getAbsolutePath(), StandardCharsets.UTF_8)) {
			attractions.forEach((coord, value) -> {
				csv.set("x", Double.toString(coord.getX()));
				csv.set("y", Double.toString(coord.getY()));
				csv.set("attraction", Double.toString(value));
				csv.writeRow();
			});
		}
	}

	public static Map<Coord, Double> loadAttractions(File file) throws IOException {
		Map<Coord, Double> attractions = new HashMap<>();
		try (CSVReader csv = new CSVReader(new String[]{"x", "y", "attraction"}, file.getAbsolutePath(), ";")) {
			csv.readLine();
			Map<String, String> data; // header
			while ((data = csv.readLine()) != null) {
				attractions.put(new Coord(Double.parseDouble(data.get("x")), Double.parseDouble(data.get("y"))), Double.parseDouble(data.get("attraction")));
			}
		}
		return attractions;
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	public static void main(String[] args) throws IOException {
		System.setProperty("matsim.preferLocalDtds", "true");

		Thread memObs = new Thread(() -> {
			while (true) {
				Gbl.printMemoryUsage();
				try {
					Thread.sleep(30_000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}, "Memory Observer");
		memObs.setDaemon(true);
		memObs.start();

		String startCoordsFilename = args[0];
		String zonesFilename = args[1];
		String facilitiesFilename = args[2];
		String populationFilename = args[3];
		double personWeight = Double.parseDouble(args[4]);
		String networkFilename = args[5];
		String scheduleFilename = args[6];
		String transitNetworkFilename = args[7];
		String eventsFilename = args[8].isEmpty() || args[8].equals("-") ? null : args[8];
		String outputDirectory = args[9];
		int gridSize = Integer.parseInt(args[10]);
		int numThreads = Integer.parseInt(args[11]);
		String trainFilter = args[12];
		String[] modesStr = args[13].split(";"); // supported: mm (=multimodal), car, pt
		String[] timesPtStr = args[14].split(";");
		String[] timesCarAMStr = args[15].split(";");
		String[] timesCarPMStr = args[16].split(";");

		double[] carAMDepTimes = new double[timesCarAMStr.length];
		for (int i = 0; i < timesCarAMStr.length; i++) {
			carAMDepTimes[i] = Time.parseTime(timesCarAMStr[i]);
		}

		double[] carPMDepTimes = new double[timesCarPMStr.length];
		for (int i = 0; i < timesCarPMStr.length; i++) {
			carPMDepTimes[i] = Time.parseTime(timesCarPMStr[i]);
		}

		double ptMinDepTime = Time.parseTime(timesPtStr[0]);
		double ptMaxDepTime = Time.parseTime(timesPtStr[1]);

		File outputDir = new File(outputDirectory);
		if (!outputDir.exists()) {
			outputDir.mkdirs();
		}

		File csvOutputFile = new File(outputDirectory, "accessibility.csv");
		File attractionsFile = new File(outputDirectory, "attractions_" + gridSize + ".csv");

		List<Coord> coordinates = new ArrayList<>();
		try (BufferedReader reader = IOUtils.getBufferedReader(startCoordsFilename)) {
			String line = reader.readLine();
			if (!line.equals("X,Y")) {
				throw new RuntimeException("expected header 'X,Y' in " + startCoordsFilename);
			}
			while ((line = reader.readLine()) != null) {
				String[] xy = line.split(",");
				double x = Double.parseDouble(xy[0]);
				double y = Double.parseDouble(xy[1]);
				coordinates.add(new Coord(x, y));
			}
		}

		Set<String> modesSet = new HashSet<>();
		modesSet.addAll(Arrays.asList(modesStr));
		List<Accessibility.Modes> modesList = new ArrayList<>();
		if (modesSet.contains("mm")) {
			modesList.add(new Accessibility.Modes("mm", true, true, true, true));
		}
		if (modesSet.contains("car")) {
			modesList.add(new Accessibility.Modes("car", true, false, true, true));
		}
		if (modesSet.contains("pt")) {
			modesList.add(new Accessibility.Modes("pt", false, true, true, true));
		}
		Accessibility.Modes[] modes = modesList.toArray(new Modes[0]);

		Map<Coord, Double> attractions;
		if (attractionsFile.exists()) {
			log.info("loading attractions from " + attractionsFile.getAbsolutePath());
			attractions = loadAttractions(attractionsFile);
		} else {
			log.info("calculate attractions...");
			attractions = calculateAttractions(gridSize, facilitiesFilename, populationFilename, personWeight);
			log.info("write attractions to " + attractionsFile.getAbsolutePath());
			writeAttractions(attractionsFile, attractions);
		}

		BiPredicate<TransitLine, TransitRoute> trainDetector = (line, route) -> route.getTransportMode().equals(SBBModes.PTSubModes.RAIL);

		Zones zones = new ZonesQueryCache(ZonesLoader.loadZones("mobi", zonesFilename, "zone_id"));

		log.info("calculate accessibility...");
		Accessibility accessibility = new Accessibility(networkFilename, eventsFilename, scheduleFilename, transitNetworkFilename, attractions, carAMDepTimes, carPMDepTimes, ptMinDepTime,
				ptMaxDepTime, trainDetector, zones);
		accessibility.setThreadCount(numThreads);
		accessibility.calculateAccessibility(coordinates, modes, csvOutputFile);

		log.info("done.");
	}

	/** fixes an issue with streaming facilities. Should not be needed anymore with MATSim 12.0-2019w44 or newer, see https://github.com/matsim-org/matsim/pull/707 */
	private static class StreamingFacilityFixer implements Consumer<ActivityFacility> {

		private final Consumer<ActivityFacility> delegate;
		private ActivityFacility f;

		public StreamingFacilityFixer(Consumer<ActivityFacility> delegate) {
			this.delegate = delegate;
		}

		@Override
		public void accept(ActivityFacility activityFacility) {
			if (this.f != null) {
				this.delegate.accept(this.f);
			}
			this.f = activityFacility;
		}

		public void finish() {
			if (this.f != null) {
				this.delegate.accept(this.f);
			}
		}
	}
}
