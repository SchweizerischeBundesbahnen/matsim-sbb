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

package ch.sbb.matsim.preparation.PopulationSampler;

import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import java.util.Map;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

public class IncludeLiechtensteinGrowth {

    public static void main(String[] args) {
        Map<String, Double> growthRate = Map.of("2030", 1.038, "2040", 1.074, "2050", 1.137);
        String baseplans = "\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_2.1\\plans_exogenous\\cb_road\\20190509\\100pct\\plans.xml.gz";
        String outdir = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20210407_Prognose_2050\\2050\\plans_exogeneous\\make_plans\\road\\Liechtenstein\\";
        String zonesFile = "\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_3.2\\plans\\3.2_2017_100pct\\mobi-zones.shp";
        Zones zones = null;
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(baseplans);
        Scenario scenario1 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Population population = scenario1.getPopulation();

        for (var p : scenario.getPopulation().getPersons().values()) {
            var plan = p.getSelectedPlan();
            Activity act0 = (Activity) plan.getPlanElements().get(0);
            var act0zone = zones.findZone(act0.getCoord());
            Activity act1 = (Activity) plan.getPlanElements().get(2);
            var act1zone = zones.findZone(act1.getCoord());
            if ((isLiechtenstein(act0zone) && isSwiss(act1zone))) {
                population.addPerson(p);
            } else if ((isLiechtenstein(act1zone) && isSwiss(act0zone))) {
                population.addPerson(p);
            }
        }

        for (var e : growthRate.entrySet()) {
            int desiredPlans = (int) Math.ceil(e.getValue() * population.getPersons().size());
            System.out.println(desiredPlans + " " + e.getKey());
            Scenario scenario2 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
            //ScalePlans.scalePopulation(desiredPlans,population,scenario2.getPopulation());
            new PopulationWriter(scenario2.getPopulation()).write(outdir + "plans_" + e.getKey() + ".xml.gz");

        }
    }

    private static boolean isLiechtenstein(Zone zone) {
        if (zone == null) {
            return false;
        } else {
            Integer zoneNo = Integer.parseInt(zone.getId().toString());
            return (zoneNo >= 700101001 && zoneNo <= 701101001);
        }
    }

    private static boolean isSwiss(Zone zone) {
        if (zone == null) {
            return false;
        } else {
            Integer zoneNo = Integer.parseInt(zone.getId().toString());
            return (zoneNo < 700101001);
        }
    }
}
