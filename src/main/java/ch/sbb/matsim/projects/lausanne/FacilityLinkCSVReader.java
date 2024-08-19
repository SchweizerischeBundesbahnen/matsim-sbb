package ch.sbb.matsim.projects.lausanne;

import ch.sbb.matsim.csv.CSVReader;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.FacilitiesUtils;
import org.matsim.facilities.FacilitiesWriter;
import org.matsim.facilities.MatsimFacilitiesReader;

import java.io.IOException;

public class FacilityLinkCSVReader {
    public static void main(String[] args) throws IOException {

        String inputFacilitiesFile = args[0];
        String csvFile = args[1];
        String outputFacilitiesFile = args[2];

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimFacilitiesReader(scenario).readFile(inputFacilitiesFile);
        try (CSVReader reader = new CSVReader(csvFile, ";")) {
            var line = reader.readLine();
            while (line != null) {
                Id<ActivityFacility> facilityId = Id.create(line.get("facilityId"), ActivityFacility.class);
                var facility = scenario.getActivityFacilities().getFacilities().get(facilityId);
                Id<Link> linkId = Id.createLinkId(line.get("parking"));
                FacilitiesUtils.setLinkID(facility, linkId);
                line = reader.readLine();

            }

        }
        new FacilitiesWriter(scenario.getActivityFacilities()).write(outputFacilitiesFile);

    }
}
