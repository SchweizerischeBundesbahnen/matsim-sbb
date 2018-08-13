package ch.sbb.matsim.synpop;

import ch.sbb.matsim.database.Engine;
import ch.sbb.matsim.database.tables.synpop.PersonsTable;
import ch.sbb.matsim.synpop.attributes.PersonAttributes;
import ch.sbb.matsim.synpop.blurring.HomeFacilityBlurring;
import ch.sbb.matsim.synpop.reader.SynpopCSVReaderImpl;
import ch.sbb.matsim.synpop.reader.SynpopReader;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.FacilitiesWriter;

import java.io.File;
import java.sql.SQLException;
import java.util.EnumMap;

public class Synpop {
    private final static Logger log = Logger.getLogger(Synpop.class);

    public static void main(String[] args) {
        final String folder = "\\\\v00925\\Simba\\20_Modelle\\85_SynPop_CH\\12_SynPop_CH_2016\\20_SynPop_Ergebnisse\\02_DatenLieferungen\\ARE_SBB_Synpop_180610";

        SynpopReader loader = new SynpopCSVReaderImpl(folder);
        loader.load();

        Population population = loader.getPopulation();
        //ActivityFacilities facilities = loader.getFacilities();

        //new HomeFacilityBlurring(facilities, "\\\\v00925\\Simba\\20_Modelle\\85_SynPop_CH\\12_SynPop_CH_2016\\20_SynPop_Ergebnisse\\04_Shapefiles\\ARE_SBB_Synpop_180521\\NPVM_with_density.shp");


        PersonAttributes personAttributes = new PersonAttributes();

        for (Person person : population.getPersons().values()) {
            personAttributes.completeAttributes(person);
        }


        try {
            Engine engine = new Engine("2016test");
            PersonsTable table = new PersonsTable(population);
            engine.dropTable(table);
            engine.createTable(table);
            engine.writeToTable(table);
        } catch (SQLException a) {
            a.printStackTrace();
            log.info(a);
        }


        //new PopulationWriter(population).write(new File(folder, "popuplation.xml.gz").toString());
        //new FacilitiesWriter(facilities).write(new File(folder, "facilities.xml.gz").toString());


    }
}
