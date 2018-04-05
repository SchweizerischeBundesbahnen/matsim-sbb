package ch.sbb.matsim.analysis.LinkAnalyser.VisumNetwork;

import ch.sbb.matsim.analysis.LinkAnalyser.LinkAnalyser;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.events.algorithms.EventWriter;

import java.util.Map;

public class VisumNetworkEventWriter extends LinkAnalyser implements EventWriter {

    double scale;
    int threshold;
    String mode;
    String folder;

    public VisumNetworkEventWriter(Scenario scenario, double scale, int threshold, String mode, String folder) {
        super(scenario);
        this.scale = scale;
        this.threshold = threshold;
        this.mode = mode;
        this.folder = folder;
    }

    private final static Logger log = Logger.getLogger(VisumNetworkEventWriter.class);

    @Override
    public void closeFile(){
        this.writeVisumNetwork(this.scale, this.threshold, this.mode, this.folder);
    }


    private void writeVisumNetwork(double scale, int threshold, String mode, String folder) {

        VisumNetwork visumNetwork = new VisumNetwork();

        for (Map.Entry<Id, Integer> entry : this.linkVolumes.entrySet()) {

            final Link link = this.scenario.getNetwork().getLinks().get(entry.getKey());
            final double volume = entry.getValue() * scale;
            try {
                if (link.getAllowedModes().contains(mode) && volume > threshold) {
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

    // Methods
    @Override
    public void reset(int iteration) {
    }
}
