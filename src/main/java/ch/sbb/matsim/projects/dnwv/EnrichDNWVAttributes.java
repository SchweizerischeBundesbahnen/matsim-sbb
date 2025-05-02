package ch.sbb.matsim.projects.dnwv;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVReader;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.algorithms.TripsToLegsAlgorithm;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.router.RoutingModeMainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class EnrichDNWVAttributes {
    private final Scenario scenario;
    TripsToLegsAlgorithm legsAlgorithm;
    Map<Id<Person>, TreeMap<Integer, Double>> weightPerTripMap = new HashMap<>();

    public EnrichDNWVAttributes(Scenario scenario, String factorsFile) {
        this.scenario = scenario;
        this.legsAlgorithm = new TripsToLegsAlgorithm(new RoutingModeMainModeIdentifier());

        readFactorsFile(factorsFile);
    }

    public static void main(String[] args) {
        String inputPopulation0 = args[0];
        String inputPopulation1 = args[1];
        String factorsFile = args[2];
        String outputPopulation = args[3];

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(inputPopulation0);


        if (!"-".equals(inputPopulation1)) {
            new PopulationReader(scenario).readFile(inputPopulation1);
        }
        scenario.getPopulation().getPersons().values().forEach(person -> PersonUtils.removeUnselectedPlans(person));

        EnrichDNWVAttributes enrichDNWVAttributes = new EnrichDNWVAttributes(scenario, factorsFile);
        enrichDNWVAttributes.enrichPopulation();
        new PopulationWriter(scenario.getPopulation()).write(outputPopulation);


    }

    private void readFactorsFile(String factorsFile) {
        try (CSVReader reader = new CSVReader(factorsFile, ";")) {
            var line = reader.readLine();
            while (line != null) {
                String personAndTripIdString = line.get("trip_id");
                int lastIndex = personAndTripIdString.lastIndexOf("_");
                if (lastIndex != -1) {
                    var personId = Id.createPersonId(personAndTripIdString.substring(0, lastIndex));
                    int tripId = Integer.parseInt(personAndTripIdString.substring(lastIndex + 1));
                    double weight = Double.parseDouble(line.get("factor"));
                    weightPerTripMap.computeIfAbsent(personId, personId1 -> new TreeMap<>()).put(tripId, weight);

                }

                line = reader.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void enrichPopulation() {
        for (Person person : scenario.getPopulation().getPersons().values()) {
            var factorsMap = this.weightPerTripMap.get(person.getId());
            if (factorsMap != null) {
                var trips = TripStructureUtils.getTrips(person.getSelectedPlan());
                for (var entry : factorsMap.entrySet()) {
                    int tripNo = entry.getKey();
                    TripStructureUtils.Trip trip = trips.get(tripNo - 1);
                    trip.getOriginActivity().getAttributes().putAttribute(Variables.MOBiTripAttributes.DNWV_FACTOR, entry.getValue());
                }
            }
        }
    }

}
