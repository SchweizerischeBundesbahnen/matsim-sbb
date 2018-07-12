package ch.sbb.matsim.synpop;

import ch.sbb.matsim.synpop.blurring.HomeFacilityBlurring;
import ch.sbb.matsim.synpop.loader.SynpopCSVLoaderImpl;
import ch.sbb.matsim.synpop.loader.SynpopLoader;
import org.matsim.api.core.v01.population.Population;
import org.matsim.facilities.ActivityFacilities;

public class Synpop {


    public static void main(String[] args) {
        SynpopLoader loader = new SynpopCSVLoaderImpl("\\\\v00925\\Simba\\20_Modelle\\85_SynPop_CH\\12_SynPop_CH_2016\\20_SynPop_Ergebnisse\\02_DatenLieferungen\\ARE_SBB_Synpop_180610");
        loader.load();

        Population population = loader.getPopulation();
        ActivityFacilities facilities = loader.getFacilities();

        new HomeFacilityBlurring(facilities, "\\\\v00925\\Simba\\20_Modelle\\85_SynPop_CH\\12_SynPop_CH_2016\\20_SynPop_Ergebnisse\\04_Shapefiles\\ARE_SBB_Synpop_180521\\NPVM_with_density.shp");

    }
}
