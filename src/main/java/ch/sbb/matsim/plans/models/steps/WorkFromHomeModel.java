package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.steps.csvmodels.BooleanSelectionModelFromCSV;
import ch.sbb.matsim.plans.models.utils.AttributeUtils;
import jakarta.inject.Inject;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.utils.objectattributes.attributable.AttributesUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class WorkFromHomeModel implements ModelStep{


    private final Scenario scenario;
    private final MOBiPlansConfig config;
    private final BooleanSelectionModelFromCSV workFromHomeModel;
    private final BooleanSelectionModelFromCSV workFlexibleModel;

    @Inject
    public WorkFromHomeModel(Scenario scenario, MOBiPlansConfig config) {
        this.scenario = scenario;
        this.config = config;
        Path modelFolder = Paths.get(config.data().modelBaseDir()).resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_CHOICE_MODELS);
        this.workFromHomeModel = new BooleanSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.WORK_FROM_HOME_MODEL_CSV).toString(),getName());
        this.workFlexibleModel = new BooleanSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.FLEXIBLE_WORKING_HOURS_CSV).toString(),getName());

    }


    @Override
    public String getName() {
        return MOBiPlansRunner.WORK_FROM_HOME;
    }

    @Override
    public void run() {
        List<Person> personsToConsider = new ArrayList<>();
        scenario.getPopulation().getPersons().values().stream()
                .filter(p -> AttributeUtils.getAsInteger(p, SynpopConstants.LEVEL_OF_EMPLOYMENT,0) > 0)
                .filter(p -> p.getAttributes().getAttribute(SynpopConstants.WORK_FACILITY) != null)
                .forEach(person ->
                {
                    person.getAttributes().putAttribute(SynpopConstants.ORIGINAL_WORK_ZONE,  person.getAttributes().getAttribute(SynpopConstants.WORK_ZONE));
                    person.getAttributes().putAttribute(SynpopConstants.ORIGINAL_WORK_FACILITY,  person.getAttributes().getAttribute(SynpopConstants.WORK_FACILITY));
                    personsToConsider.add(person);
                    });
        int totalWorkForce = personsToConsider.size();
        int workingFromHome = (int) Math.floor( totalWorkForce * config.models().wfh_rate());
        int flexibleWorkingHours = (int) Math.floor( totalWorkForce * config.models().work_flexible_rate());

    }
}
