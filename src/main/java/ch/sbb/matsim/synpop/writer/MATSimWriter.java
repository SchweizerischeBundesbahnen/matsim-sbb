package ch.sbb.matsim.synpop.writer;

import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.FacilitiesWriter;

import java.io.File;

public class MATSimWriter {

    String output;

    public MATSimWriter(String output) {
        this.output = output;
    }

    public void run(Population population, ActivityFacilities facilities) {
        new PopulationWriter(population).write(new File(output, "popuplation.xml.gz").toString());
        new FacilitiesWriter(facilities).write(new File(output, "facilities.xml.gz").toString());
    }
}
