package ch.sbb.matsim.plans;

import ch.sbb.matsim.plans.abm.AbmData;
import ch.sbb.matsim.plans.converter.ABM2MATSim;
import ch.sbb.matsim.plans.discretizer.FacilityDiscretizer;
import ch.sbb.matsim.plans.reader.AbmDataReader;
import ch.sbb.matsim.plans.reader.ScenarioLoader;
import org.matsim.api.core.v01.Scenario;

import java.util.HashMap;

public class PlanGenerator {

    public static void main(String[] args)  {
        HashMap<String, String> abmActs2matsimActs = new HashMap<>();
        abmActs2matsimActs.put("L", "leisure");
        abmActs2matsimActs.put("W", "work");
        abmActs2matsimActs.put("H", "home");
        abmActs2matsimActs.put("E", "education");
        abmActs2matsimActs.put("O", "other");
        abmActs2matsimActs.put("S", "shopping");
        abmActs2matsimActs.put("A", "accompany");

        AbmData abmData = new AbmDataReader("\\\\k13536\\mobi\\plans\\endogenous\\abm\\plan_table.csv").loadABMData();
        Scenario scenario = new ScenarioLoader("\\\\k13536\\mobi\\synpop\\data\\output\\2016\\for_mobi_plans\\v_02").prepareSynpopData(abmData);
        FacilityDiscretizer discretizer = new FacilityDiscretizer(scenario.getActivityFacilities());

        new ABM2MATSim(scenario).processAbmData(discretizer, abmData, abmActs2matsimActs);
    }
}
