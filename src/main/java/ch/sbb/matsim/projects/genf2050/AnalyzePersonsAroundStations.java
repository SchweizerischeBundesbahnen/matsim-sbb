package ch.sbb.matsim.projects.genf2050;

import ch.sbb.matsim.csv.CSVReader;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class AnalyzePersonsAroundStations {

    public static void main(String[] args) throws IOException {
        double threshold = 750.0;

        CSVReader reader = new CSVReader("\\\\wsbbrz0283\\mobi\\40_Projekte\\20240327_Genf_CityRail\\plans\\vivaldi\\personen_ge.att", ";");
        var line = reader.readLine();
        Map<Id<Person>, Coord> homeLocations = new HashMap<>();
        while (line != null) {
            var personId = Id.createPersonId(line.get("PERSON"));
            Coord coord = new Coord(Double.parseDouble(line.get("XCOORD")), Double.parseDouble(line.get("YCOORD")));
            homeLocations.put(personId, coord);
            line = reader.readLine();

        }

        CSVReader businesseReader = new CSVReader("\\\\wsbbrz0283\\mobi\\40_Projekte\\20240327_Genf_CityRail\\plans\\vivaldi\\business_ge.csv", ";");
        var lineb = businesseReader.readLine();
        Map<String, Business> workLocations = new HashMap<>();
        while (lineb != null) {
            String location = lineb.get("LOCATION");
            Coord coord = new Coord(Double.parseDouble(lineb.get("XCOORD")), Double.parseDouble(lineb.get("YCOORD")));
            workLocations.put(location, new Business(location, coord, Double.parseDouble(lineb.get("JOBS_ENDO")) + Double.parseDouble(lineb.get("JOBS_EXO"))));
            lineb = businesseReader.readLine();

        }

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        List<Id<TransitRoute>> relevantTransitRoutesTram = List.of(Id.create("69815_1_18", TransitRoute.class), Id.create("69814_1_26", TransitRoute.class), Id.create("69812_1_27", TransitRoute.class));
        new TransitScheduleReader(scenario).readFile("\\\\wsbbrz0283\\mobi\\40_Projekte\\20240327_Genf_CityRail\\pt\\AK35_2050_GECityRail_Vivaldi\\output\\transitSchedule.xml.gz");
        Set<TransitStopFacility> allStops = new HashSet<>();
        Set<TransitStopFacility> gcrstops = scenario.getTransitSchedule().getTransitLines().values().stream()
                //.filter(transitLine -> transitLine.getId().toString().startsWith("GCR_"))
                .flatMap(transitLine -> transitLine.getRoutes().values().stream())
                .filter(transitRoute -> relevantTransitRoutesTram.contains(transitRoute.getId()))
                .flatMap(transitRoute -> transitRoute.getStops().stream())
                .map(transitRouteStop -> transitRouteStop.getStopFacility())
                .collect(Collectors.toSet());

        Set<TransitStopFacility> railstops = scenario.getTransitSchedule().getTransitLines().values().stream()
                .flatMap(transitLine -> transitLine.getRoutes().values().stream())
                .filter(transitRoute -> transitRoute.getTransportMode().equals("rail"))
                .flatMap(transitRoute -> transitRoute.getStops().stream())
                .map(transitRouteStop -> transitRouteStop.getStopFacility())
                .collect(Collectors.toSet());
        allStops.addAll(gcrstops);
        allStops.addAll(railstops);
        int persons = 0;
        double jobs = 0;
        for (Coord coord : homeLocations.values()) {
            double closestStopDistance = findClosestStopDistance(coord, allStops);
            if (closestStopDistance < threshold) {
                persons++;
            }
        }
        for (Business business : workLocations.values()) {
            double closestStopDistance = findClosestStopDistance(business.coord, allStops);
            if (closestStopDistance < threshold) {
                jobs += business.jobs;
            }
        }
        System.out.println("persons rail\t" + persons);
        System.out.println("jobs rail\t" + jobs);


    }

    private static double findClosestStopDistance(Coord coord, Set<TransitStopFacility> railStops) {
        double closest = Double.MAX_VALUE;
        for (TransitStopFacility facility : railStops) {
            double distance = CoordUtils.calcEuclideanDistance(coord, facility.getCoord());
            if (distance < closest) {
                closest = distance;
            }
        }
        return closest;
    }

    record Business(String id, Coord coord, double jobs) {
    }

}
