package ch.sbb.matsim.utils;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

public class ComparePTLineLenghts {

    public static void main(String[] args) {
        String scheduleBase = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240209_Cernier\\pt\\Cernier\\output\\transitSchedule.xml.gz";
        String networkBase = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240209_Cernier\\pt\\Cernier\\output\\transitNetwork.xml.gz";
        Scenario scenarioBase = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimNetworkReader(scenarioBase.getNetwork()).readFile(networkBase);
        new TransitScheduleReader(scenarioBase).readFile(scheduleBase);

        String scheduleVar = "C:\\devsbb\\tmp\\oev-exporter\\output\\transitSchedule.xml.gz";
        String networkVar = "C:\\devsbb\\tmp\\oev-exporter\\output\\transitNetwork.xml.gz";
        Scenario scenarioVar = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimNetworkReader(scenarioVar.getNetwork()).readFile(networkVar);
        new TransitScheduleReader(scenarioVar).readFile(scheduleVar);

        for (var line : scenarioBase.getTransitSchedule().getTransitLines().values()) {
            for (var route : line.getRoutes().values()) {
                double distanceBase = RouteUtils.calcDistanceExcludingStartEndLink(route.getRoute(), scenarioBase.getNetwork());
                var routeVar = scenarioVar.getTransitSchedule().getTransitLines().get(line.getId()).getRoutes().get(route.getId());
                double distanceVar = RouteUtils.calcDistanceExcludingStartEndLink(routeVar.getRoute(), scenarioVar.getNetwork());
                if (Math.abs(distanceBase - distanceVar) > 100) {
                    System.out.println(line.getId() + "\t" + route.getId() + "\t" + distanceBase + "\t" + distanceVar);
                }
            }
        }
    }
}
