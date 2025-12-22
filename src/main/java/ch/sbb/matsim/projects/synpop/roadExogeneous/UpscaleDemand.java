package ch.sbb.matsim.projects.synpop.roadExogeneous;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.projects.synpop.SingleTripAgentToCSVConverter;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.IOException;
import java.util.*;

public class UpscaleDemand {

    private final Population population;

    private final double globalFactor;
    private final Random random = MatsimRandom.getRandom();

    public UpscaleDemand(Population population, double globalFactor) {
        this.population = population;
        this.globalFactor = globalFactor;
    }

    public static void main(String[] args) throws IOException {
        String inputPopulation = "/Users/joschka.bischoff/106_release.plans.xml.gz";
        String outputPopulation = "/Volumes/mobi/40_Projekte/20251007_Prognose_LFP26/2050/plans_exogenous/csv/sbb_gmbh_2050.xml.gz";
        Scenario scenario = ScenarioUtils.loadScenario(ConfigUtils.createConfig());
        Scenario scenario2 = ScenarioUtils.loadScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario2).readFile(inputPopulation);
        for (var person : scenario2.getPopulation().getPersons().values()) {
                if (person.getId().toString().startsWith("cb_rail_add_")) {
                    scenario.getPopulation().addPerson(person);
                }
        }
        UpscaleDemand adjustTourists = new UpscaleDemand(scenario.getPopulation(), 1.49);
        adjustTourists.adjust();

        new PopulationWriter(scenario.getPopulation()).write(outputPopulation);
        SingleTripAgentToCSVConverter.writeSingleTripsAsCSV(outputPopulation.replace("xml.gz", "csv"), scenario.getPopulation());
    }

    private void adjust() {

        Map<Id<Person>, Double> requiredClones = new HashMap<>();
        Set<Id<Person>> toBeRemoved = new HashSet<>();
        for (Person person : population.getPersons().values()) {
            Plan plan = person.getSelectedPlan();
            double requiredClonesForPerson = determineNumberOfClones(plan);
            if (requiredClonesForPerson > 0.0) {
                requiredClones.put(person.getId(), requiredClonesForPerson);
            } else if (requiredClonesForPerson < 0.0) {
                toBeRemoved.add(person.getId());
            }
        }
        createClones(requiredClones);
        toBeRemoved.forEach(personId -> population.removePerson(personId));
        population.getPersons().values().forEach(person -> PopulationUtils.putSubpopulation(person, Variables.TOURISM_RAIL));
    }

    private void createClones(Map<Id<Person>, Double> requiredClones) {
        for (var personClones : requiredClones.entrySet()) {
            Person originalPerson = population.getPersons().get(personClones.getKey());
            double clonesLeft = personClones.getValue();
            for (int j = 0; j <= personClones.getValue(); j++) {
                if (clonesLeft > 1.0) {
                    clonePerson(originalPerson, j);
                    clonesLeft--;
                } else if (clonesLeft > 0.0) {
                    if (random.nextDouble() < clonesLeft) {
                        clonePerson(originalPerson, j);
                        clonesLeft--;
                    }
                }

            }

        }
    }

    private void clonePerson(Person originalPerson, int j) {
        var newPersonId = Id.createPersonId(originalPerson.getId().toString() + "_" + j+1);
        Person newPerson = population.getFactory().createPerson(newPersonId);
        population.addPerson(newPerson);
        Plan plan = population.getFactory().createPlan();
        newPerson.addPlan(plan);
        for (PlanElement pe : originalPerson.getSelectedPlan().getPlanElements()) {
            if (pe instanceof Activity activity) {
                Activity newActivity = population.getFactory().createActivityFromCoord(activity.getType(), activity.getCoord());
                if (activity.getEndTime().isDefined()) {
                    newActivity.setEndTime(activity.getEndTime().seconds() - 1800 + random.nextInt(3600));
                }
                plan.addActivity(newActivity);

            } else if (pe instanceof Leg leg) {
                plan.addLeg(leg);
            }
        }


    }

    private double determineNumberOfClones(Plan plan) {
        double numberOfClones = globalFactor - 1.0;
        return numberOfClones;


    }
}
