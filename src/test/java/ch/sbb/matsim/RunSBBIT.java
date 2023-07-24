package ch.sbb.matsim;

import org.junit.Test;
import org.matsim.core.config.Config;

/**
 * An integration test to see if the Mobi Scenario is running.
 */
public class RunSBBIT {


	@Test
	public void runSBBIt() {
		System.setProperty("matsim.preferLocalDtds", "true");
		Config config = RunSBB.buildConfig("test/input/scenarios/mobi31test/config.xml");
		config.planCalcScore().setWriteExperiencedPlans(true);
		RunSBB.run(config);

    }
}