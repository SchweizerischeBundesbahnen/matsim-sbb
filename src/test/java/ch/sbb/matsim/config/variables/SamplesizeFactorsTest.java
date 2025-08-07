package ch.sbb.matsim.config.variables;

import ch.sbb.matsim.config.PostProcessingConfigGroup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;

public class SamplesizeFactorsTest {

    @Test
    public void setFlowAndStorageCapacities() {

        {
            Config config = ConfigUtils.createConfig();
            PostProcessingConfigGroup pp = new PostProcessingConfigGroup();
            pp.setSimulationSampleSize(0.1);
            config.addModule(pp);
            SamplesizeFactors.setFlowAndStorageCapacities(config);

            Assertions.assertEquals(0.11, config.hermes().getFlowCapacityFactor(), 0.001);
            Assertions.assertEquals(0.20, config.hermes().getStorageCapacityFactor(), 0.001);
            Assertions.assertEquals(0.1, config.qsim().getFlowCapFactor(), 0.001);
            Assertions.assertEquals(0.40, config.qsim().getStorageCapFactor(), 0.001);
        }

        {
            Config config = ConfigUtils.createConfig();
            PostProcessingConfigGroup pp = new PostProcessingConfigGroup();
            pp.setSimulationSampleSize(1.0);
            config.addModule(pp);
            SamplesizeFactors.setFlowAndStorageCapacities(config);

            Assertions.assertEquals(1.0, config.hermes().getFlowCapacityFactor(), 0.001);
            Assertions.assertEquals(1.0, config.hermes().getStorageCapacityFactor(), 0.001);
            Assertions.assertEquals(1.0, config.qsim().getFlowCapFactor(), 0.001);
            Assertions.assertEquals(1.0, config.qsim().getStorageCapFactor(), 0.001);
        }

    }
}