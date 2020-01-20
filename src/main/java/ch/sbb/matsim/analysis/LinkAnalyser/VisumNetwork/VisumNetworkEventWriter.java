package ch.sbb.matsim.analysis.LinkAnalyser.VisumNetwork;

import ch.sbb.matsim.analysis.EventsAnalysis;
import ch.sbb.matsim.analysis.LinkAnalyser.LinkAnalyser;
import ch.sbb.matsim.csv.CSVWriter;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.io.UncheckedIOException;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class VisumNetworkEventWriter extends LinkAnalyser implements EventsAnalysis {

    private static final String FILENAME_VISUMVOLUMES = "visum_volumes.csv.gz";
    private static final String FILENAME_FINALDAILYLINKVOLUMES =  "visum_volumes_daily.csv.gz";
    private static final String COL_ITERATION = "it";
    private final double scale;
    private final String mode;
    private String filename;
    private boolean writeFinalDailyVolumes;
    private CSVWriter linkVolumesPerIterationWriter = null;

    public VisumNetworkEventWriter(Scenario scenario, double scale, String mode, String filename, boolean writeFinalDailyVolumes) {
        super(scenario);
        this.scale = scale;
        this.mode = mode;
        this.filename = filename;
        this.writeFinalDailyVolumes = writeFinalDailyVolumes;
        if (writeFinalDailyVolumes) {
            try {
                List<String> columns = this.linkVolumes.keySet().stream().map(Id::toString)
                        .sorted(String::compareTo).collect(Collectors.toList());
                columns.add(0, COL_ITERATION);
                this.linkVolumesPerIterationWriter = new CSVWriter("", columns.toArray(new String[0]), this.filename + FILENAME_FINALDAILYLINKVOLUMES);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static final Logger log = Logger.getLogger(VisumNetworkEventWriter.class);

    @Override
    public void writeResults(boolean lastIteration) {
        if (this.writeFinalDailyVolumes) {
            try {
                this.linkVolumesPerIterationWriter.close();
            } catch (IOException e) {
                log.error("Could not close volumes per iteration file.", e);
            }
        } else {
            writeVolumes(this.scale, this.mode);
            if (lastIteration) {
                EventsAnalysis.copyToOutputFolder(this.filename, FILENAME_VISUMVOLUMES);
            }
        }
    }

    private void writeVolumes(double scale, String mode) {

        VisumNetwork visumNetwork = new VisumNetwork();
        Map<Link, Double> volumes = new LinkedHashMap<>();

        for (Map.Entry<Id, Integer> entry : this.linkVolumes.entrySet()) {
            final Link link = this.scenario.getNetwork().getLinks().get(entry.getKey());
            final double volume = entry.getValue() * scale;
            if (link.getAllowedModes().contains(mode) && volume > 0) {
                volumes.put(link, volume);
            }
        }
        visumNetwork.writeLinkVolumesCSV(this.filename + FILENAME_VISUMVOLUMES, volumes);
    }

    @Override
    public void reset(int iteration) {
        if (this.linkVolumesPerIterationWriter != null) {
            this.linkVolumesPerIterationWriter.set(COL_ITERATION, String.valueOf(iteration));
            for (Map.Entry<Id, Integer> e : this.linkVolumes.entrySet()) {
                this.linkVolumesPerIterationWriter.set(e.getKey().toString(), String.valueOf(e.getValue()));
            }
            this.linkVolumesPerIterationWriter.writeRow();
            this.linkVolumes.replaceAll((k, v) -> 0);
        }
    }
}
