package ch.sbb.matsim;

import ch.ethz.matsim.discrete_mode_choice.model.DiscreteModeChoiceModel;
import ch.ethz.matsim.discrete_mode_choice.model.tour_based.TourEstimator;
import ch.ethz.matsim.discrete_mode_choice.modules.*;
import ch.ethz.matsim.discrete_mode_choice.modules.config.DiscreteModeChoiceConfigGroup;
import ch.ethz.matsim.discrete_mode_choice.modules.config.ModeChainFilterRandomThresholdConfigGroup;
import com.sun.xml.internal.ws.message.stream.StreamAttachment;
import org.junit.Test;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.utils.collections.CollectionUtils;

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

}