package ch.sbb.matsim.plans.models.steps;


import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.steps.csvmodels.IntegerSelectionModelFromCSV;
import jakarta.inject.Inject;
import org.matsim.api.core.v01.Scenario;

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
        scenario.getPopulation().getPersons().values()
                .parallelStream()
                .peek(person -> person.getAttributes().putAttribute(SynpopConstants.NO_WORK_TOURS,0))
                .filter(person -> ((Number) person.getAttributes().getAttribute(SynpopConstants.LEVEL_OF_EMPLOYMENT)).intValue()>0)
                .forEach(person -> person.getAttributes().putAttribute(SynpopConstants.NO_WORK_TOURS,numberOfWorkToursModel.calculate(person)));

        //Number of education tours, anyone in education
        scenario.getPopulation().getPersons().values()
                .parallelStream()
                .peek(person -> person.getAttributes().putAttribute(SynpopConstants.NO_EDUCATION_TOURS,0))
                .filter(person -> ((Number) person.getAttributes().getAttribute(SynpopConstants.CURRENT_EDU)).intValue()>0)
                .forEach(person -> person.getAttributes().putAttribute(SynpopConstants.NO_EDUCATION_TOURS,numberOfEducationToursModel.calculate(person)));

        //Number of business tours, working population
        scenario.getPopulation().getPersons().values()
                .parallelStream()
                .peek(person -> person.getAttributes().putAttribute(SynpopConstants.NO_BUSINESS_TOURS,0))
                .filter(person -> ((Number) person.getAttributes().getAttribute(SynpopConstants.LEVEL_OF_EMPLOYMENT)).intValue()>0)
                .forEach(person -> person.getAttributes().putAttribute(SynpopConstants.NO_BUSINESS_TOURS,numberOfBusinessToursModel.calculate(person)));

        //Number of other tours, for everyone
        scenario.getPopulation().getPersons().values()
                .parallelStream()
                .forEach(person -> person.getAttributes().putAttribute(SynpopConstants.NO_OTHER_TOURS,numberOfOtherToursModel.calculate(person)));


    }
}
