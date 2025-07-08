package ch.sbb.matsim.plans.models;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.models.resolver.NestedAttributeResolver;
import ch.sbb.matsim.plans.synpop.SynPopUtils;
import ch.sbb.matsim.zones.ZoneGeoPackageWriter;
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

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


public class RunMobilityTools {

    public static void main(String[] args) {
        var logger = LogManager.getLogger(RunMobilityTools.class);

        String folder = "Y:\\40_Projekte\\20250408_mobi_plans_6.0\\test_input_synpop\\";
//        String folder = "C:\\devsbb\\tmp\\synpop_2022\\";
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
        readEducationDistances(scenario, folder + "net-dist-school-run35.csv");
        SynPopUtils.linkHouseholdsAndPopulation(scenario);
        SynPopUtils.linkZonesToHouseHoldsAndPersons(scenario);
        SynPopUtils.calculateHouseHoldCompositions(scenario);
        BooleanSelectionModelFromCSV drivingLicenseModel = new BooleanSelectionModelFromCSV(modelFolder + "driving_license.csv");
        BooleanSelectionModelFromCSV ebikeModel = new BooleanSelectionModelFromCSV(modelFolder + "ebike45.csv");
        CarsPerHouseholdModel carsPerHouseholdModel = new CarsPerHouseholdModel(modelFolder + "cars_per_household.csv");
        PTSubscriptionModel ptSubscriptionModelAdults = new PTSubscriptionModel(modelFolder + "pt_subscription_adults.csv");
        PTSubscriptionModel ptSubscriptionModelMinors = new PTSubscriptionModel(modelFolder + "pt_subscription_minors.csv");

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
                    } else {
                        person.getAttributes().putAttribute(SynpopConstants.HAS_DRIVING_LICENSE, false);
                    }
                }
        );
        double calctime = (System.currentTimeMillis() - time) / 1000.0;
        logger.info("Number of persons with driving license: " + drivingLicenseCount.get());
        logger.info("Number of persons without driving license: " + (count.get() - drivingLicenseCount.get()));
        logger.info("Number of persons NOT in model: " + (scenario.getPopulation().getPersons().size() - count.get()));
        logger.info("Calculation time for driving license model: " + calctime);
        SynPopUtils.calculateDrivinglicensesPerHousehold(scenario);
        AtomicInteger cars = new AtomicInteger(0);
        AtomicInteger householdWithCars = new AtomicInteger(0);
        logger.info("Starting cars per household calculation...");
        scenario.getHouseholds().getHouseholds().values().parallelStream()
                .forEach(household ->
                        {
                            Integer numberOfCars = 0;
                            if ((Integer) household.getAttributes().getAttribute(SynpopConstants.NB_OF_DRIVING_LICENSES) > 0) {
                                numberOfCars = carsPerHouseholdModel.calculate(household);
                            }
                            household.getAttributes().putAttribute(SynpopConstants.NB_OF_CARS, numberOfCars);
                            double numberOfAdults = (Integer) household.getAttributes().getAttribute(SynpopConstants.NB_OF_ADULTS);
                            household.getAttributes().putAttribute(SynpopConstants.NB_OF_CARS_PER_ADULT, numberOfCars / Math.max(numberOfAdults, 1));
                            cars.addAndGet(numberOfCars);
                            if (numberOfCars > 0) {
                                householdWithCars.incrementAndGet();
                            }
                        }
                );
        logger.info("Number of cars: " + cars.get());
        logger.info("Number of households with cars: " + householdWithCars.get());

        logger.info("Starting public transport subscription calculation...");

        scenario.getPopulation().getPersons().values().parallelStream()
                .forEach(
                person -> {
                    Double netDistanceSchool = (Double) person.getAttributes().getAttribute(SynpopConstants.NET_DIST_SCHOOL);
                    if (netDistanceSchool == null) {
                        netDistanceSchool = 0.0;
                        person.getAttributes().putAttribute(SynpopConstants.NET_DIST_SCHOOL, netDistanceSchool);
                    }
                    person.getAttributes().putAttribute(SynpopConstants.NET_DIST_SCHOOL_PTSUBSCR, netDistanceSchool / 10.0);
                    String ptSubscription;
                    if ((int) person.getAttributes().getAttribute(SynpopConstants.AGE) < 18) {
                        ptSubscription = ptSubscriptionModelMinors.calculate(person);
                    } else {
                        ptSubscription = ptSubscriptionModelAdults.calculate(person);
                    }
                    person.getAttributes().putAttribute(Variables.PT_SUBSCRIPTION, ptSubscription);
                }
        );
        logger.info("Finished public transport subscription calculation.");

        logger.info("Starting ebike posession calculation...");

        scenario.getPopulation().getPersons().values().parallelStream()
                .forEach(
                        person -> {
                            boolean hasEbike45 = false;
                            if ((int) person.getAttributes().getAttribute(SynpopConstants.AGE) > 13) {
                                hasEbike45 = ebikeModel.calculate(person);
                            }
                            person.getAttributes().putAttribute(SynpopConstants.HAS_EBIKE45, hasEbike45);
                        }
                );
        logger.info("Finished e-bike calculation.");



        SynPopUtils.setCarAvailableForPopulation(scenario);
        SynPopUtils.aggregateMobilityToolsPerZone(scenario);
        ZoneGeoPackageWriter.writeAttributesToCSV(zones, folder + "zones_2022" + suffix + "_attributes.csv");

