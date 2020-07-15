package ch.sbb.matsim.analysis.LinkAnalyser;

import com.google.common.base.Functions;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.Vehicle;

public class LinkAnalyser implements LinkEnterEventHandler, PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler, TransitDriverStartsEventHandler {

	private final static Logger log = Logger.getLogger(LinkAnalyser.class);

	protected Scenario scenario;
	protected LinkedHashMap<Id, Integer> linkVolumes;
	protected HashMap<Id<Vehicle>, Integer> passengers;
	HashSet<Id> transitDrivers;

	public LinkAnalyser(Scenario scenario) {
		this.scenario = scenario;
		this.passengers = new HashMap<>();
		this.transitDrivers = new HashSet<>();
		this.linkVolumes = new LinkedHashMap<>();
		this.linkVolumes.putAll(scenario.getNetwork().getLinks().keySet()
				.stream().collect(Collectors.toMap(Functions.identity(), i -> 0)));
	}

	public static void main(String[] args) {
		Config config = ConfigUtils.createConfig();

		String events = "D:\\tmp\\miv\\9.16\\CH.10pct.2015.output_events.xml.gz";
		String network = "D:\\tmp\\miv\\9.16\\CH.10pct.2015.output_network.xml.gz";

		config.network().setInputFile(network);
		Scenario scenario = ScenarioUtils.loadScenario(config);

		EventsManager eventsManager = new EventsManagerImpl();

		LinkAnalyser vv = new LinkAnalyser(scenario);
		eventsManager.addHandler(vv);

		new MatsimEventsReader(eventsManager).readFile(events);
	}

	@Override
	public void handleEvent(TransitDriverStartsEvent event) {
		transitDrivers.add(event.getDriverId());
	}

	@Override
	public void handleEvent(PersonLeavesVehicleEvent event) {
		if (!transitDrivers.contains(event.getPersonId())) {
			Id<Vehicle> vehId = event.getVehicleId();
			Integer passengers = this.passengers.get(vehId);
			this.passengers.put(vehId, passengers == null ? 0 : passengers - 1);
		}

	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		if (!transitDrivers.contains(event.getPersonId())) {
			Id<Vehicle> vehId = event.getVehicleId();
			Integer passengers = this.passengers.get(vehId);

			this.passengers.put(vehId, passengers == null ? 1 : passengers + 1);
		}
	}

	@Override
	public void handleEvent(LinkEnterEvent event) {
		Id<Link> linkid = event.getLinkId();
		Integer passengers = this.passengers.get(event.getVehicleId());
		if (passengers == null) {
			passengers = 0;
		}
		Integer vol = linkVolumes.get(linkid);
		linkVolumes.put(linkid, vol + passengers);
	}

	// Methods
	@Override
	public void reset(int iteration) {
		this.linkVolumes.clear();
		this.passengers.clear();
		this.transitDrivers.clear();
	}
}
