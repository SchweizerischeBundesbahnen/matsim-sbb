package ch.sbb.matsim.analysis.potential;

import ch.sbb.matsim.analysis.traveltimes.RunTravelTimeValidation;
import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.csv.CSVWriter;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.*;
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
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.core.utils.geometry.transformations.WGS84toCH1903LV03;
import org.matsim.facilities.Facility;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class PotentialAnalysisRouter {


    private static final Logger log = Logger.getLogger(PotentialAnalysisRouter.class);

    private Network network;
    private final double startTime;
    private NetworkRoutingModule router;
    private Person person;

    //use this method for free flow travel time evaluation
    public PotentialAnalysisRouter(Network network, double startTime) {
        this.person = PopulationUtils.getFactory().createPerson(Id.create(1, Person.class));
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
    public PotentialAnalysisRouter(Network network, String configPath, String eventsFilename, double startTime) {
        this.person = PopulationUtils.getFactory().createPerson(Id.create(1, Person.class));
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


    public Leg fetch(Facility fromFacility, Facility toFacility) {


        List<? extends PlanElement> pes = this.router.calcRoute(fromFacility, toFacility, this.startTime, this.person);
        Leg leg = (Leg) pes.get(0);


        return leg;
    }
}
