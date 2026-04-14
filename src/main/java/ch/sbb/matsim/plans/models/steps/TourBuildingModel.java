package ch.sbb.matsim.plans.models.steps;


import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.constants.ZonalAttributes;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.steps.csvmodels.IntegerSelectionModelFromCSV;
import ch.sbb.matsim.plans.synpop.SynPopUtils;
import ch.sbb.matsim.plans.tours.Tour;
import ch.sbb.matsim.zones.Zone;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import static ch.sbb.matsim.plans.models.steps.IterativeDailyPlanGenerationOrchestrator.NEEDS_DAILY_REPLANNING;

/*
   * This model step is responsible for building tours for each person in the population based on their attributes. Besides the number of stops for each tour, it also assigns the type of secondary activities for the stops based on a separate model.
   *  The generated tours are then stored as an attribute of the person for use in later steps.
 */
@Singleton
public class TourBuildingModel {

    public static final String IS_WORKING_FROM_HOME_AND_HAS_ONE_WORKTOUR = "is_working_from_home_and_has_one_worktour";
    public static final String HOME_MUL_CAR_ACC_SC = "home_mul_car_acc_sc";
    private final Scenario scenario;
    private final int threads;
    private final IntegerSelectionModelFromCSV tourFrequencyWorkModel;
    private final IntegerSelectionModelFromCSV tourFrequencyEducationModel;
    private final IntegerSelectionModelFromCSV tourFrequencyOtherModel;
    private final IntegerSelectionModelFromCSV tourFrequencyBusinessModel;
    private final IntegerSelectionModelFromCSV stopFrequencyInboundWorkModel;
    private final IntegerSelectionModelFromCSV stopFrequencyOutboundWorkModel;
    private final IntegerSelectionModelFromCSV stopFrequencyInboundEducationModel;
    private final IntegerSelectionModelFromCSV stopFrequencyOutboundEducationModel;
    private final IntegerSelectionModelFromCSV stopFrequencyBusinessModel;
    private final IntegerSelectionModelFromCSV stopFrequencyOtherModel;
    private final IntegerSelectionModelFromCSV subtourFrequencyModelWork;
    private final IntegerSelectionModelFromCSV subtourFrequencyModelEducation;
    private final SecondaryActivityTypeAssignmentModel secondaryActivityTypeAssignmentModel;
    private final static Logger log = LogManager.getLogger(TourBuildingModel.class);
    private final PopulationFactory factory;

