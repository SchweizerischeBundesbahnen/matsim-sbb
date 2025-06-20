package ch.sbb.matsim.plans.models;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.synpop.SynPopUtils;
import ch.sbb.matsim.plans.synpop.SynpopConstants;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.households.HouseholdsReaderV10;
import org.matsim.utils.objectattributes.attributable.Attributable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


public class RunMobilityTools {

    public static void main(String[] args) {
        var logger = LogManager.getLogger(RunMobilityTools.class);

        String folder = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20250408_mobi_plans_6.0\\test_input_synpop\\";
        String suffix = ""; //whole population
//        String suffix = "_Bern"; //City of Bern
//        String suffix = "_AI"; //Appenzell Inner Rhodes
        String modelFolder = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20250408_mobi_plans_6.0\\choice_models\\";

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Zones zones = ZonesLoader.loadZones("zones", folder + "35_kalif_secondary.mobi-zones.shp");
        addAdditionalAttributes(zones);
        scenario.addScenarioElement(Variables.ZONES, zones);
        new PopulationReader(scenario).readFile(folder + "persons_2022" + suffix + ".xml.gz");
        new HouseholdsReaderV10(scenario.getHouseholds()).readFile(folder + "households_2022" + suffix + ".xml.gz");
        SynPopUtils.linkHouseholdsAndPopulation(scenario);
        SynPopUtils.linkZonesToHouseHoldsAndPersons(scenario);
        SynPopUtils.calculateHouseHoldCompositions(scenario);
        DrivingLicenseModelFromCSV drivingLicenseModel = new DrivingLicenseModelFromCSV(modelFolder + "driving_license.csv");
        CarsPerHouseholdModel carsPerHouseholdModel = new CarsPerHouseholdModel(modelFolder + "cars_per_household.csv");

        logger.info(("Starting driving license calculation..."));
        double time = System.currentTimeMillis();
        AtomicInteger count = new AtomicInteger(0);
        AtomicInteger drivingLicenseCount = new AtomicInteger(0);
        scenario.getPopulation().getPersons().values().parallelStream().forEach(
                person -> {
                    if ((int) person.getAttributes().getAttribute(SynpopConstants.AGE) >= 18) {
                        Boolean hasDrivingLicense = drivingLicenseModel.calculate(person);
                        person.getAttributes().putAttribute(SynpopConstants.HAS_DRIVING_LICENSE, hasDrivingLicense);
                        count.incrementAndGet();
                        if (hasDrivingLicense) {
                            drivingLicenseCount.incrementAndGet();
                        }
                    }
                }
        );
        double calctime = (System.currentTimeMillis() - time) / 1000.0;
        logger.info("Number of persons with driving license: " + drivingLicenseCount.get());
        logger.info("Number of persons without driving license: " + (count.get() - drivingLicenseCount.get()));
        logger.info("Number of persons NOT in model: " + (scenario.getPopulation().getPersons().size() - count.get()));
        logger.info("Calculation time for driving license model: " + calctime);
        SynPopUtils.calculateDrivinglicensesPerHousehold(scenario);

//        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
//        Configuration config = ctx.getConfiguration();
//        LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
//        loggerConfig.setLevel(Level.DEBUG);
//        ctx.updateLoggers();

        AtomicInteger cars = new AtomicInteger(0);
        AtomicInteger householdWithCars = new AtomicInteger(0);
        logger.info("Starting cars per household calculation...");
        scenario.getHouseholds().getHouseholds().values().parallelStream()
                .filter(household -> (Integer) household.getAttributes().getAttribute(SynpopConstants.NB_OF_DRIVING_LICENSES) > 0)
                .forEach(household ->
                        {
                            Integer numberOfCars = carsPerHouseholdModel.calculate(household);
                            household.getAttributes().putAttribute(SynpopConstants.NB_OF_CARS, numberOfCars);
                            cars.addAndGet(numberOfCars);
                            if (numberOfCars > 0) {
                                householdWithCars.incrementAndGet();
                            }
                        }
                );
        logger.info("Number of cars: " + cars.get());
        logger.info("Number of households with cars: " + householdWithCars.get());
//        writeAttributesToCSV(scenario.getHouseholds().getHouseholds().values(), List.of(SynpopConstants.NB_OF_ADULTS, SynpopConstants.NB_OF_KIDS, SynpopConstants.NB_OF_CARS, SynpopConstants.NB_OF_DRIVING_LICENSES, SynpopConstants.NB_OF_DRIVING_LICENSES_PER_ADULT, SynpopConstants.LANGUAGE), folder + "households_2022" + suffix + "_attributes.csv");
        //writeAttributesToCSV(scenario.getPopulation().getPersons().values(), List.of(SynpopConstants.HAS_DRIVING_LICENSE), "C:\\devsbb\\tmp\\synpop_2022\\persons_2022_attributes.csv");
    }

    private static void addAdditionalAttributes(Zones zones) {
        zones.getZones().forEach(zone -> {
            Double accsib_car = (Double) zone.getAttributes().getAttribute("accsib_car");
            Double accsib_pt = (Double) zone.getAttributes().getAttribute("accsib_pt");
            Double accsib_mul = (Double) zone.getAttributes().getAttribute("accsib_mul");
            zone.getAttributes().putAttribute("acc_car_sc", accsib_car / 1_000_000.0);
            zone.getAttributes().putAttribute("acc_mul_sc", accsib_mul / 1_000_000.0);
            zone.getAttributes().putAttribute("acc_pt_sc", accsib_pt / 1_000_000.0);

        });
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
