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
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.Vehicle;

import java.util.*;


public class LinkAnalyser implements LinkEnterEventHandler, PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler, TransitDriverStartsEventHandler {
    private final static Logger log = Logger.getLogger(LinkAnalyser.class);


    Scenario scenario;
    HashMap<Id, Integer> linkVolumes;
    HashMap<Id<Vehicle>, Integer> passengers;
    HashSet<Id> transitDrivers;

    public LinkAnalyser(Scenario scenario) {
        this.scenario = scenario;
        this.linkVolumes = new HashMap<>();
        this.passengers = new HashMap<>();
        this.transitDrivers = new HashSet<>();

    }

    private void writeVisumNetwork(Integer scale, Integer limit, String mode, String folder) {

        VisumNetwork visumNetwork = new VisumNetwork();

        for (Map.Entry<Id, Integer> entry : this.linkVolumes.entrySet()) {

            final Link link = this.scenario.getNetwork().getLinks().get(entry.getKey());
            final Integer volume = entry.getValue() * scale;
            try {
                if (link.getAllowedModes().contains(mode) && volume > limit) {
                    VisumLink visumLink = visumNetwork.getOrCreateLink(link);
                    visumLink.setVolume(volume);
                }
            } catch (NullPointerException e) {
                log.info(e);
                log.info(link);
            }
        }

        visumNetwork.write(folder);
    }

    public void writeScreenLines(String shapefile, String folder, Integer scale) {
        ScreenLinesAnalyser sla = new ScreenLinesAnalyser(this.scenario, shapefile);
        sla.write(folder, this.linkVolumes, scale);
    }

    @Override
    public void handleEvent(TransitDriverStartsEvent event) {
        try {
            transitDrivers.add(event.getDriverId());
        } catch (Exception e) {
            log.error("Exception while handling event " + event.toString(), e);
        }
    }

    @Override
    public void handleEvent(PersonLeavesVehicleEvent event) {
        if (!transitDrivers.contains(event.getPersonId())) {
            Id vehId = event.getVehicleId();
            if (!this.passengers.containsKey(vehId)) {
                this.passengers.put(vehId, 0);
            }
            Integer passengers = this.passengers.get(vehId);
            this.passengers.put(vehId, passengers - 1);
        }
    }

    @Override
    public void handleEvent(PersonEntersVehicleEvent event) {
        if (!transitDrivers.contains(event.getPersonId())) {
            Id vehId = event.getVehicleId();
            if (!this.passengers.containsKey(vehId)) {
                this.passengers.put(vehId, 0);
            }
            Integer passengers = this.passengers.get(vehId);
            this.passengers.put(vehId, passengers + 1);
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
        //vv.writeScreenLines("D:\\tmp\\miv\\screenlines\\screenlines.shp", "D:\\tmp\\miv", scale);
        vv.writeVisumNetwork(scale, 500, TransportMode.car, "D:\\tmp\\miv");
    }
}
