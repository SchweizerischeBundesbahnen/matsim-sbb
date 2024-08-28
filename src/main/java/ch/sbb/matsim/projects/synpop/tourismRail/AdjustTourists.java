package ch.sbb.matsim.projects.synpop.tourismRail;

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

public class AdjustTourists {

    private final Zones zones;
    private final Population population;
    private final List<Id<Zone>> airportZones = List.of(Id.create(662304010, Zone.class), Id.create(6204018, Zone.class));
    private final List<Id<Zone>> specialZones = List.of(
            Id.create(79401002, Zone.class),
            Id.create(378701004, Zone.class),
            Id.create(378701007, Zone.class),
            Id.create(378701005, Zone.class),
            Id.create(120201001, Zone.class)
    );
    private final double globalFactor;
    private final Random random = MatsimRandom.getRandom();

    public AdjustTourists(Population population, Zones zones, double globalFactor) {
        this.population = population;
        this.zones = zones;
        this.globalFactor = globalFactor;
    }

    public static void main(String[] args) throws IOException {
        String inputPopulation = "\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_4.0\\2017\\plans_exogeneous\\tourism_rail\\100pct\\plans.xml.gz";
        String zonesFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans\\20_ebikes_imp\\output\\20_ebikes_imp.mobi-zones.shp";
        String outputPopulation = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\tourism_rail\\tourism_rail.xml.gz";
        Scenario scenario = ScenarioUtils.loadScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(inputPopulation);
        var zones = ZonesLoader.loadZones("id", zonesFile);
        AdjustTourists adjustTourists = new AdjustTourists(scenario.getPopulation(), zones, 4.0);
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
            int i = 1;
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
        var newPersonId = Id.createPersonId(originalPerson.getId().toString() + "_" + j);
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
        Coord fromCoord = ((Activity) plan.getPlanElements().get(0)).getCoord();
        Coord toCoord = ((Activity) plan.getPlanElements().get(2)).getCoord();
        Zone fromZone = this.zones.findZone(fromCoord);
        Zone toZone = this.zones.findZone(toCoord);
        if (fromZone != null && toZone != null) {
            if (airportZones.contains(fromZone.getId()) || airportZones.contains(toZone.getId())) {
                numberOfClones = 0.0;
            }
            if (String.valueOf(fromZone.getAttribute("kt_name")).equals("ZH") && String.valueOf(toZone.getAttribute("kt_name")).equals("GE")) {
                numberOfClones = -1.0;
            }
            if (String.valueOf(fromZone.getAttribute("kt_name")).equals("GE") && String.valueOf(toZone.getAttribute("kt_name")).equals("ZH")) {
                numberOfClones = -1.0;
            }
            if (String.valueOf(fromZone.getAttribute("kt_name")).equals("GR") || String.valueOf(toZone.getAttribute("kt_name")).equals("GR")) {
                numberOfClones = 6.0;
            }

            if (String.valueOf(fromZone.getAttribute("kt_name")).equals("NW") || String.valueOf(toZone.getAttribute("kt_name")).equals("GR")) {
                numberOfClones = 4.0;
            }
            if (specialZones.contains(fromZone.getId()) || specialZones.contains(toZone.getId())) {
                numberOfClones = 9.0;
            }
        }
        return numberOfClones;


    }
}
