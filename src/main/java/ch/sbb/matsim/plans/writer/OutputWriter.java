package ch.sbb.matsim.plans.writer;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;

import java.io.File;

public class OutputWriter {

    private final Scenario scenario;

    public OutputWriter(Scenario scenario)  {
        this.scenario = scenario;
    }

    public void writeOutputs(String path) {
        File outputPath = new File(path);
        if(!outputPath.exists()) {
            outputPath.mkdirs();
        }

        new PopulationWriter(this.scenario.getPopulation()).write(path + "/plans.xml.gz");
        new ObjectAttributesXmlWriter(this.scenario.getPopulation().getPersonAttributes()).writeFile(path + "/personAttributes.xml.gz");
    }
}
