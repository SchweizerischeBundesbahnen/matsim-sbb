package ch.sbb.matsim.plans;

import ch.sbb.matsim.plans.models.steps.ModelStep;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.gbl.Gbl;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class MOBiPlansRunner implements Runnable{
    private static final Logger logger = LogManager.getLogger(MOBiPlansRunner.class);

    public static final String LOAD_SYNPOP = "load_synpop";
    public static final String CALC_ZONAL_ATTRIBUTES = "calc_zonal_attributes";
    public static final String ZONE_BASED_ACCESSIBILITIES = "zone_based_accessibilities";
    public static final String MOBILITY_TOOL_OWNERSHIP_MODEL = "mobility_tool_ownership_model";
    public static final String PRIMARY_LOCATION_CHOICE = "primary_location_choice_model";
    public static final String ITERATTIVE_DAILY_PLAN_GENERATION = "iterative_daily_plan_generation";
    public static final String WRITE_OUTPUTS = "write_outputs";
    public static final String POPULATION_SAMPLING = "population_sampling";
    public static final String INITIAL_MODE_CHOICE = "initial_mode_choice";
    public static final String START_TIME_CHOICE = "start_time_choice";



    public static final List<String> FULL_MODEL_SEQUENCE = List.of(
            LOAD_SYNPOP,
            CALC_ZONAL_ATTRIBUTES,
            ZONE_BASED_ACCESSIBILITIES,
            MOBILITY_TOOL_OWNERSHIP_MODEL,
            POPULATION_SAMPLING,
            PRIMARY_LOCATION_CHOICE,
            ITERATTIVE_DAILY_PLAN_GENERATION,
            INITIAL_MODE_CHOICE,
            START_TIME_CHOICE,
            WRITE_OUTPUTS
    );
    private final MOBiPlansConfig config;

    private final Injector injector; // The ultimate factory
    private final Map<String, Class<? extends ModelStep>> availableSteps;

    @Inject
    public MOBiPlansRunner(
            MOBiPlansConfig config,
            Injector injector,
            // Guice injects the Set of all bound ModelStep classes from the Multibinder
            Set<ModelStep> allModelSteps
    ) {
        this.config = config;
        this.injector = injector;

        // --- Build a lookup map from the model's name to its Class ---
        // This is done once at startup. It's very fast.
        this.availableSteps = allModelSteps.stream()
                .collect(Collectors.toMap(ModelStep::getName, ModelStep::getClass));
    }

    @Override
    public void run() {
        logger.info("Starting model pipeline execution...");
        Instant startPipeline = Instant.now();

        List<String> modelsToRun = resolveModelsToRun();

        logger.info("Execution plan: {}", modelsToRun);

        for (String modelKey : modelsToRun) {
            // Find the class associated with the key
            Class<? extends ModelStep> modelClass = availableSteps.get(modelKey);

            if (modelClass != null) {
                logger.info("--- [START] Running model: '{}' ---", modelKey);
                Gbl.printMemoryUsage();
                Instant startTask = Instant.now();
                // Ask the injector to create an instance of this specific class, right now.
                // This is lazy loading. The object is only created if it's in the run list.
                ModelStep task = injector.getInstance(modelClass);
                task.run();

                logger.info("--- [END] Finished model: '{}'. Took {} ---", modelKey, Duration.between(startTask, Instant.now()));
            } else {
                logger.warn("Unknown model key in 'run' configuration: '{}'. Skipping.", modelKey);
            }
        }

        logger.info("Model pipeline finished in {}.", Duration.between(startPipeline, Instant.now()));
    }

    private List<String> resolveModelsToRun() {
        Object runConfig = config.models().run();

        if (runConfig instanceof String runAlias) {
            if ("full_model".equalsIgnoreCase(runAlias)) {
                // The alias "full_model" maps to our hard-coded full sequence.
                return FULL_MODEL_SEQUENCE;
            } else {
                // A single string can also mean "run only this one model"
                return List.of(runAlias);
            }
        } else if (runConfig instanceof List) {
            // If the config provides a list, use it directly.
            // We need to cast it, which is safe because of how Jackson works.
            @SuppressWarnings("unchecked")
            List<String> customRun = (List<String>) runConfig;
            return customRun;
        } else {
            throw new IllegalArgumentException("The 'run' configuration in your YAML must be the string 'full_model' or a list of model keys.");
        }
}
}
