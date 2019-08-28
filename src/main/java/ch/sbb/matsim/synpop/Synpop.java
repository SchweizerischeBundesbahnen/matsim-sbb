package ch.sbb.matsim.synpop;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.synpop.attributes.SynpopAttributes;
import ch.sbb.matsim.synpop.blurring.HomeFacilityBlurring;
import ch.sbb.matsim.synpop.config.SynpopConfigGroup;
import ch.sbb.matsim.synpop.converter.AttributesConverter;
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
        final String configPath = args[0];

        final SynpopConfigGroup config = ConfigUtils.addOrGetModule(ConfigUtils.loadConfig(configPath, new SynpopConfigGroup()), SynpopConfigGroup.class);
        final SynpopAttributes synpopAttributes = new SynpopAttributes(config.getAttributesCSV());

        final SynpopReader reader = new SynpopCSVReaderImpl(config.getFalcFolder());
        reader.load();

        final Population population = reader.getPopulation();
        final ActivityFacilities facilities = reader.getFacilities();

        final HomeFacilityBlurring blurring = new HomeFacilityBlurring(facilities, config.getZoneShapefile(), config.getShapeAttribute());

        final ZoneIdAssigner assigner = new ZoneIdAssigner(blurring.getZoneAggregator());
        assigner.addFacilitiesOfType(facilities, "work");
        assigner.assignIds(Variables.T_ZONE);
        assigner.checkForMissingIds(facilities, Variables.T_ZONE);

        final AttributesConverter attributesConverter = new AttributesConverter(config.getAttributeMappingSettings(), config.getColumnMappingSettings());
        attributesConverter.map(population);
        attributesConverter.map(facilities.getFacilitiesForActivityType("home").values(), "households");
        attributesConverter.map(facilities.getFacilitiesForActivityType("work").values(), "businesses");

        final File output = new File(config.getOutputFolder(), config.getVersion());
        output.mkdirs();

        new MATSimWriter(output.toString()).run(population, facilities);
        new PopulationCSVWriter(output.toString(), synpopAttributes).run(population, facilities);
        //new SQLWriter(config.getHost(), config.getPort(), config.getDatabase(), config.getYear(), synpopAttributes).run(population, facilities, config.getVersion());

    }
}