    @Inject
    public TourBuildingModel(Scenario scenario, MOBiPlansConfig mobiPlansConfig) {
        this.scenario = scenario;
        Path modelFolder = Paths.get(mobiPlansConfig.data().modelBaseDir()).resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_CHOICE_MODELS);
        this.threads = mobiPlansConfig.models().threads();
        tourFrequencyWorkModel = new IntegerSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.TOUR_FREQUENCY_WORK_MODEL_CSV).toString(), MOBiPlansConfig.TOUR_FREQUENCY_WORK_MODEL_CSV);
        tourFrequencyEducationModel = new IntegerSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.TOUR_FREQUENCY_EDUCATION_MODEL_CSV).toString(), MOBiPlansConfig.TOUR_FREQUENCY_EDUCATION_MODEL_CSV);
        tourFrequencyOtherModel = new IntegerSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.TOUR_FREQUENCY_OTHER_MODEL_CSV).toString(), MOBiPlansConfig.TOUR_FREQUENCY_OTHER_MODEL_CSV);
        tourFrequencyBusinessModel = new IntegerSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.TOUR_FREQUENCY_BUSINESS_MODEL_CSV).toString(), MOBiPlansConfig.TOUR_FREQUENCY_BUSINESS_MODEL_CSV);
        stopFrequencyInboundWorkModel = new IntegerSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.NUMBER_OF_STOPS_INBOUND_WORK_CSV).toString(), MOBiPlansConfig.NUMBER_OF_STOPS_INBOUND_WORK_CSV);
        stopFrequencyOutboundWorkModel = new IntegerSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.NUMBER_OF_STOPS_OUTBOUND_WORK_CSV).toString(), MOBiPlansConfig.NUMBER_OF_STOPS_OUTBOUND_WORK_CSV);
        stopFrequencyInboundEducationModel = new IntegerSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.NUMBER_OF_STOPS_INBOUND_EDUCATION_CSV).toString(), MOBiPlansConfig.NUMBER_OF_STOPS_INBOUND_EDUCATION_CSV);
        stopFrequencyOutboundEducationModel = new IntegerSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.NUMBER_OF_STOPS_OUTBOUND_EDUCATION_CSV).toString(), MOBiPlansConfig.NUMBER_OF_STOPS_OUTBOUND_EDUCATION_CSV);
        stopFrequencyBusinessModel = new IntegerSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.NUMBER_OF_STOPS_BUSINESS_CSV).toString(), MOBiPlansConfig.NUMBER_OF_STOPS_BUSINESS_CSV);
        stopFrequencyOtherModel = new IntegerSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.NUMBER_OF_STOPS_OTHER_CSV).toString(), MOBiPlansConfig.NUMBER_OF_STOPS_OTHER_CSV);
        subtourFrequencyModelWork = new IntegerSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.SUBTOUR_FREQUENCY_WORK_CSV).toString(), MOBiPlansConfig.SUBTOUR_FREQUENCY_WORK_CSV);
        subtourFrequencyModelEducation = new IntegerSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.SUBTOUR_FREQUENCY_EDUCATION_CSV).toString(), MOBiPlansConfig.SUBTOUR_FREQUENCY_EDUCATION_CSV);
        secondaryActivityTypeAssignmentModel = new SecondaryActivityTypeAssignmentModel(modelFolder.resolve(MOBiPlansConfig.SECONDARY_ACTIVITY_TYPE_ASSIGNMENT_CSV).toString());
        this.factory = scenario.getPopulation().getFactory();
    }

    public void runIteration(int iteration) {
        Population population = scenario.getPopulation();
        try (ForkJoinPool customPool = new ForkJoinPool(threads)){
            customPool.submit(() ->
                    population.getPersons().values().parallelStream()
                            .filter(person -> new PopulationSampler.ActivePersonPredicate().test(person)) // Only process active persons
                            .filter(p -> Boolean.TRUE.equals(p.getAttributes().getAttribute(NEEDS_DAILY_REPLANNING)))
                            .forEach(person -> processPerson(person, iteration))
            ).get();
        } catch (Exception e) {
            throw new RuntimeException("Parallel execution failed", e);
        }

    }
    public void printTourStats() {
        int initialCapacity = scenario.getPopulation().getPersons().size();
        int[] totTours = new int[initialCapacity];
        int validPersons = 0;

        // Variables for tour types
        long sumWork = 0, sumEdu = 0, sumBiz = 0, sumOther = 0;

        // Variables for subtours
        long sumWorkSubtours = 0, sumEduSubtours = 0;
        long countWorkTours = 0, countEduTours = 0;

        // Single pass over the population to collect all data efficiently
        for (Person person : scenario.getPopulation().getPersons().values()) {
            Integer tot = (Integer) person.getAttributes().getAttribute(SynpopConstants.NO_TOT_TOURS);
            if (tot == null) continue; // Skip unprocessed/inactive persons

            totTours[validPersons++] = tot;

            sumWork += getIntAttr(person, SynpopConstants.NO_WORK_TOURS);
            sumEdu += getIntAttr(person, SynpopConstants.NO_EDUCATION_TOURS);
            sumBiz += getIntAttr(person, SynpopConstants.NO_BUSINESS_TOURS);
            sumOther += getIntAttr(person, SynpopConstants.NO_OTHER_TOURS);

            @SuppressWarnings("unchecked")
            List<Tour> tours = (List<Tour>) person.getAttributes().getAttribute(SynpopConstants.TOURS);
            if (tours != null) {
                for (Tour t : tours) {
                    if (SBBActivities.work.equals(t.getMainActivityType())) {
                        countWorkTours++;
                        sumWorkSubtours += t.getSubtourActivities().size();
                    } else if (SBBActivities.education.equals(t.getMainActivityType())) {
                        countEduTours++;
                        sumEduSubtours += t.getSubtourActivities().size();
                    }
                }
            }
        }

        if (validPersons == 0) {
            log.warn("No tour statistics available. Make sure the model has run and generated tours.");
            return;
        }

        // Truncate array to actual size of processed persons and sort for median/min/max
        int[] exactTotTours = Arrays.copyOf(totTours, validPersons);
        Arrays.sort(exactTotTours);

        int min = exactTotTours[0];
        int max = exactTotTours[exactTotTours.length - 1];
        double average = Arrays.stream(exactTotTours).average().orElse(0.0);

        double median;
        int middle = exactTotTours.length / 2;
        if (exactTotTours.length % 2 == 0) {
            median = (exactTotTours[middle - 1] + exactTotTours[middle]) / 2.0;
        } else {
            median = exactTotTours[middle];
        }

        // Calculate Averages
        double avgWork = (double) sumWork / validPersons;
        double avgEdu = (double) sumEdu / validPersons;
        double avgBiz = (double) sumBiz / validPersons;
        double avgOther = (double) sumOther / validPersons;

        double avgWorkSubPerTour = countWorkTours > 0 ? (double) sumWorkSubtours / countWorkTours : 0.0;
        double avgEduSubPerTour = countEduTours > 0 ? (double) sumEduSubtours / countEduTours : 0.0;

        // Print the statistics cleanly
        log.info("=========================================");
        log.info("          TOUR BUILDING STATS            ");
        log.info("=========================================");
        log.info("Total Active Persons     : {}", validPersons);
        log.info("-----------------------------------------");
        log.info("TOTAL TOURS (per person)");
        log.info("  Average : {}", String.format(Locale.US, "%.2f", average));
        log.info("  Median  : {}", median);
        log.info("  Min     : {}", min);
        log.info("  Max     : {}", max);
        log.info("-----------------------------------------");
        log.info("TOUR TYPES (average per person)");
        log.info("  Work      : {}", String.format(Locale.US, "%.2f", avgWork));
        log.info("  Education : {}", String.format(Locale.US, "%.2f", avgEdu));
        log.info("  Business  : {}", String.format(Locale.US, "%.2f", avgBiz));
        log.info("  Other     : {}", String.format(Locale.US, "%.2f", avgOther));
        log.info("-----------------------------------------");
        log.info("SUBTOURS (average per main tour)");
        log.info("  Work Subtours      : {} (over {} work tours)", String.format(Locale.US, "%.2f", avgWorkSubPerTour), countWorkTours);
        log.info("  Education Subtours : {} (over {} edu tours)", String.format(Locale.US, "%.2f", avgEduSubPerTour), countEduTours);
        log.info("=========================================");
    }

    /**
     * Null-safe attribute extractor helper
     */
    private int getIntAttr(Person p, String attrName) {
        Integer val = (Integer) p.getAttributes().getAttribute(attrName);
        return val == null ? 0 : val;
    }
    public void writeTourDetails(String filename) {
        List<String> columns = List.of(Variables.PERSONID,"tour_no","tourType","mainActivityType","inboundStopActivities","outboundStopActivities","subtourActivities");
        try (CSVWriter writer = new CSVWriter(columns,filename)){
            for (Person p : scenario.getPopulation().getPersons().values()){
                List<Tour> tours = (List<Tour>) p.getAttributes().getAttribute(SynpopConstants.TOURS);
                if (tours == null) continue; //inactive person
                for (int i = 0; i <tours.size() ; i++) {
                    var tour = tours.get(i);
                    writer.set(Variables.PERSONID,p.getId().toString());
                    writer.set("tour_no",Integer.toString(i));
                    writer.set("tourType",tour.getTourType().toString());
                    writer.set("mainActivityType",tour.getMainActivityType());
                    writer.set("inboundStopActivities", tour.getInboundStopActivities().stream().map(Activity::getType).collect(Collectors.joining(",")));
                    writer.set("outboundStopActivities", tour.getOutboundStopActivities().stream().map(Activity::getType).collect(Collectors.joining(",")));
                    writer.set("subtourActivities", tour.getSubtourActivities().stream().map(Activity::getType).collect(Collectors.joining(",")));
                    writer.writeRow();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

public void writeTourStats(String filename) {
        List<String> columns = List.of(Variables.PERSONID,SynpopConstants.NO_BUSINESS_TOURS,SynpopConstants.NO_EDUCATION_TOURS,SynpopConstants.NO_WORK_TOURS,SynpopConstants.NO_OTHER_TOURS,SynpopConstants.NO_PRIM_TOURS,SynpopConstants.NO_TOT_TOURS);
        try (CSVWriter writer = new CSVWriter(columns,filename)){
            for (Person p : scenario.getPopulation().getPersons().values()){
                    for (String column:columns) {
                        if ((Boolean)p.getAttributes().getAttribute(SynpopConstants.ACTIVE_IN_MODEL)){
                        if (column.equals(Variables.PERSONID)) {
                            writer.set(column, p.getId().toString());
                            continue;
                        }
                        Object value = p.getAttributes().getAttribute(column);
                        writer.set(column, value != null ? value.toString() : "");
                        writer.writeRow();
                        }
                    }

            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void processPerson(Person person, int iteration) {
        var attributes = person.getAttributes();
        attributes.removeAttribute(SynpopConstants.TOURS); // Clear any existing tours if re-processing
        // 1. Work Tours
        int noWorkTours = 0;
        Number levelOfEmployment = (Number) attributes.getAttribute(SynpopConstants.LEVEL_OF_EMPLOYMENT);
        if (levelOfEmployment != null && levelOfEmployment.intValue() > 0) {
            noWorkTours = tourFrequencyWorkModel.calculate(person);
        }
        attributes.putAttribute(SynpopConstants.NO_WORK_TOURS, noWorkTours);

        // 2. Education Tours
        int noEducationTours = 0;
        Number currentEdu = (Number) attributes.getAttribute(SynpopConstants.CURRENT_EDU);

        if (currentEdu != null && currentEdu.intValue() > 0 && attributes.getAttribute(SynpopConstants.SCHOOL_ZONE) != null) {
            noEducationTours = tourFrequencyEducationModel.calculate(person);
        }
        attributes.putAttribute(SynpopConstants.NO_EDUCATION_TOURS, noEducationTours);

        // 3. Sum up primary Tours
        int noPrimTours = noWorkTours + noEducationTours;
        attributes.putAttribute(SynpopConstants.NO_PRIM_TOURS, noPrimTours);

        // 4. Business Tours
        int noBusinessTours = 0;
        if (levelOfEmployment != null && levelOfEmployment.intValue() > 0) {
            noBusinessTours = tourFrequencyBusinessModel.calculate(person);
        }
        attributes.putAttribute(SynpopConstants.NO_BUSINESS_TOURS, noBusinessTours);

        // 5. Other Tours
        boolean isWorkingFromHome = Boolean.TRUE.equals(attributes.getAttribute(SynpopConstants.IS_WORKING_FROM_HOME));
        attributes.putAttribute(IS_WORKING_FROM_HOME_AND_HAS_ONE_WORKTOUR, isWorkingFromHome && noWorkTours == 1);

        double homeMulCarAccSc = 0.0;
        if (Boolean.TRUE.equals(attributes.getAttribute(SynpopConstants.CAR_AVAILABLE))) {
            Zone zone = (Zone) attributes.getAttribute("zone");
            if (zone != null && zone.getAttribute(ZonalAttributes.ACC_MUL_SC) != null) {
                homeMulCarAccSc = ((Number) zone.getAttribute(ZonalAttributes.ACC_MUL_SC)).doubleValue();
            }
        }
        attributes.putAttribute(HOME_MUL_CAR_ACC_SC, homeMulCarAccSc);

        int noOtherTours = tourFrequencyOtherModel.calculate(person);
        attributes.putAttribute(SynpopConstants.NO_OTHER_TOURS, noOtherTours);

        // Cleanup temp attributes
        attributes.removeAttribute(IS_WORKING_FROM_HOME_AND_HAS_ONE_WORKTOUR);
        attributes.removeAttribute(HOME_MUL_CAR_ACC_SC);

        // 6. Total Tours
        int noTotTours = noPrimTours + noOtherTours + noBusinessTours;
        attributes.putAttribute(SynpopConstants.NO_TOT_TOURS, noTotTours);

        // 7. Generate Stops and Subtours
        List<Tour> tours = new ArrayList<>();
        Random activityTypeRandom = SynPopUtils.getRandom(person.getId(),SecondaryActivityTypeAssignmentModel.class.getName()+iteration);
        for (int i = 0; i < noWorkTours; i++) {
            Tour tour = new Tour(Tour.TourType.PRIMARY, SBBActivities.work);
            populateTour(tour, factory, stopFrequencyInboundWorkModel.calculate(person), stopFrequencyOutboundWorkModel.calculate(person), subtourFrequencyModelWork.calculate(person), person, activityTypeRandom);
            tours.add(tour);
        }

        for (int i = 0; i < noEducationTours; i++) {
            Tour tour = new Tour(Tour.TourType.PRIMARY, SBBActivities.education);
            Integer stopFreQInBound = stopFrequencyInboundEducationModel.calculate(person);
            Integer stopFreqOutbound = stopFrequencyOutboundEducationModel.calculate(person);
            Integer subtourEdu = subtourFrequencyModelEducation.calculate(person);
            populateTour(tour, factory, stopFreQInBound, stopFreqOutbound, subtourEdu, person, activityTypeRandom);
            tours.add(tour);
        }

        for (int i = 0; i < noBusinessTours; i++) {
            Tour tour = new Tour(Tour.TourType.SECONDARY, SBBActivities.business);
            populateTour(tour, factory, stopFrequencyBusinessModel.calculate(person), stopFrequencyBusinessModel.calculate(person), 0, person, activityTypeRandom);
            tours.add(tour);
        }

        for (int i = 0; i < noOtherTours; i++) {
            String activityType = secondaryActivityTypeAssignmentModel.assignActivityType(person, activityTypeRandom);
            Tour tour = new Tour(Tour.TourType.SECONDARY, activityType);
            populateTour(tour, factory, stopFrequencyOtherModel.calculate(person), stopFrequencyOtherModel.calculate(person), 0, person, activityTypeRandom);
            tours.add(tour);
        }


        attributes.putAttribute(SynpopConstants.TOURS, tours);
    }

    /**
     * Helper method to reduce code duplication when generating activities for tours.
     */
    private void populateTour(Tour tour, PopulationFactory factory, int inbound, int outbound, int subtours, Person person, Random secondaryActivityTypeAssignementModelPersonalRandom) {
        for (int j = 0; j < inbound; j++) {
            String activityType = secondaryActivityTypeAssignmentModel.assignActivityType(person, secondaryActivityTypeAssignementModelPersonalRandom);
            tour.getInboundStopActivities().add(factory.createActivityFromCoord(activityType, null));
        }
        for (int j = 0; j < outbound; j++) {
            String activityType = secondaryActivityTypeAssignmentModel.assignActivityType(person, secondaryActivityTypeAssignementModelPersonalRandom);
            tour.getOutboundStopActivities().add(factory.createActivityFromCoord(activityType, null));
        }
        for (int j = 0; j < subtours; j++) {
            String activityType = secondaryActivityTypeAssignmentModel.assignActivityType(person, secondaryActivityTypeAssignementModelPersonalRandom);
            tour.getSubtourActivities().add(factory.createActivityFromCoord(activityType, null));
        }
    }

 }


