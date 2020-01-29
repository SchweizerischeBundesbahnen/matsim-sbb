package ch.sbb.matsim.analysis.potential;

import ch.sbb.matsim.analysis.skims.LeastCostPathTree;
import ch.sbb.matsim.preparation.FilteredNetwork;
import com.graphhopper.isochrone.algorithm.DelaunayTriangulationIsolineBuilder;
import org.apache.log4j.Logger;
import org.geotools.feature.SchemaException;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.locationtech.jts.geom.Coordinate;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.core.utils.gis.PolygonFeatureFactory;
import org.matsim.core.utils.gis.ShapeFileWriter;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.opengis.feature.simple.SimpleFeature;

import java.io.IOException;
import java.util.*;


public class Isochrones {

    private static final Logger log = Logger.getLogger(Isochrones.class);
    private Scenario scenario;
    private String eventsFilename;
    private Config config;
    private Network network;
    private Network filteredNetwork;
    private Collection<SimpleFeature> collection = new ArrayList<SimpleFeature>();
    private TravelTime travelTime;
    private TravelTime travelTimeWithLoad;
    private TravelDisutility travelDisutility;
    private PolygonFeatureFactory pff;


    public Isochrones(String configFile, String eventsFilename) {
        this.config = ConfigUtils.loadConfig(configFile);
        this.eventsFilename = eventsFilename;


        this.pff = new PolygonFeatureFactory.Builder()
                .setName("EvacuationArea")
                .setCrs(DefaultGeographicCRS.WGS84)
                .addAttribute("station", String.class)
                .addAttribute("threshold", double.class)
                .addAttribute("withLoad", int.class)
                .addAttribute("polyId", int.class)
                .create();
    }


    public void load() {

        this.scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        new MatsimNetworkReader(this.scenario.getNetwork()).readFile(this.config.network().getInputFile());
        new TransitScheduleReader(this.scenario).readFile(this.config.transit().getTransitScheduleFile());

        this.network = NetworkUtils.createNetwork();
        new TransportModeNetworkFilter(scenario.getNetwork()).filter(this.network, Collections.singleton(TransportMode.car));
        this.filteredNetwork = new FilteredNetwork().filterNetwork(this.network);

        this.travelTime = getTravelTime();
        if (this.eventsFilename != null) {
            this.travelTimeWithLoad = getTravelTime(this.eventsFilename);
        }

        this.travelDisutility = new OnlyTimeDependentTravelDisutility(this.travelTime);

    }


    private TravelTime getTravelTime() {
        log.info("No events specified. Travel Times will be calculated with free speed travel times.");
        return new FreeSpeedTravelTime();

    }


    private TravelTime getTravelTime(String eventsFilename) {
        log.info("extracting actual travel times from " + eventsFilename);
        TravelTimeCalculator.Builder builder = new TravelTimeCalculator.Builder(scenario.getNetwork());
        builder.configure(scenario.getConfig().travelTimeCalculator());
        TravelTimeCalculator ttc = builder.build();
        EventsManager events = EventsUtils.createEventsManager();
        events.addHandler(ttc);
        new MatsimEventsReader(events).readFile(eventsFilename);
        return ttc.getLinkTravelTimes();
    }


    public void computeall(int threshold) {
//        this.computeIsochrone(new Coord(600000, 200000), threshold, "BN", false);
//        this.computeIsochrone(new Coord(600000, 200000), 15 * 60, "BN", true);

        int i = 0;

        for (TransitStopFacility stop : this.scenario.getTransitSchedule().getFacilities().values()) {

            String herkunft = stop.getAttributes().getAttribute("01_Datenherkunft").toString();
            String name = String.valueOf(stop.getAttributes().getAttribute("03_Stop_Code"));


            if (herkunft.equals("SBB_Simba") && i < 20) {
                log.info(name);
                this.computeIsochrone(stop.getCoord(), threshold, name, false);
                if (this.eventsFilename != null) {
                    this.computeIsochrone(stop.getCoord(), threshold, name, true);

                }
                i++;
            }
        }

    }


    private void computeIsochrone(Coord coord, double threshold, String name, boolean withLoad) {
        TravelTime tt = this.travelTime;
        if (withLoad) {
            tt = this.travelTimeWithLoad;
        }

        Node node = NetworkUtils.getNearestNode(this.filteredNetwork, coord);
        LeastCostPathTree leastCostPathTree = new LeastCostPathTree(tt, this.travelDisutility);

        int startTime = 7 * 60 * 60;
        leastCostPathTree.calculate(this.scenario.getNetwork(), node, startTime);

        Map<Id<Node>, LeastCostPathTree.NodeData> tree = leastCostPathTree.getTree();

        final int bucketCount = 2;
        final double bucketSize = threshold / bucketCount;
        List<List<Coordinate>> buckets = this.createBuckets(bucketCount);

        for (Map.Entry<Id<Node>, LeastCostPathTree.NodeData> e : tree.entrySet()) {
            Id<Node> id = e.getKey();
            LeastCostPathTree.NodeData d = e.getValue();
            double time1 = d.getTime();
            double time2 = time1 - startTime;

            int bucketIndex = (int) (time2 / bucketSize);

            if (bucketIndex < bucketCount) {
                Coord nodeCoord = this.network.getNodes().get(id).getCoord();

                buckets.get(bucketIndex).add(new Coordinate(nodeCoord.getX(), nodeCoord.getY()));

            }
        }

        try {

            DelaunayTriangulationIsolineBuilder instance = new DelaunayTriangulationIsolineBuilder();
            //List<List<Coordinate>> buckets = isochrone.searchGPS(qr.getClosestNode(), 2);

            List<Coordinate[]> res = instance.calcList(buckets, buckets.size() - 1);
            int polygonIndex = 1;
            SimpleFeature lastF = null;
            for (Coordinate[] polygonShell : res) {
                SimpleFeature f = this.pff.createPolygon(polygonShell);
                f.setAttribute("station", name);
                f.setAttribute("threshold", threshold);
                f.setAttribute("withLoad", ((withLoad) ? 1 : 0));
                f.setAttribute("polyId", polygonIndex);
                polygonIndex++;
                collection.add(f);
                lastF = f;
            }

            //not really nice but do the trick
            if (lastF != null) {
                collection.remove(lastF);
            }

        } catch (Exception e) {
            log.error(e);
        }
    }


    private List<List<Coordinate>> createBuckets(int bucketCount) {
        List<List<Coordinate>> buckets = new ArrayList<>(bucketCount);
        for (int i = 0; i < bucketCount + 1; i++) {
            buckets.add(new ArrayList<Coordinate>());
        }


        return buckets;
    }

    private void write(String filename) throws SchemaException, IOException {


        ShapeFileWriter.writeGeometries(collection, filename);


    }


    public static void main(String[] args) throws IOException, SchemaException {
        System.setProperty("matsim.preferLocalDtds", "true");

        String config = args[0];
        String eventsFilename = args[1].equals("-") ? null : args[1];
        String outputShapefile = args[2];
        Isochrones isochrones = new Isochrones(config, eventsFilename);
        isochrones.load();
        isochrones.computeall(15 * 60);
        isochrones.computeall(10 * 60);
        isochrones.write(outputShapefile);


    }

}
