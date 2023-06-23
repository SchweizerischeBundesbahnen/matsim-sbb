package ch.sbb.matsim.preparation.casestudies;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.MatsimFacilitiesReader;

public class MergeFacilities {
    public static void main(String[] args) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimFacilitiesReader(scenario).readFile("\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_4.0\\2040\\plans\\3.3.2040.11\\facilities.xml.gz");
        Scenario scenario1 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimFacilitiesReader(scenario1).readFile("\\\\wsbbrz0283\\mobi\\40_Projekte\\20230619_St.Gallen_Winkeln_2040\\plans\\facilities.xml.gz");
        Scenario scenario2 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        //new MatsimFacilitiesReader(scenario2).readFile("\\\\wsbbrz0283\\mobi\\40_Projekte\\20221010_Valais_2050\\plans\\03.1_Aigle_VS-2-C-2\\facilities.xml.gz");
        int i = 0;
        for (ActivityFacility a : scenario1.getActivityFacilities().getFacilities().values()) {

            ActivityFacility activityFacilityB = scenario.getActivityFacilities().getFacilities().get(a.getId());
            if (activityFacilityB != null) {
                if (CoordUtils.calcEuclideanDistance(a.getCoord(), activityFacilityB.getCoord()) > 1.0) i++;
            }
        }
        for (ActivityFacility a : scenario2.getActivityFacilities().getFacilities().values()) {
            if (!scenario.getActivityFacilities().getFacilities().containsKey(a.getId())) {
                scenario.getActivityFacilities().addActivityFacility(a);
            }
        }
        System.out.println(i);
        // new FacilitiesWriter(scenario.getActivityFacilities()).write("\\\\wsbbrz0283\\mobi\\40_Projekte\\20230619_St.Gallen_Winkeln_2040\\sim\\case\\prepared\\facilities.xml.gz");

    }

}
