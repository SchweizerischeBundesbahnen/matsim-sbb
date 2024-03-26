package ch.sbb.matsim.preparation.cutter;

import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.MatsimVehicleReader;
import org.matsim.vehicles.MatsimVehicleWriter;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class TransitscheduleCutter {

    public static void main(String[] args) {
        String inputSchedule = "\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_4.0\\2020\\pt\\NPVM2020\\output\\transitSchedule.xml.gz";
        String outputSchedule = "c:\\devsbb\\transitSchedule.xml.gz";
        String outputNetwork = "c:\\devsbb\\transitNetwork.xml.gz";
        String outputVehicles = "c:\\devsbb\\transitVehicles.xml.gz";
        String inputNetwork = "\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_4.0\\2020\\pt\\NPVM2020\\output\\transitNetwork.xml.gz";
        String zonesFile = "\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_4.0\\2017\\plans\\3.3.2017.7.100pct\\mobi-zones.shp";
        String inputVehicles = "\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_4.0\\2020\\pt\\NPVM2020\\output\\transitVehicles.xml.gz";

        String attribute = "kt_name";
        String attributeValue = "AI";
        Zones zones = ZonesLoader.loadZones("", zonesFile);

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimVehicleReader(scenario.getTransitVehicles()).readFile(inputVehicles);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(inputNetwork);
        new TransitScheduleReader(scenario).readFile(inputSchedule);
        Set<Id<TransitStopFacility>> stopsInArea = scenario.getTransitSchedule()
                .getFacilities()
                .values()
                .stream()
                .filter(transitStopFacility -> {
                    var zone = zones.findZone(transitStopFacility.getCoord());
                    if (zone != null) {
                        return (String.valueOf(zone.getAttribute(attribute)).equals(attributeValue));
                    }
                    return false;
                })
                .map(transitStopFacility -> transitStopFacility.getId())
                .collect(Collectors.toSet());

        Set<TransitLine> linesToDelete = scenario.getTransitSchedule()
                .getTransitLines()
                .values()
                .stream()
                .filter(transitLine -> transitLine.getRoutes().values().stream().noneMatch(transitRoute -> transitRoute.getStops().stream().map(a -> a.getStopFacility().getId()).anyMatch(b -> stopsInArea.contains(b))))
                .collect(Collectors.toSet());
        linesToDelete.forEach(line -> scenario.getTransitSchedule().removeTransitLine(line));
        Set<Id<TransitStopFacility>> stopsToKeep = scenario.getTransitSchedule()
                .getTransitLines()
                .values()
                .stream().flatMap(transitLine -> transitLine.getRoutes().values().stream())
                .flatMap(transitRoute -> transitRoute.getStops().stream())
                .map(transitRouteStop -> transitRouteStop.getStopFacility().getId())
                .collect(Collectors.toSet());
        Set<TransitStopFacility> stopsToDelete = scenario.getTransitSchedule()
                .getFacilities().values()
                .stream().filter(transitStopFacility -> !stopsToKeep.contains(transitStopFacility.getId()))
                .collect(Collectors.toSet());
        stopsToDelete.forEach(stop -> scenario.getTransitSchedule().removeStopFacility(stop));
        new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(outputSchedule);
        Set<Id<Link>> linksToKeep = new HashSet<>();
        scenario.getTransitSchedule().getTransitLines().values().stream().flatMap(transitLine -> transitLine.getRoutes().values().stream())
                .map(transitRoute -> transitRoute.getRoute())
                .forEach(networkRoute -> {
                    linksToKeep.add(networkRoute.getStartLinkId());
                    linksToKeep.add(networkRoute.getEndLinkId());
                    linksToKeep.addAll(networkRoute.getLinkIds());
                });
        Set<Id<Link>> linksToRemove = scenario.getNetwork().getLinks().values().stream().filter(link -> !linksToKeep.contains(link.getId())).map(link -> link.getId()).collect(Collectors.toSet());
        linksToRemove.forEach(linkId -> scenario.getNetwork().removeLink(linkId));
        Set<Id<Node>> nodesToRemove = scenario.getNetwork().getNodes().values().stream().filter(node -> node.getOutLinks().isEmpty() && node.getInLinks().isEmpty()).map(node -> node.getId()).collect(Collectors.toSet());
        nodesToRemove.forEach(nodeId -> scenario.getNetwork().removeNode(nodeId));
        new NetworkWriter(scenario.getNetwork()).write(outputNetwork);
        var vehiclesToKeep = scenario.getTransitSchedule().getTransitLines().values().stream().flatMap(transitLine -> transitLine.getRoutes().values().stream())
                .flatMap(transitRoute -> transitRoute.getDepartures().values().stream())
                .map(departure -> departure.getVehicleId())
                .collect(Collectors.toSet());
        var vehiclesToRemove = scenario.getTransitVehicles().getVehicles().keySet().stream().filter(v -> !vehiclesToKeep.contains(v)).collect(Collectors.toSet());
        vehiclesToRemove.forEach(v -> scenario.getTransitVehicles().removeVehicle(v));
        new MatsimVehicleWriter(scenario.getTransitVehicles()).writeFile(outputVehicles);
    }
}