//        writeAttributesToCSV(scenario.getHouseholds().getHouseholds().values(), List.of(SynpopConstants.NB_OF_ADULTS, SynpopConstants.NB_OF_KIDS, SynpopConstants.NB_OF_CARS, SynpopConstants.NB_OF_DRIVING_LICENSES, SynpopConstants.NB_OF_DRIVING_LICENSES_PER_ADULT, SynpopConstants.LANGUAGE), folder + "households_2022" + suffix + "_attributes.csv");
//        writeAttributesToCSV(scenario.getPopulation().getPersons().values(),
//                List.of(SynpopConstants.HAS_DRIVING_LICENSE, SynpopConstants.AGE,
//                        SynpopConstants.IS_SWISS,SynpopConstants.LEVEL_OF_EMPLOYMENT,
//                        SynpopConstants.CURRENT_EDU,SynpopConstants.LANGUAGE,"household.couple_type","household.single_parent","household.size",
//                        "household.nb_of_cars", "household.nb_of_cars_per_adult","zone.acc_car_sc","zone.acc_mul_sc","zone.acc_pt_sc","zone.sl3_id",Variables.PT_SUBSCRIPTION ), "C:\\devsbb\\tmp\\synpop_2022\\persons_2022_AI_attributes.csv");
    }

    /*
     * Reads the education distances from a CSV file and adds them to the population's persons. Helper method, not required for the full mobi plans.
     */
    private static void readEducationDistances(Scenario scenario, String file) {
        try (CSVReader reader = new CSVReader(file, ";")) {
            var line = reader.readLine();
            while (line != null) {
                var personId = Id.createPersonId(line.get("$PERSON:NO"));
                double distance = Double.parseDouble(line.get("NETWORK_DISTANCE_SCHOOL"));
                if (scenario.getPopulation().getPersons().containsKey(Id.createPersonId(personId))) {
                    scenario.getPopulation().getPersons().get(Id.createPersonId(personId))
                            .getAttributes().putAttribute(SynpopConstants.NET_DIST_SCHOOL, distance);
                }
                line = reader.readLine();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
        NestedAttributeResolver resolver = new NestedAttributeResolver();
        StringBuilder sb = new StringBuilder();
        sb.append("id;").append(String.join(";", attributes)).append("\n");
        for (Attributable attributable : values) {
            String id = ((Identifiable) attributable).getId().toString();
            sb.append(id).append(";");
            for (String attribute : attributes) {
                Object value = resolver.resolve(attributable, attribute);
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
