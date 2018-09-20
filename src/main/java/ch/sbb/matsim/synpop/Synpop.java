package ch.sbb.matsim.synpop;

import ch.sbb.matsim.synpop.converter.AttributesConverter;
import ch.sbb.matsim.synpop.attributes.SynpopAttributes;
import ch.sbb.matsim.synpop.blurring.HomeFacilityBlurring;
import ch.sbb.matsim.synpop.config.SynpopConfigGroup;
import ch.sbb.matsim.synpop.facilities.ActivityForFacility;
import ch.sbb.matsim.synpop.facilities.ZoneIdAssigner;
import ch.sbb.matsim.synpop.reader.SynpopCSVReaderImpl;
import ch.sbb.matsim.synpop.reader.SynpopReader;
import ch.sbb.matsim.synpop.writer.MATSimWriter;
import ch.sbb.matsim.synpop.writer.PopulationCSVWriter;
import ch.sbb.matsim.synpop.writer.SQLWriter;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.population.Population;
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

        HomeFacilityBlurring blurring = new HomeFacilityBlurring(facilities, config.getZoneShapefile(), config.getShapeAttribute());

        ZoneIdAssigner assigner = new ZoneIdAssigner(blurring.getZoneAggregator());
        assigner.addWorkFacilities(facilities);
        assigner.assignIds();
        assigner.checkForMissingIds(facilities);

        AttributesConverter attributesConverter = new AttributesConverter(config.getAttributeMappingSettings(), config.getColumnMappingSettings());
        attributesConverter.map(population);
        attributesConverter.map(facilities.getFacilitiesForActivityType("home").values(), "households");
        attributesConverter.map(facilities.getFacilitiesForActivityType("work").values(), "businesses");

        //change generic ActivityType to a more specific one
        new ActivityForFacility(config.getBus2act(), facilities.getFactory()).run(facilities.getFacilitiesForActivityType("work").values());

        File output = new File(config.getOutputFolder(), config.getVersion());
        output.mkdirs();

        new SQLWriter(config.getHost(), config.getPort(), config.getDatabase(), config.getYear(), synpopAttributes).run(population, facilities, config.getVersion());
        new MATSimWriter(output.toString()).run(population, facilities);
        new PopulationCSVWriter(output.toString(), synpopAttributes).run(population, facilities);

    }
}
