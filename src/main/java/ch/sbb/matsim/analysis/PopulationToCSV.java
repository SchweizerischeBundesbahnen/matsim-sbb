/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis;

import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.csv.CSVWriter;
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

import java.io.IOException;

public class PopulationToCSV {
    private final static Logger log = Logger.getLogger(PopulationToCSV.class);

    private final static String[] PLANELEMENTS_COLUMNS = new String[]{"person_id", "plan_id", "planelement_id", "selected", "plan_score", "start_time", "end_time", "type", "mode", "activity_type", "x", "y"};

    private final Scenario scenario;

    public PopulationToCSV(Scenario scenario) {
        this.scenario = scenario;
    }

    public void write(String filename) {
        this.write(filename + "agents.csv.gz", filename + "plan_elements.csv.gz");
    }

    public void write(String agentsFilename, String planElementsFilename) {
        PostProcessingConfigGroup ppConfig = ConfigUtils.addOrGetModule(this.scenario.getConfig(), PostProcessingConfigGroup.class);
        Population population = this.scenario.getPopulation();
        String[] attributes = ppConfig.getPersonAttributes().split(",");

        if(ppConfig.getWriteAgentsCSV()) {
            try (CSVWriter agentsWriter = new CSVWriter("", getColumns(attributes), agentsFilename)) {
                for (Person person : population.getPersons().values()) {
                    agentsWriter.set("person_id", person.getId().toString());
                    for (String attribute_name : attributes) {
                        Object attribute;
                        attribute = person.getAttributes().getAttribute(attribute_name);

                        if (attribute == null)
                            attribute = population.getPersonAttributes().getAttribute(person.getId().toString(), attribute_name);

                        if (attribute != null)
                            agentsWriter.set(attribute_name, attribute.toString());
                    }
                    agentsWriter.writeRow();
                }
            } catch (IOException e) {
                log.error("Could not write agents.csv.gz " + e.getMessage(), e);
            }
        }

        if(ppConfig.getWritePlanElementsCSV())  {
            try(CSVWriter planelementsWriter = new CSVWriter("", PLANELEMENTS_COLUMNS, planElementsFilename)) {
                for (Person person : population.getPersons().values()) {
                    int j = 0;
                    for (Plan plan : person.getPlans()) {
                        j += 1;

                        String score = "";
                        if (plan.getScore() != null)
                            score = plan.getScore().toString();

                        String selected = "no";
                        if (person.getSelectedPlan().equals(plan))
                            selected = "yes";

                        int i = 0;
                        for (PlanElement planelement : plan.getPlanElements()) {
                            i += 1;

                            planelementsWriter.set("person_id", person.getId().toString());
                            planelementsWriter.set("plan_id", Integer.toString(j));
                            planelementsWriter.set("selected", selected);
                            planelementsWriter.set("plan_score", score);
                            planelementsWriter.set("planelement_id", Integer.toString(i));

                            if (planelement instanceof Leg) {
                                Leg leg = ((Leg) planelement);
                                planelementsWriter.set("mode", leg.getMode());
                                planelementsWriter.set("start_time", Double.toString(leg.getDepartureTime()));
                                planelementsWriter.set("end_time", Double.toString(leg.getDepartureTime() + leg.getTravelTime()));
                                planelementsWriter.set("type", "leg");

                            }
                            if (planelement instanceof Activity) {
                                Activity activity = ((Activity) planelement);
                                planelementsWriter.set("activity_type", activity.getType());
                                planelementsWriter.set("start_time", Double.toString(activity.getStartTime()));
                                planelementsWriter.set("end_time", Double.toString(activity.getEndTime()));
                                planelementsWriter.set("type", "activity");
                                planelementsWriter.set("x", Double.toString(activity.getCoord().getX()));
                                planelementsWriter.set("y", Double.toString(activity.getCoord().getY()));
                            }

                            planelementsWriter.writeRow();
                        }
                    }
                }
            } catch (IOException e) {
                log.error("Could not write agents.csv.gz " + e.getMessage(), e);
            }
        }
    }

    private String[] getColumns(String[] attributes) {
        String[] columns = new String[attributes.length + 1];
        columns[0] = "person_id";
        System.arraycopy(attributes, 0, columns, 1, attributes.length);
        return columns;
    }

    public static void main(final String[] args) throws IOException {
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

        new PopulationToCSV(scenario).write("agents.csv.gz", "planelements.csv.gz");
    }

}
