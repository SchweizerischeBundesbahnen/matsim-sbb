package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.projects.synpop.SingleTripAgentToCSVConverter;
import jakarta.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.File;

public class MergeExogenousPlans implements ModelStep {

    private final MOBiPlansConfig config;
    private final Scenario scenario;
    private final Logger logger = LogManager.getLogger(MergeExogenousPlans.class);


    @Inject
    public MergeExogenousPlans(MOBiPlansConfig config, Scenario scenario) {
        this.config = config;
        this.scenario = scenario;
    }

    @Override
    public String getName() {
        return MOBiPlansRunner.MERGE_EXOGENOUS_PLANS;
    }

    @Override
    public void run() {
        if (config.data().exogenousPlansFolder() == null) {
            logger.warn("No exogenous plans folder specified. Skipping merging exogenous plans.");
            return;
        }
        File exogenousPlansFolder = new File(config.data().exogenousPlansFolder());
        if (exogenousPlansFolder.exists() && exogenousPlansFolder.isDirectory()) {
            File[] planFiles = exogenousPlansFolder.listFiles();
            if (planFiles != null) {
                for (File planFile : planFiles) {
                    if (planFile.isFile() && (planFile.getName().endsWith(".xml") || planFile.getName().endsWith(".xml.gz") || planFile.getName().endsWith(".xml.zst"))) {
                        handlePlansFile(planFile);
                    } else if (planFile.isFile() && (planFile.getName().endsWith(".csv") || planFile.getName().endsWith(".csv.gz")) || planFile.getName().endsWith(".csv.zst")) {
                        handleCSVFile(planFile);
                    }

                }
            }
        }
    }

    private void handleCSVFile(File planFile) {
        Scenario newScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        try {
            SingleTripAgentToCSVConverter.readCSVAndGetPlans(planFile.toString(), newScenario.getPopulation());
        } catch (Exception e) {
            logger.error("Population file could not be read {} and is skipped.", planFile);
            e.printStackTrace();
        }
        checkPopulationAndAddToScenario(planFile, newScenario);

    }

    private void handlePlansFile(File planFile) {
        Scenario newScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        try {
            new PopulationReader(newScenario).readFile(planFile.toString());
        } catch (Exception e) {
            logger.error("Population file could not be read {} and is skipped.", planFile);
            e.printStackTrace();
        }
        checkPopulationAndAddToScenario(planFile, newScenario);

    }

    private void checkPopulationAndAddToScenario(File planFile, Scenario newScenario) {
        boolean subpopulationSet = newScenario.getPopulation().getPersons().values().stream().noneMatch(person -> null == PopulationUtils.getSubpopulation(person));
        boolean idNotExisting = newScenario.getPopulation().getPersons().values().stream().noneMatch(person -> scenario.getPopulation().getPersons().containsKey(person.getId()));
        if (subpopulationSet && idNotExisting) {
            int slice = 0;
            for (Person person : newScenario.getPopulation().getPersons().values()) {
                scenario.getPopulation().addPerson(person);
                person.getAttributes().putAttribute(Variables.SLICE, slice);
                slice++;
                if (slice == 200) {
                    slice = 0;
                }
            }
        } else {
            if (!subpopulationSet) {
                logger.error("Subpopulation attribute is missing in {} Contents from this file is skipped.", planFile);
            }
            if (!idNotExisting) {
                logger.error("Some person IDs are already set in the scenario and are duplicates in {}. Contents from this file is skipped.", planFile);
            }
        }
    }
}
