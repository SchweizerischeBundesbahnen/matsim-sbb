/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.preparation;

import ch.sbb.matsim.config.PopulationMergerConfigGroup;
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

public class PopulationMerger {
    public static void main(final String[] args) {
        final Config config = ConfigUtils.loadConfig(args[0], new PopulationMergerConfigGroup());
        final PopulationMergerConfigGroup mergerConfig = (PopulationMergerConfigGroup) config.getModule(PopulationMergerConfigGroup.GROUP_NAME);

        final Scenario scenario = ScenarioUtils.createScenario(config);
        final PopulationReader reader = new PopulationReaderMatsimV5(scenario);
        final PopulationWriter writer = new PopulationWriter(scenario.getPopulation());

        final ObjectAttributes personAttributes = scenario.getPopulation().getPersonAttributes();
        final ObjectAttributesXmlReader attributesReader = new ObjectAttributesXmlReader(personAttributes);
        final ObjectAttributesXmlWriter attributesWriter = new ObjectAttributesXmlWriter(personAttributes);;

        reader.readFile(config.plans().getInputFile());
        attributesReader.parse(config.plans().getInputPersonAttributeFile());

        String inputPlanFile = null;
        while ((inputPlanFile = mergerConfig.shiftInputPlansFiles()) != null) {
            reader.readFile(inputPlanFile);
        }

        for (final Person person : scenario.getPopulation().getPersons().values()) {
            if (personAttributes.getAttribute(person.getId().toString(), mergerConfig.getMergedPersonAttributeKey()) == null) {
                personAttributes.putAttribute(
                        person.getId().toString(),
                        mergerConfig.getMergedPersonAttributeKey(),
                        mergerConfig.getMergedPersonAttributeValue()
                );
            }
        }

        writer.write(mergerConfig.getOutputPlansFile());
        attributesWriter.writeFile(mergerConfig.getOutputPersonAttributesFile());
    }
}
