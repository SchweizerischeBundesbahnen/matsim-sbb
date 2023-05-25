package ch.sbb.matsim.projects.postauto;

import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.utils.gis.shp2matsim.ShpGeometryUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class GenerateAppenzellDRTStops {

    public static void main(String[] args) throws IOException {
        String areaFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20221221_Postauto_OnDemand\\20221221_Appenzell\\drt-area-gross\\drt-area.shp";
        String existingStops = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20221221_Postauto_OnDemand\\20221221_Appenzell\\drt-area-gross\\drt-stops.csv";
        String networkFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20221221_Postauto_OnDemand\\20221221_Appenzell\\streets\\output\\network.xml.gz";
        String stopsFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20221221_Postauto_OnDemand\\20221221_Appenzell\\drt-area-gross\\drt-stops.xml";

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);
        final List<PreparedGeometry> preparedGeometries = ShpGeometryUtils.loadPreparedGeometries(
                ConfigGroup.getInputFileURL(scenario.getConfig().getContext(), areaFile));

        var existingStopCoords = Files.lines(Path.of(existingStops)).map(s -> {
                    var split = s.split(";");
                    return new Coord(Double.parseDouble(split[0]), Double.parseDouble(split[1]));
                })
                .collect(Collectors.toSet());
        var stopsInArea = scenario.getNetwork().getLinks().values()
                .stream()
                .filter(link -> ShpGeometryUtils.isCoordInPreparedGeometries(link.getToNode().getCoord(),
                        preparedGeometries))
                .map(link -> {
                    var stop = scenario.getTransitSchedule().getFactory().createTransitStopFacility(Id.create(link.getId(), TransitStopFacility.class), link.getToNode().getCoord(), false);
                    stop.setLinkId(link.getId());
                    return stop;
                })
                .collect(Collectors.toSet());
        var stopsFromCoord = existingStopCoords.stream()
                .map(coord -> NetworkUtils.getNearestLink(scenario.getNetwork(), coord))
                .map(link -> {
                    var stop = scenario.getTransitSchedule().getFactory().createTransitStopFacility(Id.create(link.getId(), TransitStopFacility.class), link.getToNode().getCoord(), false);
                    stop.setLinkId(link.getId());
                    return stop;
                }).collect(Collectors.toSet());
        stopsInArea.forEach(s -> scenario.getTransitSchedule().addStopFacility(s));
        stopsFromCoord.stream().filter(s -> !scenario.getTransitSchedule().getFacilities().containsKey(s.getId())).forEach(s -> scenario.getTransitSchedule().addStopFacility(s));

        new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(stopsFile);
    }


}
