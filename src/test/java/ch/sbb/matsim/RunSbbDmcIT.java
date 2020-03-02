package ch.sbb.matsim;

import ch.ethz.matsim.discrete_mode_choice.model.DiscreteModeChoiceModel;
import ch.ethz.matsim.discrete_mode_choice.modules.*;
import ch.ethz.matsim.discrete_mode_choice.modules.config.DiscreteModeChoiceConfigGroup;
import ch.ethz.matsim.discrete_mode_choice.modules.config.ModeChainFilterRandomThresholdConfigGroup;
import ch.sbb.matsim.RunSBB;
import org.junit.Test;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.utils.collections.CollectionUtils;

import java.util.Arrays;

public class RunSbbDmcIT {

    @Test
    public void smcIT() {

        Config config = RunSBB.buildConfig("test/input/scenarios/mobi20test/testconfig.xml");
        config.strategy().setFractionOfIterationsToDisableInnovation(1.0);
        config.controler().setLastIteration(10);
        config.controler().setOutputDirectory("test/output/RunSbbDmcIT/smcIT");

        RunSBB.run(config);

    }

    @Test
    public void dmcIT() {

        Config config = RunSBB.buildConfig("test/input/scenarios/mobi20test/testconfig.xml");
        config.strategy().setFractionOfIterationsToDisableInnovation(1.0);
        config.controler().setLastIteration(10);
        config.controler().setOutputDirectory("test/output/RunSbbDmcIT/dmcIT");

        // remove SubtourModeChoice
        StrategyConfigGroup.StrategySettings smc = null;
        for (StrategyConfigGroup.StrategySettings ss : config.strategy().getStrategySettings()) {
            if (DefaultPlanStrategiesModule.DefaultStrategy.SubtourModeChoice.equals(ss.getStrategyName()) &&
                    "regular".equals(ss.getSubpopulation())) {
                smc = ss;
                break;
            }
        }
        config.strategy().removeParameterSet(smc);

        // add DiscreteModeChoice
        StrategyConfigGroup.StrategySettings dmcSS = new StrategyConfigGroup.StrategySettings();
        dmcSS.setDisableAfter(-1);
        dmcSS.setExePath(null);
        dmcSS.setStrategyName("DiscreteModeChoice");
        dmcSS.setSubpopulation("regular");
        dmcSS.setWeight(0.15);
        config.strategy().addStrategySettings(dmcSS);

        ModeChainFilterRandomThresholdConfigGroup mcfrtConfig = ConfigUtils.addOrGetModule(config, ModeChainFilterRandomThresholdConfigGroup.class);
        mcfrtConfig.setMaxChainsThreshold(8024);

        // setup DiscreteModeChoice config
        DiscreteModeChoiceConfigGroup dmcConfig = ConfigUtils.addOrGetModule(config, DiscreteModeChoiceConfigGroup.class);
        dmcConfig.setModelType(ModelModule.ModelType.Tour);
        dmcConfig.setSelector(SelectorModule.MULTINOMIAL_LOGIT);
        dmcConfig.setTourConstraints(Arrays.asList(ConstraintModule.VEHICLE_CONTINUITY, ConstraintModule.SUBTOUR_MODE));
        dmcConfig.setTourFinder(TourFinderModule.PLAN_BASED);
        dmcConfig.setTourEstimator("SBBScoring");
        dmcConfig.setCachedModes(Arrays.asList(TransportMode.car, TransportMode.pt, TransportMode.ride, TransportMode.walk, TransportMode.bike));
        dmcConfig.setModeChainGeneratorAsString(ModeChainGeneratorModule.FILTER_RANDOM_THRESHOLD);
        dmcConfig.setFallbackBehaviour(DiscreteModeChoiceModel.FallbackBehaviour.IGNORE_AGENT);

        ConfigGroup parSet = dmcConfig.createParameterSet("tourFinder:"+TourFinderModule.ACTIVITY_BASED);
        parSet.addParam("activityType", "home");
        dmcConfig.addParameterSet(parSet);
        ConfigGroup parSet2 = dmcConfig.createParameterSet("modeAvailability:Car");
        parSet2.addParam("availableModes", CollectionUtils.arrayToString(new String[] {
                TransportMode.car, TransportMode.pt, TransportMode.ride, TransportMode.walk, TransportMode.bike}));
        dmcConfig.addParameterSet(parSet2);

        RunSBB.run(config);

    }

    @Test
    public void nomcIT() {

        Config config = RunSBB.buildConfig("test/input/scenarios/mobi20test/testconfig.xml");
        config.strategy().setFractionOfIterationsToDisableInnovation(1.0);
        config.controler().setLastIteration(10);
        config.controler().setOutputDirectory("test/output/RunSbbDmcIT/nomcIT");

        // remove SubtourModeChoice
        StrategyConfigGroup.StrategySettings smc = null;
        for (StrategyConfigGroup.StrategySettings ss : config.strategy().getStrategySettings()) {
            if (DefaultPlanStrategiesModule.DefaultStrategy.SubtourModeChoice.equals(ss.getStrategyName()) &&
                    "regular".equals(ss.getSubpopulation())) {
                smc = ss;
                break;
            }
        }
        config.strategy().removeParameterSet(smc);

        RunSBB.run(config);

    }

}
