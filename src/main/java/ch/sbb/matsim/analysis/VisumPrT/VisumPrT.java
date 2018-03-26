package ch.sbb.matsim.analysis.VisumPrT;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.*;


public class VisumPrT implements LinkEnterEventHandler {
    private final static Logger log = Logger.getLogger(VisumPrT.class);


    VisumNetwork visumNetwork;
    Scenario scenario;
    HashMap<Id, Integer> linkVolumes;

    public VisumPrT(Scenario scenario) {
        this.scenario = scenario;
        this.visumNetwork = new VisumNetwork();
        this.linkVolumes = new HashMap<>();

    }

    private void getVisumNetwork(Integer scale, Integer limit) {


        for (Map.Entry<Id, Integer> entry : this.linkVolumes.entrySet()) {

            final Link link = this.scenario.getNetwork().getLinks().get(entry.getKey());
            final Integer volume = entry.getValue()*scale;
            try {
                if (link.getAllowedModes().contains(TransportMode.car) && volume > limit) {
                    VisumLink visumLink = this.visumNetwork.getOrCreateLink(link);
                    visumLink.setVolume(volume);
                }
            }catch(NullPointerException e){
                log.info(e);
                log.info(link);
            }
        }


    }

    @Override
    public void handleEvent(LinkEnterEvent event) {
        Id linkid = event.getLinkId();
        if (!linkVolumes.containsKey(linkid)) {
            linkVolumes.put(linkid, 1);
        } else {
            Integer vol = linkVolumes.get(linkid);
            linkVolumes.put(linkid, vol + 1);
        }
    }

    public void write(String folder) {
        this.visumNetwork.writeNodes(folder+"nodes.net");
        this.visumNetwork.writeLinks(folder +"links.net");
        this.visumNetwork.writeUserDefinedAttributes(folder +"defined.net");
    }

    // Methods
    @Override
    public void reset(int iteration) {
    }

    public static void main(String[] args) {
        Config config = ConfigUtils.createConfig();

        config.network().setInputFile("D:\\tmp\\miv\\network.xml.gz");
        Scenario scenario = ScenarioUtils.loadScenario(config);

        String events = "D:\\tmp\\miv\\CH.10pct.2015.output_events.xml.gz";

        EventsManager eventsManager = new EventsManagerImpl();

        VisumPrT vv = new VisumPrT(scenario);
        eventsManager.addHandler(vv);

        new MatsimEventsReader(eventsManager).readFile(events);

        vv.getVisumNetwork(10, 10000);
        vv.write("D:\\tmp\\miv\\");
    }
}
