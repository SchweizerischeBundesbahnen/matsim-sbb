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

package ch.sbb.matsim.utils;

import ch.sbb.matsim.RunSBB;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.PrepareForMobsim;
import org.matsim.core.scenario.ScenarioUtils;

public class MobiPerformanceTest {

    public static void main(String[] args) {
        System.setProperty("matsim.preferLocalDtds", "true");

        final String configFile = args[0];

        final Config config = RunSBB.buildConfig(configFile);

        Scenario scenario = ScenarioUtils.loadScenario(config);
        RunSBB.addSBBDefaultScenarioModules(scenario);
        ScenarioConsistencyChecker.checkScenarioConsistency(scenario);
        // controler
        Controler controler = new Controler(scenario);
        RunSBB.addSBBDefaultControlerModules(controler);
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                bind(PrepareForMobsim.class).to(MobiPerformancesTestPrepareForMobsim.class);
            }
        });
        controler.run();

    }

}
