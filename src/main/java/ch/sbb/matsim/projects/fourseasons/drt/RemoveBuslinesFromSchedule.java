package ch.sbb.matsim.projects.fourseasons.drt;

import ch.sbb.matsim.config.variables.SBBModes;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.*;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class RemoveBuslinesFromSchedule {

    public static void main(String[] args) {
        String inputSchedule = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240722_Vivaldi_DRT\\pt\\viv-ref\\output\\transitSchedule.xml.gz";
        String outputSchedule = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240722_Vivaldi_DRT\\pt\\viv-nolines\\output\\transitSchedule.xml.gz";
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(inputSchedule);
        Set<Id<TransitStopFacility>> stopsWhereLinesAreRemoved = scenario.getTransitSchedule().getFacilities()
                .values()
                .stream()
                .filter(transitStopFacility -> transitStopFacility.getName() != null && transitStopFacility.getName().startsWith("Uzwil, "))
                .map(Identifiable::getId).collect(Collectors.toSet());
        Set<TransitLine> linesToBeRemoved = new HashSet<>();
        for (var line : scenario.getTransitSchedule().getTransitLines().values()) {
            Set<Id<TransitRoute>> routesToBeRemoved = line.getRoutes().values()
                    .stream()
                    .filter(transitRoute -> transitRoute.getTransportMode().equals(SBBModes.PTSubModes.BUS))
                    .filter(transitRoute -> transitRoute.getStops().stream().anyMatch(transitRouteStop -> stopsWhereLinesAreRemoved.contains(transitRouteStop.getStopFacility().getId())))
                    .map(Identifiable::getId)
                    .collect(Collectors.toSet());
            if (!routesToBeRemoved.isEmpty())
                System.out.println("removing transit routes " + routesToBeRemoved + " from line " + line.getId());
            routesToBeRemoved.forEach(transitRouteId -> line.removeRoute(line.getRoutes().get(transitRouteId)));
            if (line.getRoutes().isEmpty()) {
                linesToBeRemoved.add(line);
            }
        }
        linesToBeRemoved.forEach(transitLine -> scenario.getTransitSchedule().removeTransitLine(transitLine));

        System.out.println("Lines Removed completely: " + linesToBeRemoved.stream().map(Identifiable::getId).collect(Collectors.toSet()));
        new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(outputSchedule);

    }
}
