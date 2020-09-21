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

import ch.sbb.matsim.analysis.PopulationToCSV;
import ch.sbb.matsim.csv.CSVWriter;
import java.io.IOException;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

public class CreateAgentsCsv {

    public static void main(String[] args) {

        String atts = "subpopulation,pt_subscr,car_available,residence_msr_id,age_cat,current_edu,level_of_employment_cat,residence_zone_id";
        String[] attributes = atts.split(",");
        String agentsFilename = "\\\\k13536\\mobi\\40_Projekte\\20200330_MOBi_3.0\\sim\\2.9.x\\2.9.99\\prepared\\agents_with_residence_zone_id.csv.gz";
        String popFileName = "\\\\k13536\\mobi\\40_Projekte\\20200330_MOBi_3.0\\sim\\2.9.x\\2.9.99\\prepared\\plans.xml.gz";
        try (CSVWriter agentsWriter = new CSVWriter("", PopulationToCSV.getColumns(attributes), agentsFilename)) {
            StreamingPopulationReader spr = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
            spr.addAlgorithm(person ->
            {
                agentsWriter.set("person_id", person.getId().toString());
                for (String attribute_name : attributes) {
                    Object attribute;
                    attribute = person.getAttributes().getAttribute(attribute_name);

                    if (attribute != null) {
                        agentsWriter.set(attribute_name, attribute.toString());
                    }
                }
                agentsWriter.writeRow();

            });
            spr.readFile(popFileName);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
