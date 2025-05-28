package ch.sbb.matsim.plans.models;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.synpop.SynPopUtils;
import ch.sbb.matsim.plans.synpop.SynpopConstants;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.households.HouseholdsReaderV10;


public class RunMobilityTools {

    public static void main(String[] args) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Zones zones = ZonesLoader.loadZones("zones", "C:\\devsbb\\tmp\\synpop_2022\\35_kalif_secondary.mobi-zones.shp");
        scenario.addScenarioElement(Variables.ZONES, zones);
        new PopulationReader(scenario).readFile("C:\\devsbb\\tmp\\synpop_2022\\persons_2022.xml.gz");
        new HouseholdsReaderV10(scenario.getHouseholds()).readFile("C:\\devsbb\\tmp\\synpop_2022\\households_2022.xml.gz");
        SynPopUtils.linkHouseholdsAndPopulation(scenario);
        SynPopUtils.linkZonesToHouseHoldsAndPersons(scenario);
        SynPopUtils.calculateHouseHoldCompositions(scenario);
        var logger = LogManager.getLogger(RunMobilityTools.class);
        logger.info(("Starting driving license calculation..."));
        double time = System.currentTimeMillis();
        DrivingLicenseModel drivingLicenseModel = new DrivingLicenseModel();
        int i = 0;
        for (Id personId : scenario.getPopulation().getPersons().keySet()) {
            var person = scenario.getPopulation().getPersons().get(personId);
            if ((int) person.getAttributes().getAttribute(SynpopConstants.AGE) >= 18) {
                boolean hasDrivingLicense = drivingLicenseModel.calculate(person);
                person.getAttributes().putAttribute(SynpopConstants.HAS_DRIVING_LICENSE, hasDrivingLicense);
                if (hasDrivingLicense) {
                    i++;
                }
            }
        }
        double calctime = (System.currentTimeMillis() - time) / 1000.0;
        logger.info("Number of persons with driving license: " + i);
        logger.info("Calculation time for driving license model: " + calctime);
    }
}
