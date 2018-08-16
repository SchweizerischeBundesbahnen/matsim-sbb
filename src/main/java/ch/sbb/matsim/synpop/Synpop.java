package ch.sbb.matsim.synpop;

import ch.sbb.matsim.synpop.converter.AttributesConverter;
import ch.sbb.matsim.synpop.attributes.SynpopAttributes;
import ch.sbb.matsim.synpop.blurring.HomeFacilityBlurring;
import ch.sbb.matsim.synpop.config.SynpopConfigGroup;
import ch.sbb.matsim.synpop.reader.SynpopCSVReaderImpl;
import ch.sbb.matsim.synpop.reader.SynpopReader;
import ch.sbb.matsim.synpop.writer.MATSimWriter;
import ch.sbb.matsim.synpop.writer.PopulationCSVWriter;
import ch.sbb.matsim.synpop.writer.SQLWriter;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.ConfigUtils;
import org.matsim.facilities.ActivityFacilities;

import java.io.File;


public class Synpop {
    private final static Logger log = Logger.getLogger(Synpop.class);

    public static void main(String[] args) {
        String configPath = args[0];

        SynpopConfigGroup config = ConfigUtils.addOrGetModule(ConfigUtils.loadConfig(configPath, new SynpopConfigGroup()), SynpopConfigGroup.class);
        SynpopAttributes synpopAttributes = new SynpopAttributes(config.getAttributesCSV());


        SynpopReader reader = new SynpopCSVReaderImpl(config.getFalcFolder());
        reader.load();

        Population population = reader.getPopulation();
        ActivityFacilities facilities = reader.getFacilities();

        new HomeFacilityBlurring(facilities, config.getZoneShapefile());

        AttributesConverter attributesConverter = new AttributesConverter(config.getAttributeMappingSettings(), config.getColumnMappingSettings());
        attributesConverter.map(population);
        attributesConverter.map(facilities.getFacilitiesForActivityType("home").values(), "households");
        attributesConverter.map(facilities.getFacilitiesForActivityType("work").values(), "businesses");


        File output  = new File(config.getOutputFolder(), config.getVersion());
        output.mkdirs();

        new SQLWriter(config.getHost(), config.getPort(), config.getDatabase(), config.getYear(), synpopAttributes).run(population, facilities, config.getVersion());
        new MATSimWriter(output.toString()).run(population, facilities);
        new PopulationCSVWriter(output.toString(), synpopAttributes).run(population, facilities);


    }
}
