package ch.sbb.matsim.projects.fourseasons;

import ch.sbb.matsim.config.variables.Variables;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;

public class AddAdditionalLineAttributes {
    static void main() {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile("/Volumes/mobi/40_Projekte/20260305_AD_Szenarien/pt/vivaldi/orig/transitSchedule_merged.xml.gz");
        for (TransitLine line : scenario.getTransitSchedule().getTransitLines().values()) {
            String linename = line.getId().toString();
            if (linename.contains("AX") || linename.contains("Nebenlinien") || linename.contains("RX") || (linename.startsWith("SBB_AK35k") && (linename.contains("-D-")||linename.contains("-R-")||linename.contains("-C-"))))
            {
                line.getRoutes().values().forEach(transitRoute -> transitRoute.getAttributes().putAttribute(Variables.SPARTE,"RV"));
            } else if ((linename.contains("MX") || linename.contains("SX") || (linename.startsWith("SBB_AK35k") && (linename.contains("-A-")||linename.contains("-B-")||linename.contains("-H-")||linename.contains("-I-")))))
            {
                line.getRoutes().values().forEach(transitRoute -> transitRoute.getAttributes().putAttribute(Variables.SPARTE,"FV"));
            }
        }
        new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile("/Volumes/mobi/40_Projekte/20260305_AD_Szenarien/pt/vivaldi/transitSchedule.xml.gz");
    }
}
