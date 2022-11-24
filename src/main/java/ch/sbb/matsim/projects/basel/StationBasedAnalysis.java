package ch.sbb.matsim.projects.basel;

import ch.sbb.matsim.analysis.tripsandlegsanalysis.RailTripsAnalyzer;
import org.apache.commons.lang3.mutable.MutableInt;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileWriter;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.opengis.feature.simple.SimpleFeature;

import java.util.*;
import java.util.stream.Collectors;

public class StationBasedAnalysis {
    public static final int CELLSZE = 5000;

    private final Map<Id<TransitStopFacility>, Map<String, PreparedGeometry>> stations;
    private final Map<Id<TransitStopFacility>, Map<String, MutableInt>> stationDirectionUse;
    private final Scenario scenario;
    private final double multiplicator;
    private final RailTripsAnalyzer railTripsAnalyzer;

    public StationBasedAnalysis(List<Id<TransitStopFacility>> stations, Scenario scenario, double multiplicator, RailTripsAnalyzer railTripsAnalyzer) {
        this.stations = stations.stream().collect(Collectors.toMap((s) -> s, (s) -> new HashMap<>()));
        this.stationDirectionUse = stations.stream().collect(Collectors.toMap((s) -> s, (s) -> new HashMap<>()));
        this.scenario = scenario;
        this.multiplicator = multiplicator;
        this.railTripsAnalyzer = railTripsAnalyzer;
    }

    public static void main(String[] args) {
        List<Id<TransitStopFacility>> stations = List.of(Id.create(100001204, TransitStopFacility.class),
                Id.create(1388, TransitStopFacility.class),
                Id.create(19054489, TransitStopFacility.class),
                Id.create(1218, TransitStopFacility.class),
                Id.create(3310, TransitStopFacility.class),
                Id.create(899, TransitStopFacility.class),
                Id.create(20000002, TransitStopFacility.class),
                Id.create(727, TransitStopFacility.class),
                Id.create(19054496, TransitStopFacility.class),
                Id.create(1397, TransitStopFacility.class),
                Id.create(19054490, TransitStopFacility.class)
        );
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        String transitScheduleFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220412_Basel_2050\\sim\\0.6-v100.1-10pct\\output\\v100.1.output_transitSchedule.xml.gz";
        new MatsimNetworkReader(scenario.getNetwork()).readFile("\\\\wsbbrz0283\\mobi\\40_Projekte\\20220412_Basel_2050\\sim\\0.6-v100.1-10pct\\output\\v100.1.output_network.xml.gz");
        new TransitScheduleReader(scenario).readFile(transitScheduleFile);
        new PopulationReader(scenario).readFile("\\\\wsbbrz0283\\mobi\\40_Projekte\\20220412_Basel_2050\\sim\\2.1-v300-50pct\\output_slice0\\v300.1.output_experienced_plans.xml.gz");
        new PopulationReader(scenario).readFile("\\\\wsbbrz0283\\mobi\\40_Projekte\\20220412_Basel_2050\\sim\\2.1-v300-50pct\\output_slice1\\v300.1.output_experienced_plans.xml.gz");

        RailTripsAnalyzer analyzer = new RailTripsAnalyzer(scenario.getTransitSchedule(), scenario.getNetwork());
        StationBasedAnalysis stationBasedAnalysis = new StationBasedAnalysis(stations, scenario, 2, analyzer);
        stationBasedAnalysis.prepareStations();
        stationBasedAnalysis.analyse();
        stationBasedAnalysis.writeShapes("\\\\wsbbrz0283\\mobi\\40_Projekte\\20220412_Basel_2050\\sim\\0.6-v100.1-10pct\\output\\test\\");
    }

