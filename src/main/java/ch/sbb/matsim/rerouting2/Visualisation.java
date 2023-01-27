package ch.sbb.matsim.rerouting2;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;

public class Visualisation {

    public static void main(String[] args) {

        createSaveFile();

    }

    private static void createSaveFile() {

        String linksConnrctionFile = "Z:/99_Playgrounds/MD/Umlegung2/basisSchedule/2018/link_sequences.csv";
        String polylines = "Z:/99_Playgrounds/MD/Umlegung2/basisSchedule/2018/polylines.csv";
        String networkFile = "Z:/99_Playgrounds/MD/Umlegung2/basisSchedule/2018/transitNetwork.xml.gz";
        String outputSaveFile = "saveFile.csv";

        Map<Id<Link>, DemandStorage2> idDemandStorageMap = new HashMap<>();

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);

        try (BufferedReader reader = new BufferedReader(new FileReader(linksConnrctionFile))) {

            List<String> header = List.of(reader.readLine().split(";"));
            String line;
            while ((line = reader.readLine()) != null) {
                var linkId = Id.createLinkId(line.split(";")[header.indexOf("matsim_link")]);
                if (scenario.getNetwork().getLinks().containsKey(linkId)) {
                    idDemandStorageMap.put(linkId, new DemandStorage2(linkId, line.split(";")[header.indexOf("link_sequence_visum")]));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(polylines))) {

            List<String> header = List.of(reader.readLine().split(";"));
            String line;
            while ((line = reader.readLine()) != null) {
                var linkId = Id.createLinkId(line.split(";")[header.indexOf("LINK")]);
                if (idDemandStorageMap.containsKey(linkId)) {
                    idDemandStorageMap.get(linkId).setWkt(line.split(";")[header.indexOf("WKT")]);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputSaveFile))) {

            writer.write("Matsim_Link;Demand;Visum_Link;WKT");
            writer.newLine();
            for (DemandStorage2 demandStorage : idDemandStorageMap.values()) {
                writer.write(demandStorage.toString());
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Done");
    }

}
