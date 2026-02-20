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

import java.util.ArrayList;
import java.util.List;

public class NumberOfToursModel implements ModelStep{

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

        //Number of work tours for the working population
        Population population = scenario.getPopulation();
        population.getPersons().values()
                .parallelStream()
                .peek(person -> person.getAttributes().putAttribute(SynpopConstants.NO_WORK_TOURS,0))
                .filter(person -> ((Number) person.getAttributes().getAttribute(SynpopConstants.LEVEL_OF_EMPLOYMENT)).intValue()>0)
                .forEach(person -> person.getAttributes().putAttribute(SynpopConstants.NO_WORK_TOURS,numberOfWorkToursModel.calculate(person)));

        //Number of education tours, anyone in education
        population.getPersons().values()
                .parallelStream()
                .peek(person -> person.getAttributes().putAttribute(SynpopConstants.NO_EDUCATION_TOURS,0))
                .filter(person -> ((Number) person.getAttributes().getAttribute(SynpopConstants.CURRENT_EDU)).intValue()>0)
                .forEach(person ->
                        {
                            Integer numberOfEducationTours = numberOfEducationToursModel.calculate(person);
                            person.getAttributes().putAttribute(SynpopConstants.NO_EDUCATION_TOURS, numberOfEducationTours);
                            Integer numberOfWorkTours = (Integer) person.getAttributes().getAttribute(SynpopConstants.NO_WORK_TOURS);
                            person.getAttributes().putAttribute(SynpopConstants.NO_PRIM_TOURS, numberOfWorkTours+numberOfEducationTours);
                        }

                );



        //Number of business tours, working population
        population.getPersons().values()
                .parallelStream()
                .peek(person -> person.getAttributes().putAttribute(SynpopConstants.NO_BUSINESS_TOURS,0))
                .filter(person -> ((Number) person.getAttributes().getAttribute(SynpopConstants.LEVEL_OF_EMPLOYMENT)).intValue()>0)
                .forEach(person -> person.getAttributes().putAttribute(SynpopConstants.NO_BUSINESS_TOURS,numberOfBusinessToursModel.calculate(person)));

