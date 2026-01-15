package ch.sbb.matsim.projects.postauto;

import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.locationtech.jts.geom.MultiPolygon;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.GeoFileReader;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;

import java.util.Collection;

public class GenerateDRTStopsFromShape {
    static void main() {
        Collection<SimpleFeature> features = GeoFileReader.getAllFeatures("/Volumes/mobi/40_Projekte/20221221_Postauto_OnDemand/20260112_Olten_Aarau/area/drt-area.shp");
        Scenario scenario2 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile("/Volumes/mobi/40_Projekte/20221221_Postauto_OnDemand/20260112_Olten_Aarau/sim/ref/output/ref.output_transitSchedule.xml.gz");
        scenario.getTransitSchedule().getFacilities().values().stream()
                .filter(transitStopFacility -> isInsideAnyFeature(transitStopFacility.getCoord(), features))
                .forEach(transitStopFacility -> scenario2.getTransitSchedule().addStopFacility(scenario2.getTransitSchedule().getFactory().createTransitStopFacility(transitStopFacility.getId(),transitStopFacility.getCoord(),false))
                );
        new MatsimNetworkReader(scenario2.getNetwork()).readFile("/Volumes/mobi/40_Projekte/20221221_Postauto_OnDemand/20260112_Olten_Aarau/streets/output/network.xml.gz");
        scenario2.getTransitSchedule().getFacilities().values().forEach(transitStopFacility ->
                    transitStopFacility.setLinkId(NetworkUtils.getNearestLink(scenario2.getNetwork(), transitStopFacility.getCoord()).getId()));
        new TransitScheduleWriter(scenario2.getTransitSchedule()).writeFile("/Volumes/mobi/40_Projekte/20221221_Postauto_OnDemand/20260112_Olten_Aarau/sim/5veh/drt-stops.xml");

    }

    private static boolean isInsideAnyFeature(Coord coord, Collection<SimpleFeature> features ) {
        var point = MGC.coord2Point(coord);
        for (SimpleFeature feature : features) {
            MultiPolygon polygon = (MultiPolygon) feature.getDefaultGeometry();
             if (polygon.intersects(point)) {
                return true;
            }
        }
        return false;
    }

}
