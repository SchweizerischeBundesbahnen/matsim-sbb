/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.mavi.counts;

import ch.sbb.matsim.csv.CSVReader;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.counts.Count;
import org.matsim.counts.Counts;
import org.matsim.counts.CountsWriter;

import java.io.IOException;
import java.util.Map;

/**
 * @author mrieser / SBB
 */
public class CsvToCounts {

	public static void main(String[] args) throws IOException {
		String csvFilename = "D:\\devsbb\\memop\\data\\link_count_data_v3.csv";
		String countsFilename = "D:\\devsbb\\memop\\data\\counts_astra15.xml";

		Counts<Link> counts = new Counts<>();
		counts.setYear(1000); // prevent a bug in MATSim...
		String[] columns = {"link_id", "mode", "bin", "volume", "zaehlstellen_bezeichnung", "road_type"};
		try (CSVReader reader = new CSVReader(columns, csvFilename, ";")) {
			Map<String, String> map = reader.readLine(); // header
			while ((map = reader.readLine()) != null) {
                Id<Link> linkId = Id.create(map.get("link_id"), Link.class);
                String stationName = map.get("road_type") + ":" + map.get("zaehlstellen_bezeichnung");
                Count<Link> count = counts.createAndAddCount(linkId, stationName);
                count.createVolume(1, Double.parseDouble(map.get("volume")));
                for (int i = 2; i <= 24; i++) {
                    count.createVolume(i, Double.parseDouble("0"));
                }
            }
		}
		new CountsWriter(counts).write(countsFilename);
	}

}
