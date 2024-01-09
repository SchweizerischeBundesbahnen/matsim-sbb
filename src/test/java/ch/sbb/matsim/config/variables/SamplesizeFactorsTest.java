package ch.sbb.matsim.config.variables;

import ch.sbb.matsim.config.PostProcessingConfigGroup;
import org.junit.Assert;
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

            Assert.assertEquals(0.1075, config.hermes().getFlowCapacityFactor(), 0.001);
            Assert.assertEquals(0.20, config.hermes().getStorageCapacityFactor(), 0.001);
            Assert.assertEquals(0.10, config.qsim().getFlowCapFactor(), 0.001);
            Assert.assertEquals(0.40, config.qsim().getStorageCapFactor(), 0.001);
        }

        {
            Config config = ConfigUtils.createConfig();
            PostProcessingConfigGroup pp = new PostProcessingConfigGroup();
            pp.setSimulationSampleSize(1.0);
            config.addModule(pp);
            SamplesizeFactors.setFlowAndStorageCapacities(config);

            Assert.assertEquals(1.0, config.hermes().getFlowCapacityFactor(), 0.001);
            Assert.assertEquals(1.0, config.hermes().getStorageCapacityFactor(), 0.001);
            Assert.assertEquals(1.0, config.qsim().getFlowCapFactor(), 0.001);
            Assert.assertEquals(1.0, config.qsim().getStorageCapFactor(), 0.001);
        }

    }
}