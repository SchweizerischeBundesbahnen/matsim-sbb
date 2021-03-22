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

package ch.sbb.matsim.preparation;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.apache.commons.lang3.mutable.MutableInt;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

public class SimplePlansMerger {

    public static void main(String[] args) {
        String folder = args[0];

        File f = new File(folder);
        List<String> filestomerge = Arrays.stream(f.listFiles((file, s) -> s.endsWith(".xml"))).map(file -> file.getAbsolutePath()).collect(Collectors.toList());

        String outputfile = folder + "/plans.xml.gz";
        CoordinateTransformation coordinateTransformation = TransformationFactory.getCoordinateTransformation("EPSG:21781", "EPSG:2056");
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        final MutableInt y = new MutableInt();
        filestomerge.forEach(s -> {
            Scenario scenario2 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
            new PopulationReader(scenario2).readFile(s);
            for (Person person : scenario2.getPopulation().getPersons().values()) {
                Person p1 = PopulationUtils.getFactory().createPerson(Id.createPersonId(person.getId().toString() + "_" + y.intValue()));
                for (Plan plan : person.getPlans()) {
                //    TripStructureUtils.getActivities(plan, StageActivityHandling.StagesAsNormalActivities).stream().filter(activity -> activity.getCoord()!=null).forEach(a->a.setCoord(coordinateTransformation.transform(a.getCoord())));
                    p1.addPlan(plan);
                }
                for (Entry<String, Object> o : person.getAttributes().getAsMap().entrySet()) {
                    p1.getAttributes().putAttribute(o.getKey(), o.getValue());
                }
                scenario.getPopulation().addPerson(p1);
                y.increment();
            }

        });
        new PopulationWriter(scenario.getPopulation()).write(outputfile);
    }
}
