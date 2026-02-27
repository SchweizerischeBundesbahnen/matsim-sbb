package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.steps.ModelStep;
import ch.sbb.matsim.plans.synpop.loading.SynpopLoader;
import com.google.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;

public class ZoneBasedAccessibilities implements ModelStep{
    private final String name = MOBiPlansRunner.CALCULATE_ACCESSIBILITIES;
    private final static Logger logger = LogManager.getLogger(SynpopLoader.class);
    private final MOBiPlansConfig config;
    @Inject
    public ZoneBasedAccessibilities(MOBiPlansConfig config, Scenario scenario) {
        this.config = config;
    }

    public void run() {

    }

    public String getName() {
        return name;
    }
}
