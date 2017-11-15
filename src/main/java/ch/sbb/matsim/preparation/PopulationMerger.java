/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.preparation;

import ch.sbb.matsim.config.PopulationMergerConfigGroup;
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
    private PopulationMergerConfigGroup mergerConfig;
    private Scenario scenario;
    private PopulationReader reader;
    private PopulationWriter writer;
    private ObjectAttributes personAttributes;
    private ObjectAttributesXmlReader attributesReader;
    private ObjectAttributesXmlWriter attributesWriter;

    public static void main(final String[] args) {
        Config config = ConfigUtils.loadConfig(args[0], new PopulationMergerConfigGroup());

        PopulationMerger merger = new PopulationMerger(config);

        merger.mergeInputPlanFiles();
        merger.putPersonAttributes();
        merger.writeOutputFiles();
    }

    public PopulationMerger(Config config) {
        this.mergerConfig = (PopulationMergerConfigGroup) config.getModule(PopulationMergerConfigGroup.GROUP_NAME);

        this.scenario = ScenarioUtils.createScenario(config);
        this.reader = new PopulationReaderMatsimV5(this.scenario);
        this.writer = new PopulationWriter(this.scenario.getPopulation());

        this.personAttributes = this.scenario.getPopulation().getPersonAttributes();
        this.attributesReader = new ObjectAttributesXmlReader(this.personAttributes);
        this.attributesWriter = new ObjectAttributesXmlWriter(this.personAttributes);

        this.reader.readFile(config.plans().getInputFile());
        this.attributesReader.parse(config.plans().getInputPersonAttributeFile());
    }

    protected void mergeInputPlanFiles() {
        String inputPlanFile = null;
        while ((inputPlanFile = this.mergerConfig.shiftInputPlansFiles()) != null) {
            this.reader.readFile(inputPlanFile);
        }
    }

    protected void putPersonAttributes() {
        for (final Person person : this.scenario.getPopulation().getPersons().values()) {
            if (this.personAttributes.getAttribute(person.getId().toString(), this.mergerConfig.getMergedPersonAttributeKey()) == null) {
                this.personAttributes.putAttribute(
                        person.getId().toString(),
                        this.mergerConfig.getMergedPersonAttributeKey(),
                        this.mergerConfig.getMergedPersonAttributeValue()
                );
            }
        }
    }

    protected void writeOutputFiles() {
        this.writer.write(this.mergerConfig.getOutputPlansFile());
        this.attributesWriter.writeFile(this.mergerConfig.getOutputPersonAttributesFile());
    }
}
