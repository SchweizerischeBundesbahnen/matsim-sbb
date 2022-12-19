package ch.sbb.matsim.projects.elm;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.routing.SBBAnalysisMainModeIdentifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public final class VeloAtStation {

    private final static int distance = 300;
    private static String plansFile1 = "Z:/99_Playgrounds/MD/MOBI33IT.output_experienced_plans.xml.gz";
    private static String plansFile2 = "Z:/99_Playgrounds/MD/MOBI33IT.output_experienced_plans.xml.gz";
    private static String transitFile = "Z:/99_Playgrounds/MD/ELM/";
    private static String outputFile = "Z:/99_Playgrounds/MD/ELM/VeloPerStop.csv";
    private static double sampleSize = 0.1;
    private static String year = "2017";

    private VeloAtStation() {
    }

    public static void main(String[] args) {

        if (args != null) {
            plansFile1 = args[0];
            plansFile2 = args[1];
            transitFile = args[2];
            outputFile = args[3];
            sampleSize = Double.parseDouble(args[4]);
            year = args[5];
        }

        readTrips();

    }

    private static void readTrips() {

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(transitFile);

        SBBAnalysisMainModeIdentifier mainModeIdentifier = new SBBAnalysisMainModeIdentifier();

        Map<TransitStopFacility, Integer> veloMap = new HashMap<>();

        for (TransitStopFacility transitStopFacility : scenario.getTransitSchedule().getFacilities().values()) {
            veloMap.put(transitStopFacility, 0);
        }

        Scenario scenario1 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario1).readFile(plansFile1);

        for (Person person : scenario1.getPopulation().getPersons().values()) {

            Plan plan = person.getSelectedPlan();

            for (Trip trip : TripStructureUtils.getTrips(plan)) {

                if (mainModeIdentifier.identifyMainMode(trip.getTripElements()).equals(SBBModes.BIKE)) {

                    Coord endPoint = trip.getDestinationActivity().getCoord();

                    for (TransitStopFacility transitStopFacility : veloMap.keySet()) {
                        if (CoordUtils.calcEuclideanDistance(endPoint, transitStopFacility.getCoord()) < distance) {
                            veloMap.put(transitStopFacility, veloMap.get(transitStopFacility) + 1);
                        }
                    }
                }
            }
        }

        Scenario scenario2 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario2).readFile(plansFile2);

        for (Person person : scenario2.getPopulation().getPersons().values()) {

            Plan plan = person.getSelectedPlan();

            for (Trip trip : TripStructureUtils.getTrips(plan)) {

                if (mainModeIdentifier.identifyMainMode(trip.getTripElements()).equals(SBBModes.BIKE)) {

                    Coord endPoint = trip.getDestinationActivity().getCoord();

                    for (TransitStopFacility transitStopFacility : veloMap.keySet()) {
                        if (CoordUtils.calcEuclideanDistance(endPoint, transitStopFacility.getCoord()) < distance) {
                            veloMap.put(transitStopFacility, veloMap.get(transitStopFacility) + 1);
                        }
                    }
                }
            }
        }

        String[] header = {"Stop_Nummer", "Velos_" + year};
        try (CSVWriter csvWriter = new CSVWriter("",  header, outputFile)) {
            for (Entry<TransitStopFacility, Integer> entry : veloMap.entrySet()) {
                csvWriter.set("Stop_Nummer", entry.getKey().getAttributes().getAttribute("06_Stop_Area_No").toString());
                csvWriter.set("Velos_" + year, Integer.toString((int) (entry.getValue()/sampleSize)));
                csvWriter.writeRow();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
