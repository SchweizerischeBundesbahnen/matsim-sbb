/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.counts;

import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.mavi.streets.VisumStreetNetworkExporter;
import com.google.common.collect.ObjectArrays;
import com.jacob.com.Dispatch;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.counts.Count;
import org.matsim.counts.Counts;
import org.matsim.counts.CountsWriter;

public class VisumToCounts {

    private static final String[] visumColumns = {"NAME", "ZW_DWV_FZG", "FROMNODENO", "LINKNO", "ADDVAL1", "ZW_0_FZG", "ZW_1_FZG", "ZW_2_FZG", "ZW_3_FZG"
            , "ZW_4_FZG", "ZW_5_FZG", "ZW_6_FZG", "ZW_7_FZG", "ZW_8_FZG", "ZW_9_FZG", "ZW_10_FZG", "ZW_11_FZG", "ZW_12_FZG", "ZW_13_FZG", "ZW_14_FZG", "ZW_15_FZG"
            , "ZW_16_FZG", "ZW_17_FZG", "ZW_18_FZG", "ZW_19_FZG", "ZW_20_FZG", "ZW_21_FZG", "ZW_22_FZG", "ZW_23_FZG", "XCOORD", "YCOORD"};

    private static final String[] csvColumns = {"link_id", "mode", "bin", "volume", "zaehlstellen_bezeichnung", "road_type"};

    public void run(String visumAttributePath, String countsFilename, String csv) throws IOException {
        Counts<Link> counts = new Counts<>();
        Counts<Link> countshourly = new Counts<>();
        counts.setYear(1000); // prevent a bug in MATSim...
        countshourly.setYear(1000); // prevent a bug in MATSim...

        int skip = 13;
        int j = 0;
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
		try (BufferedReader in = new BufferedReader(new FileReader(visumAttributePath))) {
			String line;
			while ((line = in.readLine()) != null) {
				if (j >= skip) {
					if (!line.isEmpty()) {
						stream.write((line + System.lineSeparator()).getBytes());
					}
				}
				j = j + 1;
			}
		}

		ByteArrayInputStream fis = new ByteArrayInputStream(stream.toByteArray());

		try (CSVWriter writer = new CSVWriter("", csvColumns, csv)) {
			try (CSVReader reader = new CSVReader(ObjectArrays.concat("$COUNTLOCATION:NO", visumColumns), fis, ";")) {
				Map<String, String> map;
				while ((map = reader.readLine()) != null) {
					String visumLinkId = map.get("LINKNO");
					String fromNode = map.get("FROMNODENO");
					Id<Link> linkId = VisumStreetNetworkExporter.createLinkId(fromNode, visumLinkId);
					String stationName = map.get("NAME") + "_" + map.get("ADDVAL1");
					double xcoord = Double.parseDouble(map.get("XCOORD"));
					double ycoord = Double.parseDouble(map.get("YCOORD"));
					Coord coord = new Coord(xcoord, ycoord);
					if (!counts.getCounts().containsKey(linkId) && !map.get("ZW_DWV_FZG").isEmpty()) {
						Count<Link> count = counts.createAndAddCount(linkId, stationName);
						count.setCoord(coord);
						count.createVolume(1, Double.parseDouble(map.get("ZW_DWV_FZG")));
						for (int i = 2; i <= 24; i++) {
							count.createVolume(i, Double.parseDouble("0"));
						}
					}
					if (!countshourly.getCounts().containsKey(linkId) && !map.get("ZW_0_FZG").isEmpty()) {
						Count<Link> count = countshourly.createAndAddCount(linkId, stationName);
						count.setCoord(coord);
						for (int i = 1; i <= 24; i++) {
							int h = i - 1;
							count.createVolume(i, Double.parseDouble(map.get("ZW_" + h + "_FZG")));
						}
					}

                    writer.set("link_id", linkId.toString());
                    writer.set("mode", "car");
                    writer.set("bin", "");
                    writer.set("volume", map.get("ZW_DWV_FZG"));
                    writer.set("zaehlstellen_bezeichnung", stationName);
                    writer.set("road_type", "");
                    writer.writeRow();
                }
            }
        } catch (IOException ignored) {
        }

		new CountsWriter(counts).write(countsFilename + "_daily.xml");
		new CountsWriter(countshourly).write(countsFilename + "_hourly.xml");
	}

	public void exportCountStations(Dispatch net, String countsFilename, String csv) throws IOException {
		int separator = 59;
		File tempFile = File.createTempFile("simba-", "-mobi_pt_count_stations");
		Dispatch list = Dispatch.call(Dispatch.call(net, "Lists").toDispatch(), "CreateCountLocationList").toDispatch();

		for (String attribute : visumColumns) {
			Dispatch.call(list, "AddColumn", attribute);
		}
		Dispatch.call(list, "SaveToAttributeFile", tempFile.getAbsolutePath(), separator);
		this.run(tempFile.getAbsolutePath(), countsFilename, csv);

		tempFile.deleteOnExit();
	}

}
