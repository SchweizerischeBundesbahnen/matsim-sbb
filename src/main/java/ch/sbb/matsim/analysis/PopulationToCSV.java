/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.households.HouseholdsReaderV10;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.preparation.Cleaner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class PopulationToCSV {


    CSVWriter agents_writer = null;
    CSVWriter planelements_writer = null;

    public PopulationToCSV(Scenario scenario) {

        Logger log = Logger.getLogger(PopulationToCSV.class);

        PostProcessingConfigGroup ppConfig = (PostProcessingConfigGroup) scenario.getConfig().getModule(PostProcessingConfigGroup.GROUP_NAME);

        Cleaner cleaner = new Cleaner(scenario.getPopulation());
        cleaner.clean();

        String[] attributes = ppConfig.getPersonAttributes().split(",");
        agents_writer = new CSVWriter(getColumns(scenario.getPopulation(), attributes));
        planelements_writer = new CSVWriter(new String[]{"person_id", "plan_id", "planelement_id", "selected", "plan_score", "start_time", "end_time", "type", "mode", "activity_type", "x", "y"});

        for (Person person : scenario.getPopulation().getPersons().values()) {
            HashMap<String, String> agent = agents_writer.addRow();
            agent.put("person_id", person.getId().toString());


            for (Map.Entry<String, Object> attribute : person.getCustomAttributes().entrySet()) {
                if (attribute.getValue() != null) {
                    agent.put(attribute.getKey(), attribute.getValue().toString());
                }
            }
            for (String attribute_name : attributes) {
                Object attribute = scenario.getPopulation().getPersonAttributes().getAttribute(person.getId().toString(), attribute_name);
                if (attribute != null) {
                    agent.put(attribute_name, attribute.toString());
                }
            }


            int j = 0;
            for (Plan plan : person.getPlans()) {
                j += 1;

                String score = "";
                if (plan.getScore() != null) {
                    score = plan.getScore().toString();
                }
                String selected = "no";
                if (person.getSelectedPlan().equals(plan)) {
                    selected = "yes";
                }


                int i = 0;
                for (PlanElement planelement : plan.getPlanElements()) {
                    i += 1;

                    HashMap planelem = planelements_writer.addRow();

                    planelem.put("person_id", person.getId().toString());
                    planelem.put("plan_id", Integer.toString(j));
                    planelem.put("selected", selected);
                    planelem.put("plan_score", score);
                    planelem.put("planelement_id", Integer.toString(i));

                    if (planelement instanceof Leg) {
                        Leg leg = ((Leg) planelement);
                        planelem.put("mode", leg.getMode().toString());
                        planelem.put("start_time", Double.toString(leg.getDepartureTime()));
                        planelem.put("end_time", Double.toString(leg.getDepartureTime()+leg.getTravelTime()));
                        planelem.put("type", "leg");

                    }
                    if (planelement instanceof Activity) {
                        Activity activity = ((Activity) planelement);
                        planelem.put("activity_type", activity.getType().toString());
                        planelem.put("start_time", Double.toString(activity.getStartTime()));
                        planelem.put("end_time", Double.toString(activity.getEndTime()));
                        planelem.put("type", "activity");
                        planelem.put("x", Double.toString(activity.getCoord().getX()));
                        planelem.put("y", Double.toString(activity.getCoord().getY()));
                    }
                }
            }
        }
    }

    public void write(String agentsFilename, String planElementFilename) {
        agents_writer.write(agentsFilename);
        planelements_writer.write(planElementFilename);
    }

    public String[] getColumns(final Population population, String[] attributes) {

        ArrayList<String> columns = new ArrayList<>();

        columns.add("person_id");

        for (Person person : population.getPersons().values()) {
            for (String key : person.getCustomAttributes().keySet()) {
                columns.add(key);
            }
            break;
        }

        for (String attibute : attributes) {
            columns.add(attibute);
        }

        return columns.toArray(new String[0]);
    }

    public static void main(final String[] args) {

        Config config = ConfigUtils.loadConfig(args[0], new PostProcessingConfigGroup());
        String populationFile = args[1];


        Scenario scenario = ScenarioUtils.createScenario(config);

        config.plans().setInputFile(populationFile);

        new PopulationReader(scenario).readFile(config.plans().getInputFile());


        if (config.plans().getInputPersonAttributeFile() != null) {
            new ObjectAttributesXmlReader(scenario.getPopulation().getPersonAttributes()).readFile(config.plans().getInputPersonAttributeFile());
        }
        if (config.households().getInputFile() != null) {
            new HouseholdsReaderV10(scenario.getHouseholds()).readFile(config.households().getInputFile());
        }
        if (config.households().getInputHouseholdAttributesFile() != null) {
            new ObjectAttributesXmlReader(scenario.getHouseholds().getHouseholdAttributes()).readFile(config.households().getInputHouseholdAttributesFile());
        }

        new PopulationToCSV(scenario).write("agents.csv", "planelements.csv");
    }


}
