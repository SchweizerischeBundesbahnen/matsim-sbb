package ch.sbb.matsim.preparation.casestudies;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.FacilitiesWriter;
import org.matsim.facilities.MatsimFacilitiesReader;

public class MergeFacilities {
    public static void main(String[] args) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimFacilitiesReader(scenario).readFile("\\\\wsbbrz0283\\mobi\\40_Projekte\\20220114_MOBi_3.3\\2050\\sim\\3.3.2050.2.50pct\\prepared\\facilities.xml.gz");
        Scenario scenario1 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimFacilitiesReader(scenario1).readFile("\\\\wsbbrz0283\\mobi\\40_Projekte\\20221010_Valais_2050\\plans\\02.1_Villneuve_VS-2-B-2\\facilities.xml.gz");
        Scenario scenario2 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimFacilitiesReader(scenario2).readFile("\\\\wsbbrz0283\\mobi\\40_Projekte\\20221010_Valais_2050\\plans\\03.1_Aigle_VS-2-C-2\\facilities.xml.gz");
        for (ActivityFacility a : scenario1.getActivityFacilities().getFacilities().values()) {
            if (!scenario.getActivityFacilities().getFacilities().containsKey(a.getId())) {
                scenario.getActivityFacilities().addActivityFacility(a);
            }
        }
        for (ActivityFacility a : scenario2.getActivityFacilities().getFacilities().values()) {
            if (!scenario.getActivityFacilities().getFacilities().containsKey(a.getId())) {
                scenario.getActivityFacilities().addActivityFacility(a);
            }
        }
        new FacilitiesWriter(scenario.getActivityFacilities()).write("\\\\wsbbrz0283\\mobi\\40_Projekte\\20221010_Valais_2050\\inputs\\facilities_merged.xml.gz");

    }

}
