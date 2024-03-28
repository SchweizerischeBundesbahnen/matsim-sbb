/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.mavi.pt;

import ch.sbb.matsim.config.variables.Filenames;
import ch.sbb.matsim.mavi.MaviHelper;
import ch.sbb.matsim.mavi.streets.StreetNetworkExporter;
import ch.sbb.matsim.mavi.visum.Visum;
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

	public static void main(String[] args) throws IOException {
		new RunVisumPtExporter().run(args[0]);
	}


	private static void createOutputPath(URL path) {
		File outputPath = new File(path.getPath());
		outputPath.mkdirs();

	}

	private static void writeFiles(Scenario scenario, String outputPath) {
		new NetworkWriter(scenario.getNetwork()).write(new File(outputPath, Filenames.PT_NETWORK_POLYLINES).getPath());
		StreetNetworkExporter.removePolylines(scenario.getNetwork());
		new NetworkWriter(scenario.getNetwork()).write(new File(outputPath, Filenames.PT_NETWORK).getPath());
		new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(new File(outputPath, TRANSITSCHEDULE_OUT).getPath());
		new MatsimVehicleWriter(scenario.getVehicles()).writeFile(new File(outputPath, TRANSITVEHICLES_OUT).getPath());
	}

	public void run(String configFile) throws IOException {
		Config config = ConfigUtils.loadConfig(configFile, new VisumPtExporterConfigGroup());
		VisumPtExporterConfigGroup exporterConfig = ConfigUtils.addOrGetModule(config, VisumPtExporterConfigGroup.class);

		// Start Visum and load version
		Visum visum = new Visum(exporterConfig.getVisumVersion());
		String visumFile = exporterConfig.getPathToVisumURL(config.getContext()).getPath()
				.replace("////", "//")
				.replace("/", "\\");
		if (visumFile.contains(":")) {
			visumFile = visumFile.substring(1);
		}
		visum.loadVersion(visumFile);

		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

		// load stops into scenario
		VisumStopExporter stops = new VisumStopExporter(scenario);
		stops.loadStopPoints(visum, exporterConfig);

		// load minimal transfer times into scenario
		new MTTExporter(scenario).integrateMinTransferTimes(visum, stops.getStopAreasToStopPoints());

		// load transit lines
		TimeProfileExporter tpe = new TimeProfileExporter(scenario);
		tpe.createTransitLines(visum, exporterConfig);
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
		writeFiles(scenario, exporterConfig.getOutputPathURL(config.getContext()).getPath());
	}


}