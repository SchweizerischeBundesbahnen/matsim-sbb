package ch.sbb.matsim.projects.basel;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class AggregateTripsAroundStation {
    static void main() {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile("/Volumes/mobi/40_Projekte/20251007_Prognose_LFP26/2050/sim/M51205025pct.0/output/M51205025pct.0.output_transitSchedule.xml.gz");
        String filename1 = "/Volumes/mobi/40_Projekte/20251007_Prognose_LFP26/2050/sim/M51205025pct.0/output/M51205025pct.0.output_experienced_plans.xml.gz";
        String filename2 = "/Volumes/mobi/40_Projekte/20251007_Prognose_LFP26/2050/sim/M51205025pct.1/output/M51205025pct.1.output_experienced_plans.xml.gz";
        String outputFilePath = "/Volumes/mobi/99_Playgrounds/JB/BS_EA/boarding_alighting_stats_Basel_2050.csv";

        Coord targetCoords = scenario.getTransitSchedule().getFacilities().get(Id.create(1388, TransitStopFacility.class)).getCoord();
        Map<Id<TransitStopFacility>, Map<Id<TransitLine>,BoardingAlightingInfo>> boardingAlightingInfo = new HashMap<>();
        int diameter = 351;
        var targetStops = scenario.getTransitSchedule().getFacilities().values().stream()
                .filter(facility -> CoordUtils.calcEuclideanDistance(facility.getCoord(), targetCoords) < diameter)
                .map(TransitStopFacility::getId)
                .collect(Collectors.toSet());
        for (String filename : List.of(filename1, filename2)) {
            new PopulationReader(scenario).readFile(filename);
            scenario.getPopulation().getPersons()
                    .values()
                    .stream()
                    .map(person -> person.getSelectedPlan())
                    .flatMap(plan -> TripStructureUtils.getLegs(plan).stream())
                    .filter(leg -> leg.getRoute() instanceof TransitPassengerRoute)
                    .map(leg -> (TransitPassengerRoute) leg.getRoute())
                    .filter(route -> targetStops.contains(route.getAccessStopId()) || targetStops.contains(route.getEgressStopId()))
                    .forEach(route -> {
                        if (targetStops.contains(route.getAccessStopId()))     {
                            boardingAlightingInfo.computeIfAbsent(route.getAccessStopId(), k -> new HashMap<>())
                                    .computeIfAbsent(route.getLineId(), k -> new BoardingAlightingInfo(new AtomicInteger(), new AtomicInteger()))
                                    .boardingCount.incrementAndGet();
                        }
                        if (targetStops.contains(route.getEgressStopId()))     {
                            boardingAlightingInfo.computeIfAbsent(route.getEgressStopId(), k -> new HashMap<>())
                                    .computeIfAbsent(route.getLineId(), k -> new BoardingAlightingInfo(new AtomicInteger(), new AtomicInteger()))
                                    .alightingCount.incrementAndGet();
                        }

                    });
                scenario.getPopulation().getPersons().clear();
        }




        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFilePath))) {
            // Write CSV Header
            writer.write("StopId;StopName;X;Y;LineId;BoardingCount;AlightingCount\n");

            // Iterate over nested map
            for (Map.Entry<Id<TransitStopFacility>, Map<Id<TransitLine>, BoardingAlightingInfo>> stopEntry : boardingAlightingInfo.entrySet()) {
                String stopId = stopEntry.getKey().toString();
                var stop = scenario.getTransitSchedule().getFacilities().get(stopEntry.getKey());

                for (Map.Entry<Id<TransitLine>, BoardingAlightingInfo> lineEntry : stopEntry.getValue().entrySet()) {
                    String lineId = lineEntry.getKey().toString();

                    // Extract integers from AtomicIntegers using the record accessors
                    int boardings = lineEntry.getValue().boardingCount().get();
                    int alightings = lineEntry.getValue().alightingCount().get();
                    // Write row
                    writer.write(stopId + ";"+stop.getName()+";"+stop.getCoord().getX()+";"+stop.getCoord().getY()+";" + lineId + ";" + boardings*2 + ";" + alightings*2 + "\n");
                }
            }
            System.out.println("Successfully wrote data to: " + outputFilePath);

        } catch (IOException e) {
            System.err.println("Error writing to CSV file: " + e.getMessage());
            e.printStackTrace();
        }
    }


    record BoardingAlightingInfo(AtomicInteger boardingCount, AtomicInteger alightingCount) {}

}
