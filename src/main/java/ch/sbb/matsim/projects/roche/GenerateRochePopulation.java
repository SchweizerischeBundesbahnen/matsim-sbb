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

package ch.sbb.matsim.projects.roche;

import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.misc.Time;
import org.matsim.facilities.ActivityFacility;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GenerateRochePopulation {

    public static void main(String[] args) throws IOException {

        String csv = args[0];
        String outputPlans = args[1];
        String zonesFile = args[2];
        Zones zones = ZonesLoader.loadZones("zones", zonesFile, Variables.ZONE_ID);
        CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, TransformationFactory.CH1903_LV03_Plus);
        try (CSVReader csvReader = new CSVReader(csv, ";")) {
            Id<ActivityFacility> workFacilityId = Id.create("1743750", ActivityFacility.class);
            Coord workcoord = new Coord(2675437.0, 1222897.0);
            Scenario scenarioTuesday = ScenarioUtils.createScenario(ConfigUtils.createConfig());
            Scenario scenarioFriday = ScenarioUtils.createScenario(ConfigUtils.createConfig());
            var entry = csvReader.readLine();
            var fac = scenarioTuesday.getPopulation().getFactory();
            while (entry != null) {
                Id<Person> personId = Id.createPersonId("r_" + entry.get("person_id"));
                String tuesdayMode = entry.get("mode_tuesday");
                String fridayMode = entry.get("mode_friday");
                int car_available = Integer.parseInt(entry.get("car_available"));
                String level_of_employment_cat = entry.get("level_of_employment_cat");
                double homex = Double.parseDouble(entry.get("home_x"));
                double homey = Double.parseDouble(entry.get("home_y"));
                Coord homeCoord = transformation.transform(new Coord(homex, homey));
                double work_start = Time.parseTime(entry.get("work_start"));
                double work_end = Time.parseTime(entry.get("work_end"));
                String hh_child_lift = entry.get("hh_child_lift");
                String weight = entry.get("weight");
                String age_cat = entry.get("age_cat");
                String current_edu = "null";
                String pt_subscr = entry.get("pt_subscr");
                String car2pt = entry.get("car2pt");
                String bike2pt = entry.get("bike2pt");
                String car2pt_act = car2pt.equals("1") ? SBBActivities.work : "";
                String bike2pt_act = bike2pt.equals("1") ? SBBActivities.work : "";
                if (tuesdayMode.equals(SBBModes.RIDE)) {
                    tuesdayMode = SBBModes.CAR;
                }
                if (fridayMode.equals(SBBModes.RIDE)) {
                    fridayMode = SBBModes.CAR;
                }
                if (work_end - work_start < 0) {
                    throw new RuntimeException(personId + " has work end before start");
                }
                if (car_available == 0) {
                    if (tuesdayMode.equals(SBBModes.CAR)) {
                        System.out.println(personId + " has no car parking, but uses car mode on Tuesdays.");
                        car_available = 1;
                    }
                    if (fridayMode.equals(SBBModes.CAR)) {
                        System.out.println(personId + " has no car parking, but uses car mode on Fridays.");
                        car_available = 1;
                    }
                }

                //als Zeichen allgemeiner oev-affinitaet
                if (!pt_subscr.equals("")) {
                    pt_subscr = "HTA";
                }
                if (tuesdayMode.equals(SBBModes.PT) && fridayMode.equals(SBBModes.PT)) {
                    pt_subscr = "HTA";
                }
                Zone homezone = zones.findZone(homeCoord);
                Integer residence_zone_id = Integer.parseInt(Variables.DEFAULT_OUTSIDE_ZONE);
                Integer residence_msr_id = -1;
                if (homezone != null) {
                    residence_zone_id = Integer.parseInt(homezone.getId().toString());
                    residence_msr_id = Integer.parseInt(homezone.getAttribute("msr_id").toString());
                }
                List<Person> persons = new ArrayList<>();
                if (!tuesdayMode.equals("none")) {
                    Person person = fac.createPerson(personId);
                    scenarioTuesday.getPopulation().addPerson(person);
                    persons.add(person);

                    createPlan(workFacilityId, workcoord, fac, tuesdayMode, homeCoord, work_start, work_end, person);

                }
                if (!fridayMode.equals("none")) {
                    Person person = fac.createPerson(personId);
                    scenarioFriday.getPopulation().addPerson(person);
                    persons.add(person);
                    createPlan(workFacilityId, workcoord, fac, fridayMode, homeCoord, work_start, work_end, person);
                }

                for (var person : persons) {

                    person.getAttributes().putAttribute(Variables.CAR_AVAIL, car_available);
                    person.getAttributes().putAttribute("level_of_employment_cat", level_of_employment_cat);
                    person.getAttributes().putAttribute("hh_child_lift", hh_child_lift);
                    person.getAttributes().putAttribute("weight", weight);
                    person.getAttributes().putAttribute("subpopulation", Variables.REGULAR);
                    person.getAttributes().putAttribute("residence_zone_id", residence_zone_id);
                    person.getAttributes().putAttribute("residence_msr_id", residence_msr_id);
                    person.getAttributes().putAttribute("pt_subscr", pt_subscr);
                    person.getAttributes().putAttribute("age_cat", age_cat);
                    person.getAttributes().putAttribute("current_edu", current_edu);

                    person.getAttributes().putAttribute("tuesday_mode", tuesdayMode);
                    person.getAttributes().putAttribute("friday_mode", fridayMode);
                    person.getAttributes().putAttribute("bike2pt", bike2pt);
                    person.getAttributes().putAttribute("bike2pt_act", bike2pt_act);
                    person.getAttributes().putAttribute("car2pt_act", car2pt_act);
                    person.getAttributes().putAttribute("car2pt", car2pt);

                }

                entry = csvReader.readLine();
            }


            new PopulationWriter(scenarioFriday.getPopulation()).write(outputPlans + "_friday.xml");
            new PopulationWriter(scenarioTuesday.getPopulation()).write(outputPlans + "_tuesday.xml");
        }
    }

    public static void createPlan(Id<ActivityFacility> workFacilityId, Coord workcoord, PopulationFactory fac, String mode, Coord homeCoord, double work_start,
            double work_end, Person person) {
        double traveltimeEst = 600 + CoordUtils.calcEuclideanDistance(homeCoord, workcoord) / 10;
        if (mode.equals(SBBModes.WALK_FOR_ANALYSIS)) {
            mode = SBBModes.WALK_MAIN_MAINMODE;
        }
        Leg workleg = fac.createLeg(mode);
        Leg homeleg = fac.createLeg(mode);
        Activity home1 = fac.createActivityFromCoord(SBBActivities.home, homeCoord);

        home1.setEndTime(Math.max(0, work_start - traveltimeEst));
        Activity work = fac.createActivityFromActivityFacilityId(SBBActivities.work, workFacilityId);
        work.setCoord(workcoord);
        work.setEndTime(work_end);
        work.setStartTime(work_start);
        Activity home2 = fac.createActivityFromCoord(SBBActivities.home, homeCoord);
        home2.setStartTime(Math.min(work_end + traveltimeEst, 24.0 * 3600));
        Plan plan = fac.createPlan();
        person.addPlan(plan);
        plan.addActivity(home1);
        plan.addLeg(workleg);
        plan.addActivity(work);
        plan.addLeg(homeleg);
        plan.addActivity(home2);
    }
}
