/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.preparation;

import ch.sbb.matsim.config.PopulationMergerConfigGroup;
import ch.sbb.matsim.config.PtMergerConfigGroup;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationReader;
import org.matsim.core.population.PopulationReaderMatsimV5;
import org.matsim.core.population.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.utils.objectattributes.ObjectAttributes;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;

import java.io.File;

public class PopulationMerger {
    public static void main(final String[] args) {
        Logger log = Logger.getLogger(Cutter.class);
        PopulationReader reader;
        PopulationWriter writer;
        ObjectAttributesXmlWriter attributesWriter;

        final Config config = ConfigUtils.loadConfig(args[0], new PopulationMergerConfigGroup());

        Scenario scenario = ScenarioUtils.createScenario(config);
        ObjectAttributes personAttributes = scenario.getPopulation().getPersonAttributes();

        reader = new PopulationReaderMatsimV5(scenario);
        reader.readFile(config.plans().getInputFile());
        new ObjectAttributesXmlReader(scenario.getPopulation().getPersonAttributes()).parse(config.plans().getInputPersonAttributeFile());

        final PopulationMergerConfigGroup mergerConfig = (PopulationMergerConfigGroup) config.getModule(PopulationMergerConfigGroup.GROUP_NAME);

        String inputPlanFile = null;
        while ((inputPlanFile = mergerConfig.shiftInputPlansFiles()) != null) {
            reader.readFile(inputPlanFile);
        }

        for (Person person : scenario.getPopulation().getPersons().values()) {
            if (personAttributes.getAttribute(person.getId().toString(), mergerConfig.getMergedPersonAttributeKey()) == null) {
                personAttributes.putAttribute(
                        person.getId().toString(),
                        mergerConfig.getMergedPersonAttributeKey(),
                        mergerConfig.getMergedPersonAttributeValue()
                );
            }
        }

        writer = new PopulationWriter(scenario.getPopulation());
        writer.write(mergerConfig.getOutputPlansFile());

        attributesWriter = new ObjectAttributesXmlWriter(personAttributes);
        attributesWriter.writeFile(mergerConfig.getOutputPersonAttributesFile());
    }
}
