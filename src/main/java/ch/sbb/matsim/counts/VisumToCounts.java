/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.counts;

import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.csv.CSVWriter;
import com.google.common.collect.ObjectArrays;
import com.jacob.com.Dispatch;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.counts.Count;
import org.matsim.counts.Counts;
import org.matsim.counts.CountsWriter;

import java.io.*;
import java.util.Map;

public class VisumToCounts {

    private static String[] visumColumns = {"NAME", "ZW_DWV_FZG", "ID_SIM", "ADDVAL1"};

    private static String[] csvColumns = {"link_id", "mode", "bin", "volume", "zaehlstellen_bezeichnung", "road_type"};

    public void run(String visumAttributePath, String countsFilename, String csv) throws IOException {
        Counts<Link> counts = new Counts<>();
        counts.setYear(1000); // prevent a bug in MATSim...

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
                    String link_id = map.get("ID_SIM");
                    Id<Link> linkId = Id.create(link_id, Link.class);
                    String stationName = map.get("NAME") + "_" + map.get("ADDVAL1");
                    if (!counts.getCounts().containsKey(linkId) && !map.get("ZW_DWV_FZG").isEmpty()) {
                        Count<Link> count = counts.createAndAddCount(linkId, stationName);
                        count.createVolume(1, Double.parseDouble(map.get("ZW_DWV_FZG")));
                        for (int i = 2; i <= 24; i++)
                            count.createVolume(i, Double.parseDouble("0"));
                    }

                    writer.set("link_id", link_id);
                    writer.set("mode", "car");
                    writer.set("bin", "");
                    writer.set("volume", map.get("ZW_DWV_FZG"));
                    writer.set("zaehlstellen_bezeichnung", stationName);
                    writer.set("road_type", "");
                    writer.writeRow();
                }
            }
        } catch (IOException e) {
        }


        new CountsWriter(counts).write(countsFilename);
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
