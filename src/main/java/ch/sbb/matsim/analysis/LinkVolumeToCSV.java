/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis;

import ch.sbb.matsim.csv.CSVWriter;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.events.algorithms.EventWriter;

import java.io.IOException;
import java.util.Set;


public class LinkVolumeToCSV extends VolumesAnalyzerSBB implements EventWriter {

    private final static Logger log = Logger.getLogger(LinkVolumeToCSV.class);

    public static final String FILENAME_VOLUMES = "matsim_linkvolumes.csv";
    public static final String COL_LINK_ID = "link_id";
    public static final String COL_MODE = "mode";
    public static final String COL_BIN = "bin";
    public static final String COL_VOLUME = "volume";
    public static final String COL_NBPASSENGERS = "nb_passengers";
    public static final String[] COLUMNS = new String[]{COL_LINK_ID, COL_MODE, COL_BIN, COL_VOLUME, COL_NBPASSENGERS};
    private final String filename;
    private Network network;

    public LinkVolumeToCSV(Scenario scenario, String filename) {
        super(3600, 24 * 3600 - 1, scenario.getNetwork());
        this.filename = filename;
        this.network = scenario.getNetwork();
    }

    // Methods
    @Override
    public void reset(int iteration) {
        super.reset(iteration);
    }

    @Override
    public void closeFile() {
        this.write(this.filename);
    }

    public void write(String filename) {
        log.info("write linkvolumes to " + filename + FILENAME_VOLUMES);
        try (CSVWriter linkVolumesWriter = new CSVWriter("", COLUMNS, filename + FILENAME_VOLUMES)) {
            for (Id<Link> linkId : super.getLinkIds()) {
                for (String aMode: this.network.getLinks().get(linkId).getAllowedModes()) {
                    int[] volumes = super.getVolumesForLink(linkId);
                    int[] nbPassengers = super.getPassengerVolumesForLink(linkId);
                    if (volumes != null) {
                        for (int i = 0; i < volumes.length; i++) {
                            linkVolumesWriter.set(COL_LINK_ID, linkId.toString());
                            linkVolumesWriter.set(COL_MODE, aMode);
                            linkVolumesWriter.set(COL_BIN, Integer.toString(i + 1));
                            linkVolumesWriter.set(COL_VOLUME, Integer.toString(volumes[i]));
                            linkVolumesWriter.set(COL_NBPASSENGERS, Integer.toString(nbPassengers[i]));
                            linkVolumesWriter.writeRow();
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("Could not write linkvolumes. " + e.getMessage(), e);
        }
    }

}
