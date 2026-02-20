package ch.sbb.matsim.plans.models.steps;


import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.constants.ZonalAttributes;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.steps.csvmodels.IntegerSelectionModelFromCSV;
import ch.sbb.matsim.plans.tours.Tour;
import ch.sbb.matsim.zones.Zone;
import jakarta.inject.Inject;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;

import java.util.ArrayList;
import java.util.List;

public class NumberOfToursModel implements ModelStep{

    public static final String IS_WORKING_FROM_HOME_AND_HAS_ONE_WORKTOUR1 = "is_working_from_home_and_has_one_worktour";
    public static final String IS_WORKING_FROM_HOME_AND_HAS_ONE_WORKTOUR = "is_working_from_home_and_has_one_worktour";
    public static final String HOME_MUL_CAR_ACC_SC = "home_mul_car_acc_sc";
    private final Scenario scenario;
    private final IntegerSelectionModelFromCSV numberOfWorkToursModel;
    private final IntegerSelectionModelFromCSV numberOfEducationToursModel;
    private final IntegerSelectionModelFromCSV numberOfOtherToursModel;
    private final IntegerSelectionModelFromCSV numberOfBusinessToursModel;
    private final IntegerSelectionModelFromCSV stopFrequencyInboundWorkModel;
    private final IntegerSelectionModelFromCSV stopFrequencyOutboundWorkModel;
    private final IntegerSelectionModelFromCSV stopFrequencyInboundEducationModel;
    private final IntegerSelectionModelFromCSV stopFrequencyOutboundEducationModel;
    private final IntegerSelectionModelFromCSV stopFrequencyBusinessModel;
    private final IntegerSelectionModelFromCSV stopFrequencyOtherModel;
    private final IntegerSelectionModelFromCSV subtourFrequencyModelWork;
    private final IntegerSelectionModelFromCSV subtourFrequencyModelEducation;

    @Inject
           public NumberOfToursModel(Scenario scenario, MOBiPlansConfig mobiPlansConfig) {
        this.scenario = scenario;
        String modelFolder = mobiPlansConfig.data().modelBaseDir()+"/";
        numberOfWorkToursModel = new IntegerSelectionModelFromCSV(modelFolder + MOBiPlansConfig.NUMBER_OF_WORK_TOURS_CSV);
        numberOfEducationToursModel = new IntegerSelectionModelFromCSV(modelFolder + MOBiPlansConfig.NUMBER_OF_EDUCATION_TOURS_CSV);
        numberOfOtherToursModel = new IntegerSelectionModelFromCSV(modelFolder + MOBiPlansConfig.NUMBER_OF_OTHER_TOURS_CSV);
        numberOfBusinessToursModel = new IntegerSelectionModelFromCSV(modelFolder + MOBiPlansConfig.NUMBER_OF_BUSINESS_TOURS_CSV);
        stopFrequencyInboundWorkModel = new IntegerSelectionModelFromCSV(modelFolder + MOBiPlansConfig.NUMBER_OF_STOPS_INBOUND_WORK_CSV);
        stopFrequencyOutboundWorkModel = new IntegerSelectionModelFromCSV(modelFolder + MOBiPlansConfig.NUMBER_OF_STOPS_OUTBOUND_WORK_CSV);
        stopFrequencyInboundEducationModel = new IntegerSelectionModelFromCSV(modelFolder + MOBiPlansConfig.NUMBER_OF_STOPS_INBOUND_EDUCATION_CSV);
        stopFrequencyOutboundEducationModel = new IntegerSelectionModelFromCSV(modelFolder + MOBiPlansConfig.NUMBER_OF_STOPS_OUTBOUND_EDUCATION_CSV);
        stopFrequencyBusinessModel = new IntegerSelectionModelFromCSV(modelFolder + MOBiPlansConfig.NUMBER_OF_STOPS_BUSINESS_CSV);
        stopFrequencyOtherModel = new IntegerSelectionModelFromCSV(modelFolder + MOBiPlansConfig.NUMBER_OF_STOPS_OTHER_CSV);
        subtourFrequencyModelWork = new IntegerSelectionModelFromCSV(modelFolder + MOBiPlansConfig.SUBTOUR_FREQUENCY_WORK_CSV);
        subtourFrequencyModelEducation = new IntegerSelectionModelFromCSV(modelFolder + MOBiPlansConfig.SUBTOUR_FREQUENCY_EDUCATION_CSV);

    }

