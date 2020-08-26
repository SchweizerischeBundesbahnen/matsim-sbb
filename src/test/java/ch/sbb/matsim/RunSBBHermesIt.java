/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2020 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package ch.sbb.matsim;

import org.junit.Test;
import org.matsim.core.config.Config;

public class RunSBBHermesIt {

	@Test
	public void hermesIT() {
		System.setProperty("matsim.preferLocalDtds", "true");
		Config config = RunSBB.buildConfig("test/input/scenarios/mobi20test/testconfig.xml");
		config.controler().setMobsim("hermes");
		config.controler().setRunId("hermesit");
		config.controler().setCreateGraphs(true);
		config.controler().setOutputDirectory("test/output/hermesIt");
		RunSBB.run(config);

	}
}
