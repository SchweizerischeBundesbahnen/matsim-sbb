/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis;

import ch.sbb.matsim.csv.CSVWriter;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.counts.Counts;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class LinkVolumeToCSV extends VolumesAnalyzerSBB implements EventsAnalysis {

    public static final String FILENAME_VOLUMES = "matsim_linkvolumes.csv.gz";
    public static final String COL_LINK_ID = "link_id";
    public static final String COL_MODE = "mode";
    public static final String COL_BIN = "bin";
	public static final String COL_VOLUME = "volume";
	protected static final String[] COLUMNS = {COL_LINK_ID, COL_MODE, COL_BIN, COL_VOLUME};
	private final static Logger log = LogManager.getLogger(LinkVolumeToCSV.class);
    private final String filename;
    private final Network network;

    public LinkVolumeToCSV(Scenario scenario, String filename) {
        super(3600, 24 * 3600 - 1, scenario.getNetwork());
        Counts<Link> counts = (Counts<Link>) scenario.getScenarioElement(Counts.ELEMENT_NAME);
        if (counts != null && !counts.getCounts().isEmpty()) { // by default, an empty counts is registered, even if no inputCountsFile was specified
            Set<Id<Link>> linkIds = new HashSet<>(counts.getCounts().keySet());
            super.setLinkFilter(linkIds);
        }
        this.filename = filename;
        this.network = scenario.getNetwork();
	}

	// Methods
	@Override
	public void writeResults(boolean lastIteration) {
		this.write(this.filename);
		if (lastIteration) {
			EventsAnalysis.copyToOutputFolder(this.filename, FILENAME_VOLUMES);
		}
	}

	public void write(String filename) {
		log.info("write linkvolumes to " + filename + FILENAME_VOLUMES);
		try (CSVWriter linkVolumesWriter = new CSVWriter("", COLUMNS, filename + FILENAME_VOLUMES)) {
			for (Id<Link> linkId : super.getLinkIds()) {
				try {
					for (String aMode : this.network.getLinks().get(linkId).getAllowedModes()) {
						int[] volumes = super.getVolumesForLink(linkId, aMode);
						if (volumes != null) {
							for (int i = 0; i < volumes.length; i++) {
								linkVolumesWriter.set(COL_LINK_ID, linkId.toString());
								linkVolumesWriter.set(COL_MODE, aMode);
								linkVolumesWriter.set(COL_BIN, Integer.toString(i + 1));
								linkVolumesWriter.set(COL_VOLUME, Double.toString(volumes[i]));
								linkVolumesWriter.writeRow();
							}
						}
					}
				} catch (Exception e) {
					log.info("couldn't write " + linkId.toString());
				}
			}
		} catch (IOException e) {
			log.error("Could not write linkvolumes. " + e.getMessage(), e);
		}
	}

}
