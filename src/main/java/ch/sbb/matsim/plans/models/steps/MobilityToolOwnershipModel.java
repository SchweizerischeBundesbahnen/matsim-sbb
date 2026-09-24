package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.steps.csvmodels.BooleanSelectionModelFromCSV;
import ch.sbb.matsim.plans.models.steps.csvmodels.IntegerSelectionModelFromCSV;
import ch.sbb.matsim.plans.models.steps.csvmodels.StringSelectionModelFromCSV;
import ch.sbb.matsim.plans.models.utils.AttributeUtils;
import ch.sbb.matsim.plans.synpop.SynPopUtils;
import ch.sbb.matsim.zones.Zones;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.concurrent.atomic.LongAdder;

@Singleton
public class MobilityToolOwnershipModel implements ModelStep {

    private  final BooleanSelectionModelFromCSV drivingLicenseModel;
    private  final BooleanSelectionModelFromCSV ebikeModel;
    private  final IntegerSelectionModelFromCSV carsPerHouseholdModel;
    private  final StringSelectionModelFromCSV ptSubscriptionModelAdults;
    private  final StringSelectionModelFromCSV ptSubscriptionModelMinors;
    final Scenario scenario;
    final MOBiPlansConfig mobiPlansConfig;
    final Zones zones;
    private final int threads;

