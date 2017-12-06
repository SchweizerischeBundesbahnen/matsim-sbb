/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis;

import ch.sbb.matsim.csv.CSVWriter;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;

import java.util.HashMap;
import java.util.Set;


public class LinkVolumeToCSV extends VolumesAnalyzerSBB {

    private final static Logger log = Logger.getLogger(LinkVolumeToCSV.class);

    public static final String FILENAME_VOLUMES = "matsim_linkvolumes.csv";
    public static final String COL_LINK_ID = "link_id";
    public static final String COL_MODE = "mode";
    public static final String COL_BIN = "bin";
    public static final String COL_VOLUME = "volume";
    public static final String COL_NBPASSENGERS = "nb_passengers";
    public static final String[] COLUMNS = new String[]{COL_LINK_ID, COL_MODE, COL_BIN, COL_VOLUME, COL_NBPASSENGERS};

    Scenario scenario;

    private final CSVWriter linkVolumesWriter = new CSVWriter(COLUMNS);


    public LinkVolumeToCSV(Scenario scenario){
        super(3600, 24 * 3600 - 1, scenario.getNetwork());
        this.scenario = scenario;
    }

    // Methods
    @Override
    public void reset(int iteration) {
        super.reset(iteration);
        linkVolumesWriter.clear();
    }

    public void write(String path){
        log.info("write linkvolumes");
        Set<String> modes = super.getModes();
        for (Id<Link> linkId: super.getLinkIds()) {
            for (String aMode: modes) {
                int[] volumes = super.getVolumesForLink(linkId);
                int[] nbPassengers = super.getPassengerVolumesForLink(linkId);
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
        linkVolumesWriter.write(path + "/" + FILENAME_VOLUMES);
    }
}
