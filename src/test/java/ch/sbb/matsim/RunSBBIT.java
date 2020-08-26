package ch.sbb.matsim;

import org.junit.Test;
import org.matsim.core.config.Config;

/**
 * An integration test to see if the Mobi Scenario is running.
 */
public class RunSBBIT {

	@Test
	public void qsimIT() {
		System.setProperty("matsim.preferLocalDtds", "true");
		Config config = RunSBB.buildConfig("test/input/scenarios/mobi20test/testconfig.xml");
		config.controler().setMobsim("qsim");
		config.controler().setCreateGraphs(true);
		RunSBB.run(config);

	}

	@Test
	public void interModalIT() {
		RunSBB.main(new String[]{"test/input/scenarios/mobi20test/intermodal_testconfig.xml"});

	}
}