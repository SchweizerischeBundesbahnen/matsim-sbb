package ch.sbb.matsim.analysis.LinkAnalyser.ScreenLines;

import ch.sbb.matsim.csv.CSVWriter;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.index.SpatialIndex;
import com.vividsolutions.jts.index.quadtree.Quadtree;
import org.apache.log4j.Logger;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;


public class ScreenLinesAnalyser {
    private final static Logger log = Logger.getLogger(ScreenLinesAnalyser.class);

    Network network;

    public ArrayList<ScreenLine> getScreenlines() {
        return screenlines;
    }

    ArrayList<ScreenLine> screenlines;
    private final SpatialIndex quadTree = new Quadtree();
    private ArrayList<String> attributes;

    public ScreenLinesAnalyser(Scenario scenario, String shapefile) {
        this.network = scenario.getNetwork();
        this.screenlines = this.readShapeFile(shapefile);
    }

    private void buildQuadTree() {

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


    public void write(String folder, Map<Id, Integer> volumes, Integer scale) {
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
            Integer i = 0;
            for (ScreenLine screenLine : this.screenlines) {
                i+=1;
                for (Link link : screenLine.getLinks()) {

                    Integer volume = 0;
                    if(volumes.containsKey(link.getId())){
                        volume = volumes.get(link.getId())*scale;
                    }

                    writer.set("MATSIMID", link.getId().toString());
                    writer.set("FROM_X", Double.toString(link.getFromNode().getCoord().getX()));
                    writer.set("FROM_Y", Double.toString(link.getFromNode().getCoord().getY()));
                    writer.set("TO_X", Double.toString(link.getToNode().getCoord().getX()));
                    writer.set("TO_Y", Double.toString(link.getToNode().getCoord().getY()));
                    writer.set("VOLUME", volume.toString());
                    writer.set("SCREENLINE", i.toString());
                    writer.set("MODES", link.getAllowedModes().toString());
                    writer.writeRow();
                }
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

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
}