    private void writeShapes(String outputFolder) {
        for (var facId : this.stations.keySet()) {
            String stationName = scenario.getTransitSchedule().getFacilities().get(facId).getName();

            String fileName = outputFolder + stationName + ".shp";
            SimpleFeatureTypeBuilder simpleFeatureBuilder = new SimpleFeatureTypeBuilder();
            simpleFeatureBuilder.setCRS(MGC.getCRS("CH1903_LV03_Plus_GT"));
            simpleFeatureBuilder.setName("stationuse");
            simpleFeatureBuilder.add("the_geom", Polygon.class);
            simpleFeatureBuilder.add("zoneId", String.class);
            simpleFeatureBuilder.add("Usage", Integer.class);
            SimpleFeatureBuilder builder = new SimpleFeatureBuilder(simpleFeatureBuilder.buildFeatureType());

            Collection<SimpleFeature> features = new ArrayList<>();
            int i = 0;
            for (var feature : stations.get(facId).entrySet()) {
                Object[] stationFeatureAttribute = new Object[3];
                PreparedGeometry geo = feature.getValue();
                stationFeatureAttribute[0] = geo;
                stationFeatureAttribute[1] = feature.getKey();
                stationFeatureAttribute[2] = stationDirectionUse.get(facId).get(feature.getKey()).intValue();


                try {
                    features.add(builder.buildFeature(Integer.toString(i), stationFeatureAttribute));
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }
                i++;
            }
            ShapeFileWriter.writeGeometries(features, fileName);
            System.out.println(stationName);
            stationDirectionUse.get(facId).forEach((k, v) -> System.out.println(k + "\t" + v.toString()));

        }
    }

    private void analyse() {
        for (Person p : scenario.getPopulation().getPersons().values()) {
            Plan plan = p.getSelectedPlan();
            for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(plan)) {
                var railOd = railTripsAnalyzer.getOriginDestination(trip);
                if (railOd != null) {
                    if (stations.containsKey(railOd.getFirst())) {
                        handleAccessEgress(railOd.getFirst(), trip.getOriginActivity());
                    }
                    if (stations.containsKey(railOd.getSecond())) {
                        handleAccessEgress(railOd.getSecond(), trip.getDestinationActivity());
                    }
                }

            }
        }
    }

    private void handleAccessEgress(Id<TransitStopFacility> stopFacilityId, Activity destinationActivity) {
        var stationMap = stations.get(stopFacilityId);
        Point coord = MGC.coord2Point(destinationActivity.getCoord());

        String zone = stationMap.entrySet().stream().filter((s) -> s.getValue().intersects(coord)).map(s -> s.getKey()).findAny().orElse("");
        var count = this.stationDirectionUse.get(stopFacilityId).get(zone);
        if (count != null) {
            count.add(this.multiplicator);
        }
    }


    private void prepareStations() {
        for (var f : this.stations.keySet()) {
            GeometryFactory gf = new GeometryFactory();
            PreparedGeometryFactory preparedGeometryFactory = new PreparedGeometryFactory();
            Map<String, PreparedGeometry> grid = new HashMap<>();
            Coord stopCoord = scenario.getTransitSchedule().getFacilities().get(f).getCoord();
            Coordinate center = new Coordinate(stopCoord.getX(), stopCoord.getY());
            Coordinate topLeft = new Coordinate(stopCoord.getX() - CELLSZE, stopCoord.getY() + CELLSZE);
            Coordinate topRight = new Coordinate(stopCoord.getX() + CELLSZE, stopCoord.getY() + CELLSZE);
            Coordinate bottomRight = new Coordinate(stopCoord.getX() + CELLSZE, stopCoord.getY() - CELLSZE);
            Coordinate bottomLeft = new Coordinate(stopCoord.getX() - CELLSZE, stopCoord.getY() - CELLSZE);
            Coordinate[] up = {center, topLeft, topRight, center};
            Coordinate[] left = {center, topLeft, bottomLeft, center};
            Coordinate[] right = {center, topRight, bottomRight, center};
            Coordinate[] low = {center, bottomLeft, bottomRight, center};

            Polygon north = gf.createPolygon(up);
            Polygon east = gf.createPolygon(right);
            Polygon west = gf.createPolygon(left);
            Polygon south = gf.createPolygon(low);

            grid.put("North", preparedGeometryFactory.create(north));
            grid.put("South", preparedGeometryFactory.create(south));
            grid.put("West", preparedGeometryFactory.create(west));
            grid.put("East", preparedGeometryFactory.create(east));
            Map<String, MutableInt> directionUse = grid.keySet()
                    .stream()
                    .collect(Collectors.toMap((k) -> k, (k) -> new MutableInt()));

            this.stations.put(f, grid);
            this.stationDirectionUse.put(f, directionUse);

        }
    }
}
