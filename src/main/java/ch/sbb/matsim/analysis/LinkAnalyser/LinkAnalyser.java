package ch.sbb.matsim.analysis.LinkAnalyser;

import ch.sbb.matsim.analysis.LinkAnalyser.ScreenLines.ScreenLinesAnalyser;
import ch.sbb.matsim.analysis.LinkAnalyser.VisumNetwork.VisumLink;
import ch.sbb.matsim.analysis.LinkAnalyser.VisumNetwork.VisumNetwork;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
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
import org.matsim.core.events.algorithms.EventWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.Vehicle;

import java.io.IOException;
import java.util.*;


public class LinkAnalyser implements LinkEnterEventHandler, PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler, TransitDriverStartsEventHandler {
    private final static Logger log = Logger.getLogger(LinkAnalyser.class);


    protected Scenario scenario;
    protected HashMap<Id, Integer> linkVolumes;
    protected HashMap<Id<Vehicle>, Integer> passengers;
    HashSet<Id> transitDrivers;

    public LinkAnalyser(Scenario scenario) {
        this.scenario = scenario;
        this.linkVolumes = new HashMap<>();
        this.passengers = new HashMap<>();
        this.transitDrivers = new HashSet<>();

    }


    @Override
    public void handleEvent(TransitDriverStartsEvent event) {
        transitDrivers.add(event.getDriverId());
    }

    @Override
    public void handleEvent(PersonLeavesVehicleEvent event) {
        if (!transitDrivers.contains(event.getPersonId())) {
            Id vehId = event.getVehicleId();
            Integer passengers = this.passengers.get(vehId);
            this.passengers.put(vehId, passengers == null ? 0 : passengers - 1);
        }

    }

    @Override
    public void handleEvent(PersonEntersVehicleEvent event) {
        if (!transitDrivers.contains(event.getPersonId())) {
            Id vehId = event.getVehicleId();
            Integer passengers = this.passengers.get(vehId);

            this.passengers.put(vehId, passengers == null ? 1 : passengers + 1);
        }
    }

    @Override
    public void handleEvent(LinkEnterEvent event) {
        Id linkid = event.getLinkId();
        Integer passengers = this.passengers.get(event.getVehicleId());
        if (passengers == null) {
            passengers = 0;
        }
        if (!linkVolumes.containsKey(linkid)) {
            linkVolumes.put(linkid, passengers);
        } else {
            Integer vol = linkVolumes.get(linkid);
            linkVolumes.put(linkid, vol + passengers);
        }
    }


    // Methods
    @Override
    public void reset(int iteration) {
        this.linkVolumes.clear();
        this.passengers.clear();
        this.transitDrivers.clear();
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

        Integer scale = 10;
    }
}
