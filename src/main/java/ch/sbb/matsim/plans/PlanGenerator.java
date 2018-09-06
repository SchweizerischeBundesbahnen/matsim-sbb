package ch.sbb.matsim.plans;

import ch.sbb.matsim.plans.abm.AbmData;
import ch.sbb.matsim.plans.converter.ABM2MATSim;
import ch.sbb.matsim.plans.discretizer.FacilityDiscretizer;
import ch.sbb.matsim.plans.reader.AbmDataReader;
import ch.sbb.matsim.plans.reader.ScenarioLoader;
import ch.sbb.matsim.plans.writer.OutputWriter;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.network.io.NetworkReaderMatsimV2;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.opengis.feature.simple.SimpleFeature;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class PlanGenerator {

    public static void main(String[] args)  {
        // TODO: define activity options globally in the repo, not sure where
        HashMap<String, String> abmActs2matsimActs = new HashMap<>();
        abmActs2matsimActs.put("L", "leisure");
        abmActs2matsimActs.put("B", "business");
        abmActs2matsimActs.put("W", "work");
        abmActs2matsimActs.put("H", "home");
        abmActs2matsimActs.put("E", "education");
        abmActs2matsimActs.put("O", "other");
        abmActs2matsimActs.put("S", "shop");
        abmActs2matsimActs.put("A", "accompany");

        AbmData abmData = new AbmDataReader("\\\\k13536\\mobi\\plans\\endogenous\\abm\\plan_table.csv").loadABMData();
        Scenario scenario = new ScenarioLoader("\\\\k13536\\mobi\\synpop\\data\\output\\2016\\for_mobi_plans\\v_02").prepareSynpopData(abmData);
        new NetworkReaderMatsimV2(scenario.getNetwork()).readFile("\\\\k13536\\mobi\\model\\input\\network\\2016\\reference\\v1\\network.xml.gz");

        Collection<SimpleFeature> zones = new ShapeFileReader().readFileAndInitialize("\\\\V00925\\Simba\\20_Modelle\\85_SynPop_CH\\12_SynPop_CH_2016\\20_SynPop_Ergebnisse\\04_Shapefiles\\ARE_SBB_Synpop_180521\\NPVM_with_density.shp");
        Map<Integer, SimpleFeature> zonesById = new HashMap<>();
        for (SimpleFeature zone : zones) {
            int zoneId = (int) Double.parseDouble(zone.getAttribute("ID").toString());
            zonesById.put(zoneId, zone);
        }

        FacilityDiscretizer discretizer = new FacilityDiscretizer(scenario.getActivityFacilities(), zonesById);

        new ABM2MATSim(scenario).processAbmData(discretizer, abmData, abmActs2matsimActs);

        new OutputWriter("\\\\k13536\\mobi\\model\\input\\plans\\2016\\endogenous\\v1").writeOutputs(scenario);
    }
}
