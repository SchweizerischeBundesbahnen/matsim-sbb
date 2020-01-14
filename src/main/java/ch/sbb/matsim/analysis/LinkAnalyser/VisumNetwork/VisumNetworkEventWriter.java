package ch.sbb.matsim.analysis.LinkAnalyser.VisumNetwork;

import ch.sbb.matsim.analysis.EventsAnalysis;
import ch.sbb.matsim.analysis.LinkAnalyser.LinkAnalyser;
import ch.sbb.matsim.csv.CSVWriter;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class VisumNetworkEventWriter extends LinkAnalyser implements EventsAnalysis {

    private static final String FILENAME_VISUMVOLUMES = "visum_volumes.csv.gz";
    private static final String FILENAME_FINALDAILYLINKVOLUMES =  "visum_volumes_daily.csv.gz";
    private final double scale;
    private final String mode;
    private String filename;
    private boolean writeFinalDailyVolumes = false;
    private Map<Id<Link>, Map<Integer, Double>> volumes = new HashMap<>();
    private int currentIteration = 0;

    public VisumNetworkEventWriter(Scenario scenario, double scale, String mode, String filename, boolean writeFinalDailyVolumes) {
        super(scenario);
        this.scale = scale;
        this.mode = mode;
        this.filename = filename;
        this.writeFinalDailyVolumes = writeFinalDailyVolumes;
    }

    private static final Logger log = Logger.getLogger(VisumNetworkEventWriter.class);

    @Override
    public void writeResults(boolean lastIteration) {
        reset(this.currentIteration); // make sure volumes are updated before writing
        if (this.writeFinalDailyVolumes) {
            this.writeFinalDailyVolumesToCSV(this.filename);
        } else {
            new VisumNetwork().writeLinkVolumesCSV(this.filename + FILENAME_VISUMVOLUMES, this.volumes, this.currentIteration);
            if (lastIteration) {
                EventsAnalysis.copyToOutputFolder(this.filename, FILENAME_VISUMVOLUMES);
            }
        }
    }

    // Methods
    @Override
    public void reset(int iteration) {
        for (Map.Entry<Id, Integer> entry : this.linkVolumes.entrySet()) {

            final Link link = this.scenario.getNetwork().getLinks().get(entry.getKey());

            final double volume = entry.getValue() * scale;
            if (link.getAllowedModes().contains(mode) && volume > 0) {
                this.volumes.putIfAbsent(link.getId(), new HashMap<>());
                this.volumes.get(link.getId()).put(this.currentIteration, volume);
            }
        }
        super.reset(iteration);
        this.currentIteration = iteration;
    }

    private void writeFinalDailyVolumesToCSV(String filename) {
        List<String> columns = Stream.iterate("0", n -> String.valueOf(Integer.valueOf(n) + 1))
                .limit(this.currentIteration + 1).collect(Collectors.toList());
        columns.add(0, "linkId");
        try {
            CSVWriter writer = new CSVWriter("", columns.toArray(new String[0]), filename + FILENAME_FINALDAILYLINKVOLUMES);
            for (Map.Entry<Id<Link>, Map<Integer, Double>> linkVolumes : this.volumes.entrySet()) {
                writer.set("linkId", linkVolumes.getKey().toString());
                for (int i = 0; i<linkVolumes.getValue().size(); i++) {
                    writer.set(String.valueOf(i), String.valueOf(linkVolumes.getValue().get(i)));
                }
                writer.writeRow();
            }
            writer.close();
        } catch (IOException e) {
            log.warn(e);
        }
    }

}
