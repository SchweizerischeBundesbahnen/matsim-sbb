package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.steps.csvmodels.ASCFittingToTargets;
import ch.sbb.matsim.plans.models.steps.csvmodels.BooleanSelectionModelFromCSV;
import ch.sbb.matsim.plans.models.utils.AttributeUtils;
import jakarta.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.facilities.ActivityFacility;
import org.matsim.households.Household;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class WorkFromHomeModel implements ModelStep {


    private final Scenario scenario;
    private final MOBiPlansConfig config;
    private final BooleanSelectionModelFromCSV workFromHomeModel;
    private final BooleanSelectionModelFromCSV workFlexibleModel;
    private final static Logger logger = LogManager.getLogger(WorkFromHomeModel.class);

    @Inject
    public WorkFromHomeModel(Scenario scenario, MOBiPlansConfig config) {
        this.scenario = scenario;
        this.config = config;
        Path modelFolder = Paths.get(config.data().modelBaseDir()).resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_CHOICE_MODELS);
        this.workFromHomeModel = new BooleanSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.WORK_FROM_HOME_MODEL_CSV).toString(), getName());
        this.workFlexibleModel = new BooleanSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.FLEXIBLE_WORKING_HOURS_CSV).toString(), getName());

    }


    @Override
    public String getName() {
        return MOBiPlansRunner.WORK_FROM_HOME;
    }

    @Override
    public void run() {
        List<Person> personsToConsider = new ArrayList<>();
        scenario.getPopulation().getPersons().values().stream().filter(p -> AttributeUtils.getAsInteger(p, SynpopConstants.LEVEL_OF_EMPLOYMENT, 0) > 0).filter(p -> p.getAttributes().getAttribute(SynpopConstants.WORK_FACILITY) != null).forEach(person -> {
            person.getAttributes().putAttribute(SynpopConstants.ORIGINAL_WORK_ZONE, person.getAttributes().getAttribute(SynpopConstants.WORK_ZONE));
            person.getAttributes().putAttribute(SynpopConstants.ORIGINAL_WORK_FACILITY, person.getAttributes().getAttribute(SynpopConstants.WORK_FACILITY));
            personsToConsider.add(person);
        });
        int totalWorkForce = personsToConsider.size();
        int workingFromHome = (int) Math.floor(totalWorkForce * config.models().wfh_rate());
        int flexibleWorkingHours = (int) Math.floor(totalWorkForce * config.models().work_flexible_rate());
        logger.info("Total workforce: " + totalWorkForce);
        logger.info("Target working from home population: " + workingFromHome);
        logger.info("Target flexible working hours population: " + flexibleWorkingHours);
        ASCFittingToTargets.fitAndAssignTarget(personsToConsider, workingFromHome, workFromHomeModel, SynpopConstants.IS_WORKING_FROM_HOME, "true");
        ASCFittingToTargets.fitAndAssignTarget(personsToConsider, flexibleWorkingHours, workFlexibleModel, SynpopConstants.HAS_FLEXIBLE_WORK_HOURS, "true");
        personsToConsider.stream().filter(person -> Boolean.TRUE.equals(person.getAttributes().getAttribute(SynpopConstants.IS_WORKING_FROM_HOME))).forEach(person -> {
            person.getAttributes().putAttribute(SynpopConstants.WORK_ZONE, person.getAttributes().getAttribute(SynpopConstants.ZONE));
            Household household = (Household) person.getAttributes().getAttribute(SynpopConstants.HOUSEHOLD);
            var homeFacility = ((ActivityFacility) household.getAttributes().getAttribute(SynpopConstants.FACILITY));
            person.getAttributes().putAttribute(SynpopConstants.WORK_FACILITY, homeFacility);
        });
        long wfhPopulation = personsToConsider.stream().filter(person -> Boolean.TRUE.equals(person.getAttributes().getAttribute(SynpopConstants.IS_WORKING_FROM_HOME))).count();
        long flexibleWorkingHoursPopulation = personsToConsider.stream().filter(person -> Boolean.TRUE.equals(person.getAttributes().getAttribute(SynpopConstants.HAS_FLEXIBLE_WORK_HOURS))).count();
        logger.info("Actual working from home population: " + wfhPopulation);
        logger.info("Actual flexible working hours population: " + flexibleWorkingHoursPopulation);
    }

}

