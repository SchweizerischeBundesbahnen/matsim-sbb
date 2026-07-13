package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.selector.LinearRegressionModel;
import jakarta.inject.Inject;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Population;

import java.nio.file.Paths;

public class PedestrianSpeedModel implements ModelStep{

    private final Population population;
    private final LinearRegressionModel pedestrianSpeedModel;
    @Inject
    public PedestrianSpeedModel(Scenario scenario, MOBiPlansConfig config){
        this.population = scenario.getPopulation();
        this.pedestrianSpeedModel = LinearRegressionModel.createFromCSV(Paths.get(config.data().modelBaseDir()).resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_CHOICE_MODELS).resolve(MOBiPlansConfig.PEDESTRIAN_SPEED_CSV).toString());
    }

    @Override
    public String getName() {
        return MOBiPlansRunner.PEDESTRIAN_SPEED;
    }

    @Override
    public void run() {
        population.getPersons().values()
                .stream()
                .filter(person -> new PopulationSampler.ActivePersonPredicate().test(person))
                .forEach(person -> {
            double walkSpeed = pedestrianSpeedModel.calculate(person) / 3.6;
            person.getAttributes().putAttribute(SynpopConstants.PEDESTRIAN_SPEED,walkSpeed);
        });
    }
}
