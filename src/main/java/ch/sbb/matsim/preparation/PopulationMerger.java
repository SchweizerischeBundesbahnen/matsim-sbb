/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.preparation;

import ch.sbb.matsim.config.PopulationMergerConfigGroup;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.utils.objectattributes.ObjectAttributes;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;

import java.util.Map;
import java.util.TreeMap;

public class PopulationMerger {
    private Scenario scenario;
    private PopulationReader reader;
    private PopulationWriter writer;
    private ObjectAttributes personAttributes;
    private ObjectAttributesXmlReader attributesReader;
    private ObjectAttributesXmlWriter attributesWriter;

    public static void main(final String[] args) {
        Config config = ConfigUtils.loadConfig(args[0], new PopulationMergerConfigGroup());
        PopulationMergerConfigGroup mergerConfig = (PopulationMergerConfigGroup) config.getModule(PopulationMergerConfigGroup.GROUP_NAME);

        PopulationMerger merger = new PopulationMerger(config);

        Map<String, String> attributes = new TreeMap<String, String>();
        attributes.put(mergerConfig.getMergedPersonAttributeKey(), mergerConfig.getMergedPersonAttributeValue());
        attributes.put("season_ticket", "none");

        merger.mergeInputPlanFiles(mergerConfig);
        merger.putPersonAttributes(attributes);
        merger.putPersonCustomAttributes();
        merger.writeOutputFiles(mergerConfig);
    }

    public PopulationMerger(Config config) {
        this.scenario = ScenarioUtils.createScenario(config);
        this.reader = new PopulationReader(this.scenario);
        this.writer = new PopulationWriter(this.scenario.getPopulation());

        this.personAttributes = this.scenario.getPopulation().getPersonAttributes();
        this.attributesReader = new ObjectAttributesXmlReader(this.personAttributes);
        this.attributesWriter = new ObjectAttributesXmlWriter(this.personAttributes);

        this.reader.readFile(config.plans().getInputFile());
        this.attributesReader.readFile(config.plans().getInputPersonAttributeFile());
    }

    protected void mergeInputPlanFiles(PopulationMergerConfigGroup mergerConfig) {
        String inputPlanFile = null;
        while ((inputPlanFile = mergerConfig.shiftInputPlansFiles()) != null) {
            this.reader.readFile(inputPlanFile);
        }
    }

    protected void putPersonAttributes(Map<String, String> attributes) {
        for (final Person person : this.scenario.getPopulation().getPersons().values()) {
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (this.personAttributes.getAttribute(person.getId().toString(), key) == null) {
                    this.personAttributes.putAttribute(person.getId().toString(), key, value);
                }
            }
        }
    }

    protected void putPersonCustomAttributes() {
        for (final Person person : this.scenario.getPopulation().getPersons().values()) {
            Object carAvail = this.personAttributes.getAttribute(person.getId().toString(), "availability: car");

            if (carAvail != null && (!carAvail.toString().equals("never"))) {
                person.getCustomAttributes().put("carAvail", "always");
                PersonUtils.setLicence(person, "yes");
            } else{
                person.getCustomAttributes().put("carAvail", "never");
                PersonUtils.setLicence(person, "no");
            }

            Object age = this.personAttributes.getAttribute(person.getId().toString(), "age");

            if (age != null){
                person.getCustomAttributes().put("age", age);
            }

            Object gender = this.personAttributes.getAttribute(person.getId().toString(), "gender");

            if (gender != null && (!gender.toString().isEmpty())){
                person.getCustomAttributes().put("gender", gender.toString());
            }
        }
    }

    protected void writeOutputFiles(PopulationMergerConfigGroup mergerConfig) {
        this.writer.write(mergerConfig.getOutputPlansFile());
        this.attributesWriter.writeFile(mergerConfig.getOutputPersonAttributesFile());
    }
}
