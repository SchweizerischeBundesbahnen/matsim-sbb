package ch.sbb.matsim.analysis.LinkAnalyser.VisumNetwork;

import ch.sbb.matsim.analysis.LinkAnalyser.LinkAnalyser;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.events.algorithms.EventWriter;

import java.util.HashMap;
import java.util.Map;

public class VisumNetworkEventWriter extends LinkAnalyser implements EventWriter {

    double scale;
    String mode;
    String folder;

    public VisumNetworkEventWriter(Scenario scenario, double scale, String mode, String folder) {
        super(scenario);
        this.scale = scale;
        this.mode = mode;
        this.folder = folder;
    }

    private final static Logger log = Logger.getLogger(VisumNetworkEventWriter.class);

    @Override
    public void closeFile() {
        this.writeVolumes(this.scale, this.mode, this.folder);
    }


    private void writeVolumes(double scale, String mode, String folder) {

        VisumNetwork visumNetwork = new VisumNetwork();
        Map<Link, Double> volumes = new HashMap<>();

        for (Map.Entry<Id, Integer> entry : this.linkVolumes.entrySet()) {

            final Link link = this.scenario.getNetwork().getLinks().get(entry.getKey());

            final double volume = entry.getValue() * scale;
            if (link.getAllowedModes().contains(mode) && volume > 0) {
                volumes.put(link, volume);
            }
        }

        visumNetwork.writeLinksAttributes(folder+"visum_volumes.att", volumes);
    }

    // Methods
    @Override
    public void reset(int iteration) {
    }
}
