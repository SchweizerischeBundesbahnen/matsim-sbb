package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.steps.csvmodels.BooleanSelectionModelFromCSV;
import jakarta.inject.Inject;
import org.matsim.api.core.v01.Scenario;

import java.nio.file.Path;
import java.nio.file.Paths;

public class WorkFromHomeModel implements ModelStep{


    private final Scenario scenario;
    private final MOBiPlansConfig config;
    private final BooleanSelectionModelFromCSV workFromHomeModel;

    @Inject
    public WorkFromHomeModel(Scenario scenario, MOBiPlansConfig config) {
        this.scenario = scenario;
        this.config = config;
        Path modelFolder = Paths.get(config.data().modelBaseDir()).resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_CHOICE_MODELS);
        this.workFromHomeModel = new BooleanSelectionModelFromCSV(modelFolder.resolve(MOBiPlansConfig.WORK_FROM_HOME_MODEL).toString(),getName());
        
    }


    @Override
    public String getName() {
        return MOBiPlansRunner.WORK_FROM_HOME;
    }

    @Override
    public void run() {

    }
}
