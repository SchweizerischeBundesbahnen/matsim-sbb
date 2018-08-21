package ch.sbb.matsim.synpop.writer;

import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.synpop.attributes.SynpopAttributes;
import ch.sbb.matsim.synpop.converter.PersonAttributes;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.population.Population;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class PopulationCSVWriter {

    String output;
    SynpopAttributes personAttributes;
    private final static Logger log = Logger.getLogger(PopulationCSVWriter.class);

    public PopulationCSVWriter(String output, SynpopAttributes personAttributes) {
        this.output = output;
        this.personAttributes = personAttributes;
    }


    private String[] getColumns(String name) {
        return this.personAttributes.getAttributes(name).stream().map(a -> a.getName()).toArray(String[]::new);
    }

    private void write(Population population) {

        try (CSVWriter writer = new CSVWriter("", getColumns("persons"), new File(this.output, "persons.csv").toString())) {
            population.getPersons().values().stream().forEach(p -> {
                this.personAttributes.getAttributes("persons").stream().map(a -> a.getName()).forEach(c -> {
                    writer.set(c, p.getAttributes().getAttribute(c).toString());
                });
                writer.writeRow();
            });
        } catch (IOException e) {
            log.warn(e);
        }
    }


    private void write(Collection<ActivityFacility> facilities, String name) {

        try (CSVWriter writer = new CSVWriter("", getColumns(name), new File(this.output, name + ".csv").toString())) {
            facilities.stream().forEach(p -> {
                this.personAttributes.getAttributes(name).stream().map(a -> a.getName()).forEach(c -> {
                    try {
                        writer.set(c, p.getAttributes().getAttribute(c).toString());
                    } catch (NullPointerException e) {
                        log.error(p.getAttributes());
                        log.error(c);
                        throw e;
                    }
                });
                writer.writeRow();

            });
        } catch (
                IOException e)

        {
            log.warn(e);
        }

    }


    public void run(Population population, ActivityFacilities facilities) {
        this.write(population);
        this.write(facilities.getFacilitiesForActivityType("home").values(), "households");
        this.write(facilities.getFacilitiesForActivityType("work").values(), "businesses");
    }
}


