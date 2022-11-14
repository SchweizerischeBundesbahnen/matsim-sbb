package ch.sbb.matsim.utils;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.tabularFileParser.TabularFileParser;
import org.matsim.core.utils.io.tabularFileParser.TabularFileParserConfig;

import java.util.HashMap;
import java.util.Map;

public class ScaleAgentTrips {

    Map<Id<Person>, Map<Integer, Integer>> scalingPerPerson = new HashMap<>();
    private PopulationFactory populationFactory;

    public static void main(String[] args) {
        String inputPlans = args[0];
        String scaleFile = args[1];
        String outputPlansFile = args[2];

        new ScaleAgentTrips().run(inputPlans, scaleFile, outputPlansFile);
    }

    private void run(String inputPlans, String scaleFile, String outputPlansFile) {
        readScaleFile(scaleFile);

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        this.populationFactory = scenario.getPopulation().getFactory();
        StreamingPopulationReader spr = new StreamingPopulationReader(scenario);
        StreamingPopulationWriter spw = new StreamingPopulationWriter();
        spw.startStreaming(outputPlansFile);
        spr.addAlgorithm(person -> {
            var personId = person.getId();
            var mapping = scalingPerPerson.get(personId);
            var trips = TripStructureUtils.getTrips(person.getSelectedPlan());

            int cloneId = 0;
            if (mapping != null) {
                for (Map.Entry<Integer, Integer> e : mapping.entrySet()) {
                    int trip = e.getKey();
                    int scale = e.getValue();
                    for (int i = 0; i < scale; i++) {
                        Person newPerson = cloneTripAndPersonAtts(person, trips.get(trip - 1), cloneId);
                        spw.run(newPerson);
                        cloneId++;

                    }
                }

            }
        });

        spr.readFile(inputPlans);
        spw.closeStreaming();

    }

    private Person cloneTripAndPersonAtts(Person person, TripStructureUtils.Trip trip, int cloneId) {
        Person newPerson = populationFactory.createPerson(Id.createPersonId(person.getId().toString() + "_" + cloneId));
        person.getAttributes().getAsMap().forEach((s, o) -> newPerson.getAttributes().putAttribute(s, o));
        Plan plan = populationFactory.createPlan();
        plan.addActivity(trip.getOriginActivity());
        trip.getTripElements().forEach(planElement -> {
            if (planElement instanceof Activity) {
                plan.addActivity((Activity) planElement);
            } else {
                plan.addLeg((Leg) planElement);
            }
        });

        plan.addActivity(trip.getDestinationActivity());
        newPerson.addPlan(plan);

        return newPerson;
    }

    private void readScaleFile(String scaleFile) {
        TabularFileParserConfig tbc = new TabularFileParserConfig();
        tbc.setFileName(scaleFile);
        tbc.setDelimiterRegex(",");
        new TabularFileParser().parse(tbc, row -> {
            try {
                Id<Person> personId = Id.createPersonId(row[1].replaceAll("\"", ""));
                Integer trip = Integer.parseInt(row[2]);
                Integer scale = Integer.parseInt(row[4]);
                scalingPerPerson.computeIfAbsent(personId, a -> new HashMap<>()).put(trip, scale);
            } catch (Exception e) {
            }
        });


    }

}
