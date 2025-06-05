/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.mavi.pt;

import ch.sbb.matsim.config.variables.Filenames;
import ch.sbb.matsim.mavi.MaviHelper;
import ch.sbb.matsim.mavi.streets.StreetNetworkExporter;
import ch.sbb.matsim.mavi.visum.Visum;
import ch.sbb.matsim.mavi.visum.VisumDataSource;
import ch.sbb.matsim.mavi.visum.VisumNetFile;
import ch.sbb.matsim.preparation.MobiTransitScheduleVerifiyer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.utils.TransitScheduleValidator;
import org.matsim.vehicles.MatsimVehicleWriter;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;

/***
 *
 * @author pmanser / SBB
 *
 * IMPORTANT:
 * Download the JACOB library version 1.18 and
 * set path to the library in the VM Options (e.g. -Djava.library.path="C:\Users\u225744\Downloads\jacob-1.18\jacob-1.18")
 *
 */

public class RunVisumPtExporter {

	private static final Logger log = LogManager.getLogger(RunVisumPtExporter.class);

	private static final String TRANSITSCHEDULE_OUT = "transitSchedule.xml.gz";
	private static final String TRANSITVEHICLES_OUT = "transitVehicles.xml.gz";
	private static final String ZONECONNECTIONS_OUT = "zoneConnections.csv";

	public static void main(String[] args) throws IOException {
		new RunVisumPtExporter().run(args[0]);
	}


	private static void createOutputPath(URL path) {
		File outputPath = new File(path.getPath());
		outputPath.mkdirs();

	}

	private static void writeFiles(Scenario scenario, VisumConnectionsExporter connectionsExporter, String outputPath) {
		new NetworkWriter(scenario.getNetwork()).write(new File(outputPath, Filenames.PT_NETWORK_POLYLINES).getPath());
		StreetNetworkExporter.removePolylines(scenario.getNetwork());
		new NetworkWriter(scenario.getNetwork()).write(new File(outputPath, Filenames.PT_NETWORK).getPath());
		new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(new File(outputPath, TRANSITSCHEDULE_OUT).getPath());
		new MatsimVehicleWriter(scenario.getVehicles()).writeFile(new File(outputPath, TRANSITVEHICLES_OUT).getPath());
		connectionsExporter.writeConnections(new File(outputPath, ZONECONNECTIONS_OUT).getPath());
	}

	/**
	 * Creates and returns the appropriate VisumDataSource based on the configuration.
	 * 
	 * @param exporterConfig The configuration for the Visum exporter
	 * @param filePath The path to the Visum file
	 * @return A VisumDataSource instance (either COM-based Visum or file-based VisumNetFile)
	 * @throws IOException If there's an error accessing the file
	 */
	private VisumDataSource createVisumDataSource(VisumPtExporterConfigGroup exporterConfig, String filePath) throws IOException {
		// Check if the file has a .net extension (indicating a file-based data source)
		if (filePath.toLowerCase().endsWith(".net")) {
			log.info("Using file-based Visum data source for {}", filePath);
			return VisumNetFile.open(Path.of(filePath));
		} else {
			// Use COM-based Visum by default or for .ver files
			log.info("Using COM-based Visum data source for {}", filePath);
			Visum visum = Visum.connect(exporterConfig.getVisumVersion());
			visum.load(filePath);
			return visum;
		}
	}

	public void run(String configFile) throws IOException {
		Config config = ConfigUtils.loadConfig(configFile, new VisumPtExporterConfigGroup());
		VisumPtExporterConfigGroup exporterConfig = ConfigUtils.addOrGetModule(config, VisumPtExporterConfigGroup.class);

		// Prepare Visum file path
		String visumFile = exporterConfig.getPathToVisumURL(config.getContext()).getPath()
				.replace("////", "//")
				.replace("/", "\\");
		if (visumFile.contains(":")) {
			visumFile = visumFile.substring(1);
		}
		
		// Create a scenario
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		
		// Create the appropriate VisumDataSource based on the configuration
		try (VisumDataSource dataSource = createVisumDataSource(exporterConfig, visumFile)) {
			// load stops into scenario
			VisumStopExporter stops = new VisumStopExporter(scenario);
			stops.loadStopPoints(dataSource, exporterConfig);

			// load minimal transfer times into scenario
			new MTTExporter(scenario).integrateMinTransferTimes(dataSource, stops.getStopAreasToStopPoints());

			// load transit lines
			TimeProfileExporter tpe = new TimeProfileExporter(scenario);
			tpe.createTransitLines(dataSource, exporterConfig);
			tpe.createDepartureChaining(dataSource);
			
			// load zone connections
			VisumConnectionsExporter connectionsExporter = new VisumConnectionsExporter(dataSource);
			
			// reduce the size of the network and the schedule by taking necessary things only.
			MaviHelper.cleanStops(scenario.getTransitSchedule());
			MaviHelper.removeUnusedLinksInPTNetwork(scenario.getTransitSchedule(), scenario.getNetwork());

			//verify schedule integrity
			var result = TransitScheduleValidator.validateAll(scenario.getTransitSchedule(), scenario.getNetwork());
			TransitScheduleValidator.printResult(result);
			if (!result.isValid()) {
				throw new RuntimeException("Transit Schedule integrity not confirmed");
			}
			MobiTransitScheduleVerifiyer.verifyTransitSchedule(scenario.getTransitSchedule());

			// write outputs
			createOutputPath(exporterConfig.getOutputPathURL(config.getContext()));
			writeFiles(scenario, connectionsExporter, exporterConfig.getOutputPathURL(config.getContext()).getPath());
		} catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}