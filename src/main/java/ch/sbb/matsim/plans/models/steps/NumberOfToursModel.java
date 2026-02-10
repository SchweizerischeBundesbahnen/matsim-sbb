package ch.sbb.matsim.plans.models.steps;


import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.constants.ZonalAttributes;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.steps.csvmodels.IntegerSelectionModelFromCSV;
import ch.sbb.matsim.zones.Zone;
import jakarta.inject.Inject;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Population;

public class NumberOfToursModel implements ModelStep{

    private final Scenario scenario;
    private final MOBiPlansConfig mobiPlansConfig;
    private final IntegerSelectionModelFromCSV numberOfWorkToursModel;
    private final IntegerSelectionModelFromCSV numberOfEducationToursModel;
    private final IntegerSelectionModelFromCSV numberOfOtherToursModel;
    private final IntegerSelectionModelFromCSV numberOfBusinessToursModel;

    @Inject
           public NumberOfToursModel(Scenario scenario, MOBiPlansConfig mobiPlansConfig) {
        this.scenario = scenario;
        this.mobiPlansConfig = mobiPlansConfig;
        String modelFolder = mobiPlansConfig.data().modelBaseDir()+"/";
        numberOfWorkToursModel = new IntegerSelectionModelFromCSV(modelFolder + MOBiPlansConfig.NUMBER_OF_WORK_TOURS_CSV);
        numberOfEducationToursModel = new IntegerSelectionModelFromCSV(modelFolder + MOBiPlansConfig.NUMBER_OF_EDUCATION_TOURS_CSV);
        numberOfOtherToursModel = new IntegerSelectionModelFromCSV(modelFolder + MOBiPlansConfig.NUMBER_OF_OTHER_TOURS_CSV);
        numberOfBusinessToursModel = new IntegerSelectionModelFromCSV(modelFolder + MOBiPlansConfig.NUMBER_OF_BUSINESS_TOURS_CSV);

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







    }
}
