package ch.sbb.matsim.plans.models.steps;


import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.constants.ZonalAttributes;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.steps.csvmodels.IntegerSelectionModelFromCSV;
import ch.sbb.matsim.plans.synpop.SynPopUtils;
import ch.sbb.matsim.plans.tours.Tour;
import ch.sbb.matsim.zones.Zone;
import jakarta.inject.Inject;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

/*
   * This model step is responsible for building tours for each person in the population based on their attributes. Besides the number of stops for each tour, it also assigns the type of secondary activities for the stops based on a separate model.
   *  The generated tours are then stored as an attribute of the person for use in later steps.
 */
public class TourBuildingModel implements ModelStep{

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
    private final MOBiPlansConfig mobiPlansConfg;


    @Inject
           public TourBuildingModel(Scenario scenario, MOBiPlansConfig mobiPlansConfig) {
        this.scenario = scenario;
        Path modelFolder = Paths.get(mobiPlansConfig.data().modelBaseDir());
        this.threads = mobiPlansConfig.models().threads();
        this.mobiPlansConfg = mobiPlansConfig;
        tourFrequencyWorkModel = new IntegerSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.TOUR_FREQUENCY_WORK_MODEL_CSV).toString());
        tourFrequencyEducationModel = new IntegerSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.TOUR_FREQUENCY_EDUCATION_MODEL_CSV).toString());
        tourFrequencyOtherModel = new IntegerSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.TOUR_FREQUENCY_OTHER_MODEL_CSV).toString());
        tourFrequencyBusinessModel = new IntegerSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.TOUR_FREQUENCY_BUSINESS_MODEL_CSV).toString());
        stopFrequencyInboundWorkModel = new IntegerSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.NUMBER_OF_STOPS_INBOUND_WORK_CSV).toString());
        stopFrequencyOutboundWorkModel = new IntegerSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.NUMBER_OF_STOPS_OUTBOUND_WORK_CSV).toString());
        stopFrequencyInboundEducationModel = new IntegerSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.NUMBER_OF_STOPS_INBOUND_EDUCATION_CSV).toString());
        stopFrequencyOutboundEducationModel = new IntegerSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.NUMBER_OF_STOPS_OUTBOUND_EDUCATION_CSV).toString());
        stopFrequencyBusinessModel = new IntegerSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.NUMBER_OF_STOPS_BUSINESS_CSV).toString());
        stopFrequencyOtherModel = new IntegerSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.NUMBER_OF_STOPS_OTHER_CSV).toString());
        subtourFrequencyModelWork = new IntegerSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.SUBTOUR_FREQUENCY_WORK_CSV).toString());
        subtourFrequencyModelEducation = new IntegerSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.SUBTOUR_FREQUENCY_EDUCATION_CSV).toString());
        secondaryActivityTypeAssignmentModel = new SecondaryActivityTypeAssignmentModel(modelFolder.resolve(MOBiPlansConfig.SECONDARY_ACTIVITY_TYPE_ASSIGNMENT_CSV).toString());
    }

    @Override
    public String getName() {
        return MOBiPlansRunner.TOURBUILDING_MODEL;
    }
    @Override
    public void run() {
        Population population = scenario.getPopulation();
        PopulationFactory factory = population.getFactory();
        ForkJoinPool customPool = new ForkJoinPool(threads);
        try {
            customPool.submit(() ->
                    population.getPersons().values().parallelStream()
                            .forEach(person -> processPerson(person, factory))
            ).get();
        } catch (Exception e) {
            throw new RuntimeException("Parallel execution failed", e);
        } finally {
            // 3. Just make sure to always shut it down!
            customPool.shutdown();
        }
        if (mobiPlansConfg.models().write_analysis()){
            writeTourDetails(mobiPlansConfg.data().outputBaseDir()+"/tour_building_model_output.csv.gz");
        }
    }

    private void writeTourDetails(String filename) {
        List<String> columns = List.of(Variables.PERSONID,"tour_no","tourType","mainActivityType","inboundStopActivities","outboundStopActivities","subtourActivities");
        try (CSVWriter writer = new CSVWriter(columns,filename)){
            for (Person p : scenario.getPopulation().getPersons().values()){
                List<Tour> tours = (List<Tour>) p.getAttributes().getAttribute(SynpopConstants.TOURS);
                for (int i = 0; i <tours.size() ; i++) {
                    var tour = tours.get(i);
                    writer.set(Variables.PERSONID,p.getId().toString());
                    writer.set("tour_no",Integer.toString(i));
                    writer.set("tourType",tour.getTourType().toString());
                    writer.set("mainActivityType",tour.getMainActivityType());
                    writer.set("inboundStopActivities",tour.getInboundStopActivities().stream().map(activity -> activity.getType()).collect(Collectors.joining(",")).toString());
                    writer.set("outboundStopActivities",tour.getOutboundStopActivities().stream().map(activity -> activity.getType()).collect(Collectors.joining(",")).toString());
                    writer.set("subtourActivities",tour.getSubtourActivities().stream().map(activity -> activity.getType()).collect(Collectors.joining(",")).toString());
                    writer.writeRow();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void processPerson(Person person, PopulationFactory factory) {
        var attributes = person.getAttributes();

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
        Random activityTypeRandom = SynPopUtils.getRandom(person.getId(),SecondaryActivityTypeAssignmentModel.class.getName());
        if (((Number)person.getAttributes().getAttribute(SynpopConstants.AGE)).intValue()>5) {
            //FIXME: This is a temporary fix to avoid generating tours for very young children, in the final setup there should be pre-selection
        for (int i = 0; i < noWorkTours; i++) {
            Tour tour = new Tour(Tour.TourType.PRIMARY, SBBActivities.work);
            populateTour(tour, factory, stopFrequencyInboundWorkModel.calculate(person), stopFrequencyOutboundWorkModel.calculate(person), subtourFrequencyModelWork.calculate(person), person, activityTypeRandom);
            tours.add(tour);
        }

        for (int i = 0; i < noEducationTours; i++) {
            Tour tour = new Tour(Tour.TourType.PRIMARY, SBBActivities.education);
            populateTour(tour, factory, stopFrequencyInboundEducationModel.calculate(person), stopFrequencyOutboundEducationModel.calculate(person), subtourFrequencyModelEducation.calculate(person), person, activityTypeRandom);
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


