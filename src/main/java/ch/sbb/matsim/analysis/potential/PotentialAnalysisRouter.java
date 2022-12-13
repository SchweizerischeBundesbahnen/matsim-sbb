package ch.sbb.matsim.analysis.potential;

import ch.sbb.matsim.config.variables.SBBModes;
import java.util.List;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.DefaultRoutingRequest;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.NetworkRoutingModule;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.facilities.Facility;

public class PotentialAnalysisRouter {

    private static final Logger log = LogManager.getLogger(PotentialAnalysisRouter.class);
    private final double startTime;
    private final Network network;
    private final NetworkRoutingModule router;
    private final Person person;

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
				SBBModes.CAR,
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
				SBBModes.CAR,
				PopulationUtils.getFactory(),
				network,
				routeAlgo);
	}

	public Leg fetch(Facility fromFacility, Facility toFacility) {

        List<? extends PlanElement> pes = this.router.calcRoute(DefaultRoutingRequest.withoutAttributes(fromFacility, toFacility, this.startTime, this.person));

        return (Leg) pes.get(0);
    }
}
