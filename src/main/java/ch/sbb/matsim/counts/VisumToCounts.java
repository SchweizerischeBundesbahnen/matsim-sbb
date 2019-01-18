/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.counts;

import ch.sbb.matsim.csv.CSVReader;
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

    private static String[] visumColumns = {"CODE", "NAME", "VOLUME", "LINKNO", "FROMNODENO", "TONODENO"};

    public void run(String visumAttributePath, String countsFilename) throws IOException {
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

        try (CSVReader reader = new CSVReader(ObjectArrays.concat("$COUNTLOCATION:NO", visumColumns), fis, ";")) {
            Map<String, String> map;
            while ((map = reader.readLine()) != null) {
                String link_id = map.get("LINKNO") + "_" + map.get("FROMNODENO") + "_" + map.get("TONODENO");
                Id<Link> linkId = Id.create(link_id, Link.class);
                String road_type = "";
                String stationName = road_type + ":" + map.get("NAME");
                if (!counts.getCounts().containsKey(linkId) && !map.get("VOLUME").isEmpty()) {
                    Count<Link> count = counts.createAndAddCount(linkId, stationName);
                    count.createVolume(1, Double.parseDouble(map.get("VOLUME")));
                    for (int i = 2; i <= 24; i++)
                        count.createVolume(i, Double.parseDouble("0"));
                }
            }
        }


        new CountsWriter(counts).write(countsFilename);
    }


    public void exportCountStations(Dispatch net, String countsFilename) throws IOException {
        int separator = 59;
        File tempFile = File.createTempFile("simba-", "-mobi_pt_count_stations");
        Dispatch list = Dispatch.call(Dispatch.call(net, "Lists").toDispatch(), "CreateCountLocationList").toDispatch();

        for (String attribute : visumColumns) {
            Dispatch.call(list, "AddColumn", attribute);
        }
        Dispatch.call(list, "SaveToAttributeFile", tempFile.getAbsolutePath(), separator);
        this.run(tempFile.getAbsolutePath(), countsFilename);

        tempFile.deleteOnExit();
    }

}
