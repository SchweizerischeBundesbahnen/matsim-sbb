package ch.sbb.matsim.utils;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.algorithms.PersonAlgorithm;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacility;

import java.util.*;

/**
 * @author jbischoff / SBB
 */
public class CreateCloneAgents {

    private static final String SOURCEPOP = "C:\\devsbb\\population.xml.gz";
    private static final String DESTINATIONPOP = "C:\\devsbb\\sg_pupils.xml.gz";
    private static final String ACTIVITYTYPE = "education";
    private static final int NOOFAGENTS = 1385;
    private static final Id<ActivityFacility> FACILITYID = Id.create("B_309093", ActivityFacility.class);

    private Random random = MatsimRandom.getRandom();

    public static void main(String[] args) {
        new CreateCloneAgents().run();
    }

    private void run() {
        List<Person> sourcepersons = new ArrayList<>(readSourcePopulation().getPersons().values());
        System.out.println("Found " + sourcepersons.size() + " clone candidates.");
        Collections.shuffle(sourcepersons, random);
        LinkedList<Person> personQueue = new LinkedList<>(sourcepersons);
        Population population = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        for (int i = 0; i < NOOFAGENTS; i++) {
            if (personQueue.isEmpty()) {
                personQueue = new LinkedList<>(sourcepersons);
            }
            Person p = personQueue.poll();
            copyPerson(population, p, i);
        }
        new PopulationWriter(population).write(DESTINATIONPOP);


    }

    private void copyPerson(Population population, Person p, int i) {
        Id<Person> newId = Id.createPersonId(p.getId().toString() + "_clone_" + i);
        Person newPerson = population.getFactory().createPerson(newId);
        newPerson.addPlan(p.getSelectedPlan());
        for (Map.Entry<String, Object> e : p.getAttributes().getAsMap().entrySet()) {
            newPerson.getAttributes().putAttribute(e.getKey(), e.getValue());
        }
        population.addPerson(newPerson);
    }

    private Population readSourcePopulation() {
        Population population = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        StreamingPopulationReader spr = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
        spr.addAlgorithm(new PersonAlgorithm() {
            @Override
            public void run(Person person) {
                Plan plan = person.getSelectedPlan();
                if (TripStructureUtils.getActivities(plan, TripStructureUtils.StageActivityHandling.ExcludeStageActivities).stream().filter(a -> a.getFacilityId() != null).anyMatch(a ->
                        (a.getFacilityId().equals(FACILITYID) && a.getType().startsWith(ACTIVITYTYPE)))) {
                    population.addPerson(person);
                }

            }
        });
        spr.readFile(SOURCEPOP);
        return population;
    }
}
