package ch.sbb.matsim.analysis.linkAnalysis.ScreenLines;

import ch.sbb.matsim.csv.CSVWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.io.UncheckedIOException;
import org.opengis.feature.simple.SimpleFeature;

public class ScreenLinesAnalyser {

	private final static Logger log = Logger.getLogger(ScreenLinesAnalyser.class);

	Network network;
	ArrayList<ScreenLine> screenlines;

	public ScreenLinesAnalyser(Scenario scenario, String shapefile) {
		this.network = scenario.getNetwork();
		this.screenlines = this.readShapeFile(shapefile);
	}

	public static void main(String[] args) {
		Config config = ConfigUtils.createConfig();
		String network = "D:\\tmp\\miv\\network.xml.gz";
		String events = "D:\\tmp\\miv\\CH.10pct.2015.output_events.xml.gz";
		String shapefile = "D:\\tmp\\miv\\screenlines\\screenlines.shp";

		config.network().setInputFile(network);
		Scenario scenario = ScenarioUtils.loadScenario(config);

		EventsManager eventsManager = new EventsManagerImpl();

		scenario.getNetwork();

		ScreenLinesAnalyser sla = new ScreenLinesAnalyser(scenario, shapefile);

		//new MatsimEventsReader(eventsManager).readFile(events);

	}

	public ArrayList<ScreenLine> getScreenlines() {
		return screenlines;
	}

	public ArrayList<ScreenLine> readShapeFile(String shapefile) {
		ArrayList<ScreenLine> screenlines = new ArrayList<>();

		ShapeFileReader shapeFileReader = new ShapeFileReader();
		shapeFileReader.readFileAndInitialize(shapefile);

		Collection<SimpleFeature> features = shapeFileReader.getFeatureSet();
		for (SimpleFeature feature : features) {
			Geometry geometry = (Geometry) feature.getDefaultGeometry();
			ScreenLine screenLine = new ScreenLine(geometry);
			screenLine.findLinks(this.network);
			screenlines.add(screenLine);
		}
		return screenlines;
	}

	public void write(String folder, Map<Id, Integer> volumes, double scale) {
		final String[] COLUMNS = new String[]{
				"MATSIMID",
				"FROM_X",
				"FROM_Y",
				"TO_X",
				"TO_Y",
				"VOLUME",
				"SCREENLINE", "MODES"
		};
		try (CSVWriter writer = new CSVWriter("", COLUMNS, folder + "/screenlines.csv")) {
			int i = 0;
			for (ScreenLine screenLine : this.screenlines) {
				i += 1;
				for (Link link : screenLine.getLinks()) {

					double volume = volumes.getOrDefault(link.getId(), 0) * scale;

					writer.set("MATSIMID", link.getId().toString());
					writer.set("FROM_X", Double.toString(link.getFromNode().getCoord().getX()));
					writer.set("FROM_Y", Double.toString(link.getFromNode().getCoord().getY()));
					writer.set("TO_X", Double.toString(link.getToNode().getCoord().getX()));
					writer.set("TO_Y", Double.toString(link.getToNode().getCoord().getY()));
					writer.set("VOLUME", Double.toString(volume));
					writer.set("SCREENLINE", Integer.toString(i));
					writer.set("MODES", link.getAllowedModes().toString());
					writer.writeRow();
				}
			}

		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

	}
}