        //Number of other tours, for everyone
        population.getPersons().values()
                .parallelStream()
                .forEach(person ->
                        {
                            //this model requires some additional attributes which are not used anywhere else
                            Boolean isWorkingFromHome = (Boolean) person.getAttributes().getAttribute(SynpopConstants.IS_WORKING_FROM_HOME);
                            Boolean isWorkingFromHomeAndHasOneWorktour = isWorkingFromHome != null && isWorkingFromHome && ((Integer) person.getAttributes().getAttribute(SynpopConstants.NO_WORK_TOURS))==1;
                            person.getAttributes().putAttribute("is_working_from_home_and_has_one_worktour", isWorkingFromHomeAndHasOneWorktour);
                            Double home_mul_car_acc_sc = 0.;
                            if ((Boolean)person.getAttributes().getAttribute(SynpopConstants.CAR_AVAILABLE)){
                             home_mul_car_acc_sc = ((Number)((Zone)person.getAttributes().getAttribute("zone")).getAttribute(ZonalAttributes.ACC_MUL_SC)).doubleValue();
                            }
                            person.getAttributes().putAttribute("home_mul_car_acc_sc", home_mul_car_acc_sc);
                            Integer numberOfOtherTours = numberOfOtherToursModel.calculate(person);
                            person.getAttributes().putAttribute(SynpopConstants.NO_OTHER_TOURS, numberOfOtherTours);
                            person.getAttributes().removeAttribute("is_working_from_home_and_has_one_worktour");
                            person.getAttributes().removeAttribute("home_mul_car_acc_sc");
                            //finally, sum all tours up
                            Integer noTotTours = (Integer) person.getAttributes().getAttribute(SynpopConstants.NO_PRIM_TOURS) + numberOfOtherTours + (Integer) person.getAttributes().getAttribute(SynpopConstants.NO_BUSINESS_TOURS);
                            person.getAttributes().putAttribute(SynpopConstants.NO_TOT_TOURS, noTotTours);
                }
                );
        
        
        runNumberOfStopsAndSubtours(population);

    }

    /* This method runs the models for number of stops and subtours for all tours. It needs to be run after the number of tours has been set, as it relies on that information.
     * It creates empty stop activities and subtour activities (with undefined type) and adds them to the tours, so that the resulting population has the full structure of tours, stops and subtours.
     * The actual types and locations of these activities and the order of tours will be set in later steps.
     * @param population
     */
    private void runNumberOfStopsAndSubtours(Population population) {

        var factory =  population.getFactory();
        population.getPersons().values().parallelStream()
                .forEach(person -> {
                    Integer noWorkTours = (Integer) person.getAttributes().getAttribute(SynpopConstants.NO_WORK_TOURS);
                    Integer noEducationTours = (Integer) person.getAttributes().getAttribute(SynpopConstants.NO_EDUCATION_TOURS);
                    Integer noBusinessTours = (Integer) person.getAttributes().getAttribute(SynpopConstants.NO_BUSINESS_TOURS);
                    Integer noOtherTours = (Integer) person.getAttributes().getAttribute(SynpopConstants.NO_OTHER_TOURS);
                    List<Tour> tours  = new ArrayList<>();
                    for (int i=0; i<noWorkTours; i++){
                        Tour tour = new Tour(Tour.TourType.PRIMARY, SBBActivities.work);
                        int stopsInbound = stopFrequencyInboundWorkModel.calculate(person);
                        int stopsOutbound = stopFrequencyOutboundWorkModel.calculate(person);
                        int subtours = subtourFrequencyModelWork.calculate(person);

                        for (int j=0; j<stopsInbound; j++){
                            tour.getInboundStopActivities().add(factory.createActivityFromCoord(Tour.UNDEFINED_ACTIVITY_TYPE, null));
                        }
                        for (int j=0; j<stopsOutbound; j++){
                            tour.getOutboundStopActivities().add(factory.createActivityFromCoord(Tour.UNDEFINED_ACTIVITY_TYPE, null));
                        }
                        for (int j=0; j<subtours; j++){
                            tour.getSubtourActivities().add(factory.createActivityFromCoord(Tour.UNDEFINED_ACTIVITY_TYPE, null));
                        }
                        tours.add(tour);
                    }
                    for (int i=0; i<noEducationTours; i++){
                        Tour tour = new Tour(Tour.TourType.PRIMARY, SBBActivities.education);
                        tours.add(tour);

                        int stopsInbound = stopFrequencyInboundEducationModel.calculate(person);
                        int stopsOutbound = stopFrequencyOutboundEducationModel.calculate(person);
                        int subtours = subtourFrequencyModelEducation.calculate(person);
                        for (int j=0; j<stopsInbound; j++){
                            tour.getInboundStopActivities().add(factory.createActivityFromCoord(Tour.UNDEFINED_ACTIVITY_TYPE, null));
                        }
                        for (int j=0; j<stopsOutbound; j++){
                            tour.getOutboundStopActivities().add(factory.createActivityFromCoord(Tour.UNDEFINED_ACTIVITY_TYPE, null));
                        }
                        for (int j=0; j<subtours; j++){
                            tour.getSubtourActivities().add(factory.createActivityFromCoord(Tour.UNDEFINED_ACTIVITY_TYPE, null));
                        }

                    }
                    for (int i=0; i<noBusinessTours; i++){
                        Tour tour = new Tour(Tour.TourType.SECONDARY, SBBActivities.business);
                        tours.add(tour);
                        int stopsInbound = stopFrequencyBusinessModel.calculate(person);
                        int stopsOutbound = stopFrequencyBusinessModel.calculate(person);
                        for (int j=0; j<stopsInbound; j++){
                            tour.getInboundStopActivities().add(factory.createActivityFromCoord(Tour.UNDEFINED_ACTIVITY_TYPE, null));
                        }
                        for (int j=0; j<stopsOutbound; j++){
                            tour.getOutboundStopActivities().add(factory.createActivityFromCoord(Tour.UNDEFINED_ACTIVITY_TYPE, null));
                        }

                                                }
                     for (int i=0; i<noOtherTours; i++){
                         Tour tour = new Tour(Tour.TourType.SECONDARY, Tour.UNDEFINED_ACTIVITY_TYPE);
                         tours.add(tour);
                         int stopsInbound = stopFrequencyOtherModel.calculate(person);
                         int stopsOutbound = stopFrequencyOtherModel.calculate(person);
                         for (int j=0; j<stopsInbound; j++){
                             tour.getInboundStopActivities().add(factory.createActivityFromCoord(Tour.UNDEFINED_ACTIVITY_TYPE, null));
                         }
                         for (int j=0; j<stopsOutbound; j++){
                             tour.getOutboundStopActivities().add(factory.createActivityFromCoord(Tour.UNDEFINED_ACTIVITY_TYPE, null));
                         }

                     }


                }

                );

    }
}
