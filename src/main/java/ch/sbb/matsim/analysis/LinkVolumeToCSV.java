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

import java.util.HashMap;
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
    private String filename;

    Scenario scenario;
    private Network network;

    private final CSVWriter linkVolumesWriter = new CSVWriter(COLUMNS);


    public LinkVolumeToCSV(Scenario scenario, String filename){
        super(3600, 24 * 3600 - 1, scenario.getNetwork());
        this.scenario = scenario;
        this.filename = filename;
        this.network = scenario.getNetwork();
    }

    // Methods
    @Override
    public void reset(int iteration) {
        super.reset(iteration);
        linkVolumesWriter.clear();
    }

    public void write(){
        log.info("write linkvolumes");
        for (Id<Link> linkId: super.getLinkIds()) {
            for (String aMode: this.network.getLinks().get(linkId).getAllowedModes()) {
                int[] volumes = super.getVolumesForLink(linkId, aMode);
                int[] nbPassengers = super.getPassengerVolumesForLink(linkId, aMode);
                if (volumes != null) {
                    for (int i = 0; i < volumes.length; i++) {
                        HashMap<String, String> aRow = linkVolumesWriter.addRow();
                        aRow.put(COL_LINK_ID, linkId.toString());
                        aRow.put(COL_MODE, aMode.toString());
                        aRow.put(COL_BIN, Integer.toString(i + 1));
                        aRow.put(COL_VOLUME, Integer.toString(volumes[i]));
                        aRow.put(COL_NBPASSENGERS, Integer.toString(nbPassengers[i]));
                    }
                }
            }
        }
        linkVolumesWriter.write(this.filename + FILENAME_VOLUMES);
    }

    @Override
    public void closeFile() {
        this.write();
    }
}