    @Inject
    public MobilityToolOwnershipModel(Scenario scenario, MOBiPlansConfig mobiPlansConfig, Zones zones) {
        this.scenario = scenario;
        this.mobiPlansConfig = mobiPlansConfig;
        this.zones = zones;
        Path modelFolder = Paths.get(mobiPlansConfig.data().modelBaseDir()).resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_CHOICE_MODELS);
        drivingLicenseModel = new BooleanSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.DRIVING_LICENSE_CSV).toString(), MOBiPlansConfig.DRIVING_LICENSE_CSV);
        ebikeModel = new BooleanSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.EBIKE_45_CSV).toString(), MOBiPlansConfig.EBIKE_45_CSV);
        carsPerHouseholdModel = new IntegerSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.CARS_PER_HOUSEHOLD_CSV).toString(), MOBiPlansConfig.CARS_PER_HOUSEHOLD_CSV);
        ptSubscriptionModelAdults = new StringSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.PT_SUBSCRIPTION_ADULTS_CSV).toString(), MOBiPlansConfig.PT_SUBSCRIPTION_ADULTS_CSV);
        ptSubscriptionModelMinors = new StringSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.PT_SUBSCRIPTION_MINORS_CSV).toString(), MOBiPlansConfig.PT_SUBSCRIPTION_MINORS_CSV);
        this.threads= mobiPlansConfig.models().threads();
    }

    private static final Logger logger = LogManager.getLogger(MobilityToolOwnershipModel.class);



    @Override
    public void run() {
        try (ForkJoinPool pool = new ForkJoinPool(threads)) {
            pool.submit(() -> {
                runDrivingLicenseModel();
                runCarsPerHouseholdModel();
                runPublicTransportSubscription();
                runEBikeOwnershipModel();
            }).get();
        } catch (Exception e) {
            throw new RuntimeException("Parallel execution failed in MobilityToolOwnershipModel", e);
        }
        SynPopUtils.setCarAvailableForPopulation(scenario);
        SynPopUtils.aggregateMobilityToolsPerZone(scenario);
    }

    public void rerunForStudentsAfterPrimaryLocationChoice() {
        logger.info("Starting public transport subscription calculation for students after PrimaryLocationChoice...");
        try (ForkJoinPool pool = new ForkJoinPool(threads)) {
            pool.submit(() ->
                scenario.getPopulation().getPersons().values().parallelStream()
                        .filter(person -> (AttributeUtils.getAsDouble(person, SynpopConstants.NET_DIST_SCHOOL, 0.0) > 0.0))
                        .filter(person -> AttributeUtils.getAsInteger(person, SynpopConstants.AGE, -1) >= 18)
                        .forEach(person -> {
                            person.getAttributes().putAttribute("net_dist_school_ptsubscr", AttributeUtils.getAsDouble(person, SynpopConstants.NET_DIST_SCHOOL, 0) / 10000.0);
                            String ptSubscription = ptSubscriptionModelAdults.calculate(person);
                            person.getAttributes().putAttribute(Variables.PT_SUBSCRIPTION, ptSubscription);
                        })
            ).get();
        } catch (Exception e) {
            throw new RuntimeException("Parallel execution failed in rerunForStudentsAfterPrimaryLocationChoice", e);
        }
        logger.info("Finished public transport subscription calculation for students.");
        SynPopUtils.aggregateMobilityToolsPerZone(scenario);
    }

    private void runEBikeOwnershipModel() {
        scenario.getPopulation().getPersons().values().parallelStream()
                .forEach(person -> {
                    boolean hasEbike45 = false;
                    if (AttributeUtils.getAsInteger(person, SynpopConstants.AGE, -1) > 13) {
                        hasEbike45 = ebikeModel.calculate(person);
                    }
                    person.getAttributes().putAttribute(SynpopConstants.HAS_EBIKE45, hasEbike45);
                });
        logger.info("Finished e-bike calculation.");
    }

    private void runPublicTransportSubscription() {
        logger.info("Starting public transport subscription calculation...");
        scenario.getPopulation().getPersons().values().parallelStream()
                .forEach(person -> {
                    String ptSubscription;
                    if (AttributeUtils.getAsInteger(person, SynpopConstants.AGE, -1) < 18) {
                        ptSubscription = ptSubscriptionModelMinors.calculate(person);
                    } else {
                        person.getAttributes().putAttribute("net_dist_school_ptsubscr", 0);
                        ptSubscription = ptSubscriptionModelAdults.calculate(person);
                    }
                    person.getAttributes().putAttribute(Variables.PT_SUBSCRIPTION, ptSubscription);
                });
        logger.info("Finished public transport subscription calculation.");
    }

    private void runCarsPerHouseholdModel() {
        AtomicInteger cars = new AtomicInteger(0);
        AtomicInteger householdWithCars = new AtomicInteger(0);
        logger.info("Starting cars per household calculation...");
        scenario.getHouseholds().getHouseholds().values().parallelStream()
                .forEach(household -> {
                    Integer numberOfCars = 0;
                    if (AttributeUtils.getAsInteger(household, SynpopConstants.NB_OF_DRIVING_LICENSES, 0) > 0) {
                        numberOfCars = carsPerHouseholdModel.calculate(household);
                    }
                    household.getAttributes().putAttribute(SynpopConstants.NB_OF_CARS, numberOfCars);
                    double numberOfAdults = AttributeUtils.getAsInteger(household, SynpopConstants.NB_OF_ADULTS, 0);
                    household.getAttributes().putAttribute(SynpopConstants.NB_OF_CARS_PER_ADULT, numberOfCars / Math.max(numberOfAdults, 1));
                    cars.addAndGet(numberOfCars);
                    if (numberOfCars > 0) {
                        householdWithCars.incrementAndGet();
                    }
                });
        logger.info("Number of cars: " + cars.get());
        logger.info("Number of households with cars: " + householdWithCars.get());
    }

    private void runDrivingLicenseModel() {
        logger.info("Starting driving license calculation...");
        Instant start = Instant.now();
        LongAdder adultCounter = new LongAdder();
        scenario.getPopulation().getPersons().values().parallelStream().forEach(person -> {
            int age = AttributeUtils.getAsInteger(person, SynpopConstants.AGE, -1);
            if (age >= 18) {
                boolean hasDrivingLicense = drivingLicenseModel.calculate(person);
                person.getAttributes().putAttribute(SynpopConstants.HAS_DRIVING_LICENSE, hasDrivingLicense);
                adultCounter.increment();
            } else {
                person.getAttributes().putAttribute(SynpopConstants.HAS_DRIVING_LICENSE, false);
            }
        });

        long adults = adultCounter.sum();
        long drivingLicenseCount = scenario.getPopulation().getPersons().values().parallelStream()
                .filter(p -> (Boolean) p.getAttributes().getAttribute(SynpopConstants.HAS_DRIVING_LICENSE))
                .count();
        logger.info("Number of persons with driving license: {}", drivingLicenseCount);
        logger.info("Number of persons without driving license: {}", adults - drivingLicenseCount);
        logger.info("Calculation time for driving license model: {}", Duration.between(start, Instant.now()));
        SynPopUtils.calculateDrivinglicensesPerHousehold(scenario);

    }


    @Override
    public String getName() {
        String name = MOBiPlansRunner.MOBILITY_TOOL_OWNERSHIP_MODEL;
        return name;
    }
}
