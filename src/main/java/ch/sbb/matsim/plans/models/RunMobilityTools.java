package ch.sbb.matsim.plans.models;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.synpop.SynPopUtils;
import ch.sbb.matsim.plans.synpop.SynpopConstants;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.households.HouseholdsReaderV10;
import org.matsim.utils.objectattributes.attributable.Attributable;

import java.util.Collection;
import java.util.List;


public class RunMobilityTools {

    public static void main(String[] args) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Zones zones = ZonesLoader.loadZones("zones", "C:\\devsbb\\tmp\\synpop_2022\\35_kalif_secondary.mobi-zones.shp");
        scenario.addScenarioElement(Variables.ZONES, zones);
        new PopulationReader(scenario).readFile("C:\\devsbb\\tmp\\synpop_2022\\persons_2022_AI.xml.gz");
        new HouseholdsReaderV10(scenario.getHouseholds()).readFile("C:\\devsbb\\tmp\\synpop_2022\\households_2022_AI.xml.gz");
        SynPopUtils.linkHouseholdsAndPopulation(scenario);
        SynPopUtils.linkZonesToHouseHoldsAndPersons(scenario);
        SynPopUtils.calculateHouseHoldCompositions(scenario);
        var logger = LogManager.getLogger(RunMobilityTools.class);
        logger.info(("Starting driving license calculation..."));
        double time = System.currentTimeMillis();
        DrivingLicenseModel drivingLicenseModel = new DrivingLicenseModel();
        int i = 0;
        int persons = 0;
        for (Id personId : scenario.getPopulation().getPersons().keySet()) {
            var person = scenario.getPopulation().getPersons().get(personId);
            if ((int) person.getAttributes().getAttribute(SynpopConstants.AGE) >= 18) {
                Boolean hasDrivingLicense = drivingLicenseModel.calculate(person);
                person.getAttributes().putAttribute(SynpopConstants.HAS_DRIVING_LICENSE, hasDrivingLicense);
                persons++;
                if (hasDrivingLicense) {
                    i++;
                }
            }
        }
        double calctime = (System.currentTimeMillis() - time) / 1000.0;
        logger.info("Number of persons with driving license: " + i);
        logger.info("Number of persons without driving license: " + (persons - i));
        logger.info("Number of persons NOT in model: " + (scenario.getPopulation().getPersons().size() - persons));


        logger.info("Calculation time for driving license model: " + calctime);

        //writeAttributesToCSV(scenario.getPopulation().getPersons().values(), List.of(SynpopConstants.HAS_DRIVING_LICENSE), "C:\\devsbb\\tmp\\synpop_2022\\persons_2022_attributes.csv");
    }

    private static void writeAttributesToCSV(Collection<? extends Attributable> values, List<String> attributes, String outputfile) {
        StringBuilder sb = new StringBuilder();
        sb.append("id;").append(String.join(";", attributes)).append("\n");
        for (Attributable attributable : values) {
            String id = ((Identifiable) attributable).getId().toString();
            sb.append(id).append(";");
            for (String attribute : attributes) {
                Object value = attributable.getAttributes().getAttribute(attribute);
                sb.append(value != null ? value.toString() : "null").append(";");
            }
            sb.setLength(sb.length() - 1); // Remove last comma
            sb.append("\n");
        }
        try {
            java.nio.file.Files.write(java.nio.file.Paths.get(outputfile), sb.toString().getBytes());
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }
}
