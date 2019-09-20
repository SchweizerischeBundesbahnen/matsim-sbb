package ch.sbb.matsim.rideshare.utils;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class AddIntermodalScheduleAttributes {
    public static void main(String[] args) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile("\\\\k13536\\mobi\\40_Projekte\\20190913_Ridesharing\\scenarios\\testscenario\\transitschedule.xml");
        scenario.getTransitSchedule().getFacilities().get(Id.create("3", TransitStopFacility.class)).getAttributes().putAttribute("drtfeeder", 1);
        scenario.getTransitSchedule().getFacilities().get(Id.create("3", TransitStopFacility.class)).getAttributes().putAttribute("drtfeeder_linkId", 4241);
        new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile("\\\\k13536\\mobi\\40_Projekte\\20190913_Ridesharing\\scenarios\\testscenario\\transitscheduledrt.xml");
    }
}
