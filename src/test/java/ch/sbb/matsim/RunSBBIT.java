package ch.sbb.matsim;

import ch.ethz.matsim.discrete_mode_choice.model.DiscreteModeChoiceModel;
import ch.ethz.matsim.discrete_mode_choice.model.tour_based.TourEstimator;
import ch.ethz.matsim.discrete_mode_choice.modules.*;
import ch.ethz.matsim.discrete_mode_choice.modules.config.DiscreteModeChoiceConfigGroup;
import ch.ethz.matsim.discrete_mode_choice.modules.config.ModeChainFilterRandomThresholdConfigGroup;
import org.junit.Test;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.StrategyConfigGroup;

import java.util.Arrays;


/**
 * An integration test to see if the Mobi Scenario is running.
 */
public class RunSBBIT {

    @Test
    public void main() {
       
			RunSBB.main(new String[]{"test/input/scenarios/mobi20test/testconfig.xml"});

    }

    @Test
    public void interModalIT() {
        
			RunSBB.main(new String[]{"test/input/scenarios/mobi20test/intermodal_testconfig.xml"});

    }

    @Test
    public void dmcIT() {

        Config config = RunSBB.buildConfig("test/input/scenarios/mobi20test/testconfig.xml");
        config.strategy().setFractionOfIterationsToDisableInnovation(1.0);
        config.controler().setLastIteration(10);

        StrategyConfigGroup.StrategySettings dmcSS = new StrategyConfigGroup.StrategySettings();
        dmcSS.setDisableAfter(-1);
        dmcSS.setExePath(null);
        dmcSS.setStrategyName("DiscreteModeChoice");
        dmcSS.setSubpopulation("regular");
        dmcSS.setWeight(0.15);
        config.strategy().addStrategySettings(dmcSS);

        ModeChainFilterRandomThresholdConfigGroup mcfrtConfig = ConfigUtils.addOrGetModule(config, ModeChainFilterRandomThresholdConfigGroup.class);
        mcfrtConfig.setMaxChainsThreshold(1024);

        DiscreteModeChoiceConfigGroup dmcConfig = ConfigUtils.addOrGetModule(config, DiscreteModeChoiceConfigGroup.class);
        dmcConfig.setModelType(ModelModule.ModelType.Tour);
        dmcConfig.setSelector(SelectorModule.MULTINOMIAL_LOGIT);
        dmcConfig.setTourConstraints(Arrays.asList(ConstraintModule.VEHICLE_CONTINUITY, ConstraintModule.SUBTOUR_MODE));
        dmcConfig.setTourFinder(TourFinderModule.ACTIVITY_BASED);
        dmcConfig.setTourEstimator("SBBScoring");
        dmcConfig.setCachedModes(Arrays.asList(TransportMode.car, TransportMode.pt, TransportMode.ride, TransportMode.walk, TransportMode.bike));
        dmcConfig.setFallbackBehaviour(DiscreteModeChoiceModel.FallbackBehaviour.IGNORE_AGENT);

        ConfigGroup parSet = dmcConfig.createParameterSet("tourFinder:"+TourFinderModule.ACTIVITY_BASED);
        parSet.addParam("activityType", "home");
        dmcConfig.addParameterSet(parSet);

        RunSBB.run(config);

    }
}