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

import ch.sbb.matsim.csv.CSVReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

/**
 * A class to merge the routed plans of one finished simulation run with unrouted plans of another run. All agents performing at least one activity in either of the whitelisted zones will be drawn
 * from the unrouted plans, all other from the routed plans. Agents in the non-regular subpopulation (exogeneous plans) are not handled, but their routed plans are copied. Running via IDE and vclient
 * should be possible, as the population is read and written on the fly.
 */
public class MixExperiencedPlansFromSeveralSimulations {

    private final static Logger LOG = Logger.getLogger(MixExperiencedPlansFromSeveralSimulations.class);
    private final Map<String, Map<String, String>> runs;
    private final String outputPlansFile;

    /**
     * T
     *
     * @param plansCSVPath CSV containing a list of runs with name, plans, facilities, ids
     * @param outputPlansFile The output plans file
     * @param transitSchedulePath TransitScheduleFile to check if routes exist.
     */
    public MixExperiencedPlansFromSeveralSimulations(String plansCSVPath, String outputPlansFile, String transitSchedulePath) throws IOException {
        this.runs = readRunList(plansCSVPath);
        this.outputPlansFile = outputPlansFile;
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(transitSchedulePath);
        TransitSchedule schedule = scenario.getTransitSchedule();
    }

    /**
     * @param args
     */
    public static void main(String[] args) throws IOException {
        String plansCSV = args[0];
        String outputPlansFile = args[1];
        String schedule = args[2];
        new MixExperiencedPlansFromSeveralSimulations(plansCSV, outputPlansFile, schedule).run();
    }

    private static Map<String, Map<String, String>> readRunList(String path) throws IOException {
        Map<String, Map<String, String>> runs = new HashMap<>();
        File file = new File(path);
        try (CSVReader csv = new CSVReader(new String[]{"name", "plans", "facilities", "ids"}, file.getAbsolutePath(), ";")) {
            Map<String, String> data = csv.readLine(); // header
            while ((data = csv.readLine()) != null) {
                Map<String, String> run = new HashMap<>();
                run.put("plans", data.get("plans"));
                run.put("facilities", data.get("facilities"));
                run.put("ids", data.get("ids"));
                runs.put(data.get("name"), run);
            }
        }
        return runs;
    }

    private void run()  throws IOException  {
        mergePlans();
    }

    private void mergePlans() throws IOException  {
        StreamingPopulationWriter spw = new StreamingPopulationWriter();
        spw.startStreaming(outputPlansFile);
        Set<Id<Person>> allPersons = new HashSet<>();
        List<String> persons = new ArrayList<>();
        persons.add("run;id");
        for (String run : this.runs.keySet()) {

            if (!run.equals("base")) {
                LOG.info("processing run " + run);
                Set<Id<Person>> whitelist = Files.lines(Path.of(runs.get(run).get("ids"))).map(Id::createPersonId).collect(Collectors.toSet());
                LOG.info("whitelist contains " + whitelist.size() + " persons");
                int numPersons = allPersons.size();
                StreamingPopulationReader populationReader = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
                populationReader.addAlgorithm(person -> {
                    if (whitelist.contains(person.getId())) {
                        if (!allPersons.contains(person.getId())) {
                            spw.run(person);
                            allPersons.add(person.getId());
                            persons.add(run+";"+person.getId());
                        } else {
                            LOG.warn("person " + person.getId() + "found in more than one whitelist. Ignored in " + run);
                        }
                    }
                });

                populationReader.readFile(runs.get(run).get("plans"));
                LOG.info("wrote " + (allPersons.size() - numPersons) + " persons");
            }
        }
        StreamingPopulationReader basePopulationReader = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
        basePopulationReader.addAlgorithm(person -> {
            if (!allPersons.contains(person.getId())) {
                spw.run(person);
                allPersons.add(person.getId());
                persons.add("base;"+person.getId());
            }
        });

        LOG.info("processing base");

        int numPersons = allPersons.size();

        basePopulationReader.readFile(runs.get("base").get("plans"));

        LOG.info("wrote " + (allPersons.size() - numPersons) + " persons");

        spw.closeStreaming();

        Path out = Paths.get(this.outputPlansFile.replace(".csv.gz", "_runids.csv"));
        Files.write(out ,persons, Charset.defaultCharset());
    }

}
