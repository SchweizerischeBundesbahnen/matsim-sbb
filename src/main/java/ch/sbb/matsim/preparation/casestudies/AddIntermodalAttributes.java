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

package ch.sbb.matsim.preparation.casestudies;

import ch.sbb.matsim.intermodal.IntermodalModule;
import java.io.File;
import java.net.MalformedURLException;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

public class AddIntermodalAttributes {

    public static void main(String[] args) throws MalformedURLException {
        String inputPlansFile = args[0];
        String inputCSVFile = args[1];
        String outputPlans = args[2];
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(inputPlansFile);
        IntermodalModule.preparePopulation(scenario.getPopulation(), new File(inputCSVFile).toURI().toURL());
        new PopulationWriter(scenario.getPopulation()).write(outputPlans);

    }

}
