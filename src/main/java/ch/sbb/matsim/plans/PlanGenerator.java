package ch.sbb.matsim.plans;

import ch.sbb.matsim.plans.abm.AbmData;
import ch.sbb.matsim.plans.converter.ABM2MATSim;
import ch.sbb.matsim.plans.discretizer.FacilityDiscretizer;
import ch.sbb.matsim.plans.reader.AbmDataReader;
import ch.sbb.matsim.plans.reader.ScenarioLoader;
import ch.sbb.matsim.plans.writer.OutputWriter;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.opengis.feature.simple.SimpleFeature;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class PlanGenerator {

    public static void main(String[] args)  {

        String pathToABM = args[0];
        String pathToSynPop = args[1];
        String pathToMATSimNetwork = args[2];
        String pathToShapeFile = args[3];
        String pathToOutputDir = args[4];

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

        AbmData abmData = new AbmDataReader().loadABMData(pathToABM);
        Scenario scenario = ScenarioLoader.prepareSynpopData(abmData, pathToSynPop);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(pathToMATSimNetwork);

        Collection<SimpleFeature> zones = new ShapeFileReader().readFileAndInitialize(pathToShapeFile);
        Map<Integer, SimpleFeature> zonesById = new HashMap<>();
        for (SimpleFeature zone : zones) {
            int zoneId = (int) Double.parseDouble(zone.getAttribute("ID").toString());
            zonesById.put(zoneId, zone);
        }

        FacilityDiscretizer discretizer = new FacilityDiscretizer(scenario.getActivityFacilities(), zonesById);

        new ABM2MATSim(scenario).processAbmData(discretizer, abmData, abmActs2matsimActs);

        new OutputWriter(scenario).writeOutputs(pathToOutputDir);
    }
}
