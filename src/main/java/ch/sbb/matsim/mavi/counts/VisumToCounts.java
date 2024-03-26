/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.mavi.counts;

import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.mavi.streets.VisumStreetNetworkExporter;
import ch.sbb.matsim.mavi.visum.Visum;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.counts.Count;
import org.matsim.counts.Counts;
import org.matsim.counts.CountsWriter;

import java.io.IOException;

public class VisumToCounts {

    private static final String[] visumColumns = {"NAME", "FROMNODENO", "LINKNO", "XCOORD", "YCOORD", "ZW_DWV", "ZW_H00", "ZW_H01", "ZW_H02", "ZW_H03", "ZW_H04", "ZW_H05", "ZW_H06", "ZW_H07", "ZW_H08", "ZW_H09", "ZW_H10", "ZW_H11", "ZW_H12", "ZW_H13", "ZW_H14", "ZW_H15", "ZW_H16", "ZW_H17", "ZW_H18", "ZW_H19", "ZW_H20", "ZW_H21", "ZW_H22", "ZW_H23"};

    private static final String[] csvColumns = {"link_id", "mode", "bin", "volume", "zaehlstellen_bezeichnung", "road_type"};

    public void run(String[][] countsData, String countsFilename, String csv) throws IOException {
        Counts<Link> countshourly = new Counts<>();
        try (CSVWriter writer = new CSVWriter("", csvColumns, csv)) {

            for (var countData : countsData) {
                String stationName = countData[0];
                String fromNode = countData[1];
                String visumLinkId = countData[2];
                Id<Link> linkId = VisumStreetNetworkExporter.createLinkId(fromNode, visumLinkId);
                double xcoord = Double.parseDouble(countData[3]);
                double ycoord = Double.parseDouble(countData[4]);
                Coord coord = new Coord(xcoord, ycoord);
                if (!countshourly.getCounts().containsKey(linkId)) {
                    Count<Link> count = countshourly.createAndAddCount(linkId, stationName);
                    count.setCoord(coord);
                    for (int j = 1; j <= 24; j++) {
                        int h = j + 5;
                        count.createVolume(j, Double.parseDouble(countData[h]));
                    }
                }

                writer.set("link_id", linkId.toString());
                writer.set("mode", "car");
                writer.set("bin", "");
                writer.set("volume", countData[5]);
                writer.set("zaehlstellen_bezeichnung", stationName);
                writer.set("road_type", "");
                writer.writeRow();
            }
        } catch (IOException ignored) {
        }

        new CountsWriter(countshourly).write(countsFilename + ".xml.gz");

    }

    public void exportCountStations(Visum visum, String countsFilename, String csv) throws IOException {

        var countLocations = visum.getNetObject("CountLocations");
        var countsData = Visum.getArrayFromAttributeList(countLocations.countActive(), countLocations, visumColumns);
        run(countsData, countsFilename, csv);


    }

}
