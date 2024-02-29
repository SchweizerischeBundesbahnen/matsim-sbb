/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.mavi.counts;

import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.mavi.streets.VisumStreetNetworkExporter;
import com.google.common.collect.ObjectArrays;
import com.jacob.com.Dispatch;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.counts.Count;
import org.matsim.counts.Counts;
import org.matsim.counts.CountsWriter;

import java.io.*;
import java.text.DecimalFormat;
import java.util.Map;

public class VisumToCounts {

	private static final String[] visumColumns = {"NAME", "ZW_DWV", "FROMNODENO", "LINKNO", "ZW_H00", "ZW_H01", "ZW_H02", "ZW_H03", "ZW_H04", "ZW_H05", "ZW_H06", "ZW_H07", "ZW_H08", "ZW_H09", "ZW_H10", "ZW_H11", "ZW_H12", "ZW_H13", "ZW_H14", "ZW_H15", "ZW_H16", "ZW_H17", "ZW_H18", "ZW_H19", "ZW_H20", "ZW_H21", "ZW_H22", "ZW_H23", "XCOORD", "YCOORD"};

    private static final String[] csvColumns = {"link_id", "mode", "bin", "volume", "zaehlstellen_bezeichnung", "road_type"};

    public void run(String visumAttributePath, String countsFilename, String csv) throws IOException {
        Counts<Link> countshourly = new Counts<>();
		DecimalFormat formatter = new DecimalFormat("00");
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
					String stationName = map.get("NAME");
					double xcoord = Double.parseDouble(map.get("XCOORD"));
					double ycoord = Double.parseDouble(map.get("YCOORD"));
					Coord coord = new Coord(xcoord, ycoord);
					if (!countshourly.getCounts().containsKey(linkId)) {
						Count<Link> count = countshourly.createAndAddCount(linkId, stationName);
						count.setCoord(coord);
						for (int i = 1; i <= 24; i++) {
							int h = i - 1;
							count.createVolume(i, Double.parseDouble(map.get("ZW_H" + formatter.format(h))));
						}
					}

                    writer.set("link_id", linkId.toString());
                    writer.set("mode", "car");
                    writer.set("bin", "");
					writer.set("volume", map.get("ZW_DWV"));
                    writer.set("zaehlstellen_bezeichnung", stationName);
                    writer.set("road_type", "");
                    writer.writeRow();
                }
            }
        } catch (IOException ignored) {
        }

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
