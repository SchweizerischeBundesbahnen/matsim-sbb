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

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

public class CompareSchedules {

    public static void main(String[] args) {

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Scenario scenario2 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(args[0]);
        new TransitScheduleReader(scenario2).readFile(args[1]);

        for (var l : scenario.getTransitSchedule().getTransitLines().values()) {
            var l2 = scenario2.getTransitSchedule().getTransitLines().get(l.getId());
            if (l2 != null) {
                for (var r : l.getRoutes().values()) {
                    var r2 = l2.getRoutes().get(r.getId());
                    if (r2 == null) {
                        System.out.println(r.getId() + " of line " + l.getId() + " does not exist in schedule 2");
                        System.out.println(l2.getRoutes().keySet());
                    }
                }
            } else {
                System.out.println(l.getId() + " does not exist in schedule 2");
            }
        }
    }

}
