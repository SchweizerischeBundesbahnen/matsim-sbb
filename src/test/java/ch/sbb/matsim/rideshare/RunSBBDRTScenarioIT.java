package ch.sbb.matsim.rideshare;

import org.junit.Test;

public class RunSBBDRTScenarioIT {

	@Test
	public void testDRTScenario() {
		RunSBBDRTScenario.main(new String[]{"test/input/scenarios/mobi31test/drtconfig.xml"});

	}
}