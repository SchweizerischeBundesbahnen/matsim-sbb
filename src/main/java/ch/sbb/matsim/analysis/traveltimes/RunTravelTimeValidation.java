package ch.sbb.matsim.analysis.traveltimes;

import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.csv.CSVWriter;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.LinkWrapperFacility;
import org.matsim.core.router.NetworkRoutingModule;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.core.utils.geometry.transformations.WGS84toCH1903LV03Plus;
import org.matsim.facilities.Facility;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class RunTravelTimeValidation {

    private static final Logger log = Logger.getLogger(RunTravelTimeValidation.class);

    private Network network;
    private final double startTime;
    private NetworkRoutingModule router;

    public static void main(String[] args) {
        String runOutputFolder = args[0];
        String runPrefix = args[1];
        boolean calcCongestedTravelTimes = Boolean.parseBoolean(args[2]);
        double startTime = Double.parseDouble(args[3]); // start time in hours (e.g. 7am)
        String relationFile = args[4];
        String outputFile = args[5];

        // read network file
        Network network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(runOutputFolder + "\\" + runPrefix + ".output_network.xml.gz");
        Network reducedNetwork = NetworkUtils.createNetwork();
        new TransportModeNetworkFilter(network).filter(reducedNetwork,
                CollectionUtils.stringToSet( TransportMode.car ));

        RunTravelTimeValidation validation;
        if(calcCongestedTravelTimes) {
            String config = runOutputFolder + "\\" + runPrefix + ".output_config.xml";
            String events = runOutputFolder + "\\" + runPrefix + ".output_events.xml.gz";
            validation = new RunTravelTimeValidation(reducedNetwork, config, events, startTime);
        }
        else {
            validation = new RunTravelTimeValidation(reducedNetwork, startTime);
        }
        validation.run(relationFile, outputFile);
    }

    //use this method for free flow travel time evaluation
    public RunTravelTimeValidation(Network network, double startTime) {
        this.network = network;
        this.startTime = startTime;

        DijkstraFactory factory = new DijkstraFactory();
        TravelTime tt = new FreeSpeedTravelTime();
        TravelDisutility td = new OnlyTimeDependentTravelDisutility(tt);

        LeastCostPathCalculator routeAlgo = factory.createPathCalculator(network, td, tt);
        this.router = new NetworkRoutingModule(
                TransportMode.car,
                PopulationUtils.getFactory(),
                network,
                routeAlgo);
    }

    //use this method for travel time evaluation in congested network
    public RunTravelTimeValidation(Network network, String configPath, String eventsFilename, double startTime) {
        this.network = network;
        this.startTime = startTime;

        Config config = ConfigUtils.loadConfig(configPath);

        DijkstraFactory factory = new DijkstraFactory();

        TravelTimeCalculator.Builder builder = new TravelTimeCalculator.Builder(network);
        builder.configure(config.travelTimeCalculator());
        TravelTimeCalculator ttc = builder.build();

        EventsManager events = EventsUtils.createEventsManager();
        events.addHandler(ttc);
        new MatsimEventsReader(events).readFile(eventsFilename);
        TravelTime tt = ttc.getLinkTravelTimes();

        TravelDisutility td = new OnlyTimeDependentTravelDisutility(tt);

        LeastCostPathCalculator routeAlgo = factory.createPathCalculator(network, td, tt);
        this.router = new NetworkRoutingModule(
                TransportMode.car,
                PopulationUtils.getFactory(),
                network,
                routeAlgo);
    }

    public void run(String csvPath, String outputPath) {
        String[] columns = {"Dist_MATSim", "Time_MATSim"};

        try (CSVReader reader = new CSVReader(csvPath, ";")) {
            String[] allColumns = Stream.concat(Arrays.stream(reader.getColumns()), Arrays.stream(columns)).toArray(String[]::new);

            try (CSVWriter writer = new CSVWriter("", allColumns, new File(outputPath).toString())) {
                Map<String, String> map;
                while ((map = reader.readLine()) != null) {
                    if (!map.get("Origin_X").isEmpty()) {
                        log.info(map);
                        Leg leg = this.fetch(
                                Float.parseFloat(map.get("Origin_Y")),
                                Float.parseFloat(map.get("Origin_X")),
                                Float.parseFloat(map.get("Destination_Y")),
                                Float.parseFloat(map.get("Destination_X"))
                        );
                        for (String column : reader.getColumns()) {
                            writer.set(column, map.get(column));
                        }

                        writer.set("Dist_MATSim", Double.toString(leg.getRoute().getDistance()));
                        writer.set("Time_MATSim", Double.toString(leg.getRoute().getTravelTime()));
                        writer.writeRow();
                    }
                }
            } catch (IOException e) {
                log.warn(e);
            }
        } catch (IOException e) {
            log.warn(e);
        }
    }

    private Coord transformCoord(Coord coord) {
        return new WGS84toCH1903LV03Plus().transform(coord);
    }

    public Leg fetch(float fromX, float fromY, float toX, float toY) {

        Activity fromAct = PopulationUtils.createActivityFromCoord("h", this.transformCoord(new Coord(fromX, fromY)));
        Facility fromFacility = new LinkWrapperFacility(NetworkUtils.getNearestLink(this.network, fromAct.getCoord()));

        Activity toAct = PopulationUtils.createActivityFromCoord("h", this.transformCoord(new Coord(toX, toY)));
        Facility toFacility = new LinkWrapperFacility(NetworkUtils.getNearestLink(this.network, toAct.getCoord()));

        List<? extends PlanElement> pes = this.router.calcRoute(fromFacility, toFacility,
                this.startTime * 60 * 60, null);
        Leg leg = (Leg) pes.get(0);
        return leg;
    }
}
