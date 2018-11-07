/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.preparation;

import ch.sbb.matsim.config.PopulationMergerConfigGroup;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
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
    private Population population;
    private ObjectAttributes personAttributes;

    public static void main(final String[] args) {
        Config config = ConfigUtils.loadConfig(args[0], new PopulationMergerConfigGroup());
        PopulationMergerConfigGroup mergerConfig = ConfigUtils.addOrGetModule(config, PopulationMergerConfigGroup.class);

        PopulationMerger merger = new PopulationMerger(config);

        Map<String, String> attributes = new TreeMap<>();
        attributes.put(mergerConfig.getMergedPersonAttributeKey(), mergerConfig.getMergedPersonAttributeValue());
        attributes.put("season_ticket", "none");

        merger.mergeInputPlanFiles(mergerConfig);
        merger.putMergedPersonAttributes(attributes);
        merger.putPersonAttributes();
        merger.writeOutputFiles(mergerConfig);
    }

    public PopulationMerger(Config config) {
        this.scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        this.population = this.scenario.getPopulation();
        this.personAttributes = this.scenario.getPopulation().getPersonAttributes();

        if(config.plans().getInputFile() != null)
            new PopulationReader(this.scenario).readFile(config.plans().getInputFile());
        if(config.plans().getInputPersonAttributeFile() != null)
            new ObjectAttributesXmlReader(this.personAttributes).readFile(config.plans().getInputPersonAttributeFile());
    }

    protected void mergeInputPlanFiles(PopulationMergerConfigGroup mergerConfig) {
        String inputPlanFile;
        while ((inputPlanFile = mergerConfig.shiftInputPlansFiles()) != null) {
            new PopulationReader(this.scenario).readFile(inputPlanFile);
        }
    }

    protected void putMergedPersonAttributes(Map<String, String> attributes) {
        for (final Person person : this.population.getPersons().values()) {
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                if (this.personAttributes.getAttribute(person.getId().toString(), key) == null) {
                    this.personAttributes.putAttribute(person.getId().toString(), key, value);
                }
            }
        }
    }

    protected void putPersonAttributes() {
        for (final Person person : this.population.getPersons().values()) {
            Object carAvail = this.personAttributes.getAttribute(person.getId().toString(), "availability: car");

            if (carAvail != null && (!carAvail.toString().equals("never"))) {
                PersonUtils.setCarAvail(person, "always");
                PersonUtils.setLicence(person, "yes");
            } else{
                PersonUtils.setCarAvail(person, "never");
                PersonUtils.setLicence(person, "no");
            }

            Object age = this.personAttributes.getAttribute(person.getId().toString(), "age");

            if ((age != null) && (!age.toString().isEmpty())) {
                PersonUtils.setAge(person, Integer.parseInt(age.toString()));
            }

            Object gender = this.personAttributes.getAttribute(person.getId().toString(), "gender");

            if (gender != null && (!gender.toString().isEmpty())){
                PersonUtils.setSex(person, gender.toString());
            }
        }
    }

    protected void writeOutputFiles(PopulationMergerConfigGroup mergerConfig) {
        new PopulationWriter(this.population).write(mergerConfig.getOutputPlansFile());
        new ObjectAttributesXmlWriter(this.personAttributes).writeFile(mergerConfig.getOutputPersonAttributesFile());
    }
}