    @Override
    public String getName() {
        return MOBiPlansRunner.NUMBER_OF_TOURS_MODEL;
    }
    @Override
    public void run() {
        Population population = scenario.getPopulation();
        PopulationFactory factory = population.getFactory();

        // Process the entire population in a single parallel stream
        population.getPersons().values().parallelStream().forEach(person -> {
            var attributes = person.getAttributes();

            // 1. Work Tours
            int noWorkTours = 0;
            Number levelOfEmployment = (Number) attributes.getAttribute(SynpopConstants.LEVEL_OF_EMPLOYMENT);
            if (levelOfEmployment != null && levelOfEmployment.intValue() > 0) {
                noWorkTours = numberOfWorkToursModel.calculate(person);
            }
            attributes.putAttribute(SynpopConstants.NO_WORK_TOURS, noWorkTours);

            // 2. Education Tours
            int noEducationTours = 0;
            Number currentEdu = (Number) attributes.getAttribute(SynpopConstants.CURRENT_EDU);
            if (currentEdu != null && currentEdu.intValue() > 0) {
                noEducationTours = numberOfEducationToursModel.calculate(person);
            }
            attributes.putAttribute(SynpopConstants.NO_EDUCATION_TOURS, noEducationTours);

            // 3. Sum up primary Tours
            int noPrimTours = noWorkTours + noEducationTours;
            attributes.putAttribute(SynpopConstants.NO_PRIM_TOURS, noPrimTours);

            // 4. Business Tours
            int noBusinessTours = 0;
            if (levelOfEmployment != null && levelOfEmployment.intValue() > 0) {
                noBusinessTours = numberOfBusinessToursModel.calculate(person);
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

            int noOtherTours = numberOfOtherToursModel.calculate(person);
            attributes.putAttribute(SynpopConstants.NO_OTHER_TOURS, noOtherTours);

            // Cleanup temp attributes
            attributes.removeAttribute(IS_WORKING_FROM_HOME_AND_HAS_ONE_WORKTOUR);
            attributes.removeAttribute(HOME_MUL_CAR_ACC_SC);

            // 6. Total Tours
            int noTotTours = noPrimTours + noOtherTours + noBusinessTours;
            attributes.putAttribute(SynpopConstants.NO_TOT_TOURS, noTotTours);

            // 7. Generate Stops and Subtours
            List<Tour> tours = new ArrayList<>();

            for (int i = 0; i < noWorkTours; i++) {
                Tour tour = new Tour(Tour.TourType.PRIMARY, SBBActivities.work);
                populateTour(tour, factory, stopFrequencyInboundWorkModel.calculate(person), stopFrequencyOutboundWorkModel.calculate(person), subtourFrequencyModelWork.calculate(person));
                tours.add(tour);
            }

            for (int i = 0; i < noEducationTours; i++) {
                Tour tour = new Tour(Tour.TourType.PRIMARY, SBBActivities.education);
                populateTour(tour, factory, stopFrequencyInboundEducationModel.calculate(person), stopFrequencyOutboundEducationModel.calculate(person), subtourFrequencyModelEducation.calculate(person));
                tours.add(tour);
            }

            for (int i = 0; i < noBusinessTours; i++) {
                Tour tour = new Tour(Tour.TourType.SECONDARY, SBBActivities.business);
                populateTour(tour, factory, stopFrequencyBusinessModel.calculate(person), stopFrequencyBusinessModel.calculate(person), 0);
                tours.add(tour);
            }

            for (int i = 0; i < noOtherTours; i++) {
                Tour tour = new Tour(Tour.TourType.SECONDARY, Tour.UNDEFINED_ACTIVITY_TYPE);
                populateTour(tour, factory, stopFrequencyOtherModel.calculate(person), stopFrequencyOtherModel.calculate(person), 0);
                tours.add(tour);
            }

            attributes.putAttribute(SynpopConstants.TOURS, tours);
        });
    }

    /**
     * Helper method to reduce code duplication when generating activities for tours.
     */
    private void populateTour(Tour tour, PopulationFactory factory, int inbound, int outbound, int subtours) {
        for (int j = 0; j < inbound; j++) {
            tour.getInboundStopActivities().add(factory.createActivityFromCoord(Tour.UNDEFINED_ACTIVITY_TYPE, null));
        }
        for (int j = 0; j < outbound; j++) {
            tour.getOutboundStopActivities().add(factory.createActivityFromCoord(Tour.UNDEFINED_ACTIVITY_TYPE, null));
        }
        for (int j = 0; j < subtours; j++) {
            tour.getSubtourActivities().add(factory.createActivityFromCoord(Tour.UNDEFINED_ACTIVITY_TYPE, null));
        }
    }

 }


