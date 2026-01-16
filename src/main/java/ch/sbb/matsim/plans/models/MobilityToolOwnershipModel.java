package ch.sbb.matsim.plans.models;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.synpop.SynPopUtils;
import ch.sbb.matsim.zones.Zones;
import jakarta.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class MobilityToolOwnershipModel implements ModelStep {

    private  final BooleanSelectionModelFromCSV drivingLicenseModel;
    private  final BooleanSelectionModelFromCSV ebikeModel;
    private  final CarsPerHouseholdModel carsPerHouseholdModel;
    private  final PTSubscriptionModel ptSubscriptionModelAdults;
    private  final PTSubscriptionModel ptSubscriptionModelMinors;
    final Scenario scenario;
    final MOBiPlansConfig mobiPlansConfig;
    final Zones zones;
    private final String name = MOBiPlansRunner.MOBILITY_TOOL_OWNERSHIP_MODEL;

    @Inject
    public MobilityToolOwnershipModel(Scenario scenario, MOBiPlansConfig mobiPlansConfig, Zones zones) {
        this.scenario = scenario;
        this.mobiPlansConfig = mobiPlansConfig;
        this.zones = zones;
        String modelFolder = mobiPlansConfig.data().modelBaseDir()+"/";
        drivingLicenseModel = new BooleanSelectionModelFromCSV(modelFolder + MOBiPlansConfig.DRIVING_LICENSE_CSV);
        ebikeModel = new BooleanSelectionModelFromCSV(modelFolder + MOBiPlansConfig.EBIKE_45_CSV);
        carsPerHouseholdModel = new CarsPerHouseholdModel(modelFolder + MOBiPlansConfig.CARS_PER_HOUSEHOLD_CSV);
        ptSubscriptionModelAdults = new PTSubscriptionModel(modelFolder + MOBiPlansConfig.PT_SUBSCRIPTION_ADULTS_CSV);
        ptSubscriptionModelMinors = new PTSubscriptionModel(modelFolder + MOBiPlansConfig.PT_SUBSCRIPTION_MINORS_CSV);
    }

    private static final Logger logger = LogManager.getLogger(MobilityToolOwnershipModel.class);



    @Override
    public void run() {
        addAdditionalAttributes();
        runDrivingLicenseModel();
        runCarsPerHouseholdModel();
        runPublicTransportSubscription();
        runEBikeOwnershipModel();
        SynPopUtils.setCarAvailableForPopulation(scenario);
        SynPopUtils.aggregateMobilityToolsPerZone(scenario);

    }

    public void rerunForStudentsAfterPrimaryLocationChoice(){
        logger.info("Starting public transport subscription calculation for students...");
        scenario.getPopulation().getPersons().values().parallelStream()
                .filter(person -> ((Number) person.getAttributes().getAttribute(SynpopConstants.NET_DIST_SCHOOL)).doubleValue() > 0.0)
                .filter(person -> (int)person.getAttributes().getAttribute(SynpopConstants.AGE)>=18)
                .forEach(
                        person -> {
                            double netDistanceSchool = ((Number) person.getAttributes().getAttribute(SynpopConstants.NET_DIST_SCHOOL)).doubleValue();
                            person.getAttributes().putAttribute(SynpopConstants.NET_DIST_SCHOOL_PTSUBSCR, netDistanceSchool/10.0); //in km divided by 10
                            String ptSubscription = ptSubscriptionModelAdults.calculate(person);
                            person.getAttributes().putAttribute(Variables.PT_SUBSCRIPTION, ptSubscription);
                            }
                );
        logger.info("Finished public transport subscription calculation for students.");
        SynPopUtils.aggregateMobilityToolsPerZone(scenario);
    }

    private void runEBikeOwnershipModel() {
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
    }

    private void runPublicTransportSubscription() {
        logger.info("Starting public transport subscription calculation...");
        scenario.getPopulation().getPersons().values().parallelStream()
                .forEach(
                        person -> {
                            person.getAttributes().putAttribute(SynpopConstants.NET_DIST_SCHOOL_PTSUBSCR, 0.0);
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
    }

    private void runCarsPerHouseholdModel() {
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
    }

    private void runDrivingLicenseModel() {
        logger.info("Starting driving license calculation...");
        Instant start = Instant.now();
        long adults = scenario.getPopulation().getPersons().values().parallelStream()
                .peek(person -> person.getAttributes().putAttribute(SynpopConstants.HAS_DRIVING_LICENSE, false))
                .filter(p -> (int) p.getAttributes().getAttribute(SynpopConstants.AGE) >= 18)
                .peek(person -> {
                    boolean hasDrivingLicense = drivingLicenseModel.calculate(person);
                    person.getAttributes().putAttribute(SynpopConstants.HAS_DRIVING_LICENSE, hasDrivingLicense);
                })
                .count();

        long drivingLicenseCount = scenario.getPopulation().getPersons().values().parallelStream()
                .filter(p -> (Boolean) p.getAttributes().getAttribute(SynpopConstants.HAS_DRIVING_LICENSE))
                .count();
        logger.info("Number of persons with driving license: {}", drivingLicenseCount);
        logger.info("Number of persons without driving license: {}", adults - drivingLicenseCount);
        logger.info("Calculation time for driving license model: {}", Duration.between(start, Instant.now()));
        SynPopUtils.calculateDrivinglicensesPerHousehold(scenario);

    }


    private void addAdditionalAttributes() {
        zones.getZones().forEach(zone -> {
            Double accsib_car = (Double) zone.getAttributes().getAttribute("accsib_car");
            Double accsib_pt = (Double) zone.getAttributes().getAttribute("accsib_pt");
            Double accsib_mul = (Double) zone.getAttributes().getAttribute("accsib_mul");
            zone.getAttributes().putAttribute("acc_car_sc", accsib_car / 1_000_000.0);
            zone.getAttributes().putAttribute("acc_mul_sc", accsib_mul / 1_000_000.0);
            zone.getAttributes().putAttribute("acc_pt_sc", accsib_pt / 1_000_000.0);

        });
    }

    @Override
    public String getName() {
        return name;
    }
}
