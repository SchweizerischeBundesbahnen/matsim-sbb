package ch.sbb.matsim.synpop;

import ch.sbb.matsim.synpop.blurring.HomeFacilityBlurring;
import ch.sbb.matsim.synpop.reader.SynpopCSVReaderImpl;
import ch.sbb.matsim.synpop.reader.SynpopReader;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.FacilitiesWriter;

import java.io.File;

public class Synpop {


    public static void main(String[] args) {
        String folder = "\\\\v00925\\Simba\\20_Modelle\\85_SynPop_CH\\12_SynPop_CH_2016\\20_SynPop_Ergebnisse\\02_DatenLieferungen\\ARE_SBB_Synpop_180610";

        SynpopReader loader = new SynpopCSVReaderImpl(folder);
        loader.load();

        Population population = loader.getPopulation();
        ActivityFacilities facilities = loader.getFacilities();

        new HomeFacilityBlurring(facilities, "\\\\v00925\\Simba\\20_Modelle\\85_SynPop_CH\\12_SynPop_CH_2016\\20_SynPop_Ergebnisse\\04_Shapefiles\\ARE_SBB_Synpop_180521\\NPVM_with_density.shp");

        new PopulationWriter(population).write(new File(folder, "popuplation.xml.gz").toString());
        new FacilitiesWriter(facilities).write(new File(folder, "facilities.xml.gz").toString());

    }
}
