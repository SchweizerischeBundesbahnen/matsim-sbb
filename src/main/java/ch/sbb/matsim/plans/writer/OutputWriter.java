package ch.sbb.matsim.plans.writer;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;

public class OutputWriter {

    private final String outputPath;

    public OutputWriter(String outputPath)  {
        this.outputPath = outputPath;
    }

    public void writeOutputs(Scenario scenario) {
        new PopulationWriter(scenario.getPopulation()).write(this.outputPath + "/plans.xml.gz");
        new ObjectAttributesXmlWriter(scenario.getPopulation().getPersonAttributes()).writeFile(this.outputPath + "/personAttributes.xml.gz");
    }
}
