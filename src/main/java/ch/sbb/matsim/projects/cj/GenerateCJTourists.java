package ch.sbb.matsim.projects.cj;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class GenerateCJTourists {


    public static void main(String[] args) {
        String transitScheduleFile = "\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_4.0\\2040\\pt\\BAVAK35\\output\\transitSchedule.xml.gz";

        Map<Id<TransitStopFacility>, Integer> newDemand = Map.of(
                Id.create(2665, TransitStopFacility.class), 200,
                Id.create(1249, TransitStopFacility.class), 40,
                Id.create(2492, TransitStopFacility.class), 50,
                Id.create(1537, TransitStopFacility.class), 40,
                Id.create(1549, TransitStopFacility.class), 10,
                Id.create(2372, TransitStopFacility.class), 50,
                Id.create(2948, TransitStopFacility.class), 100);
        List<Id<TransitStopFacility>> sources = List.of(
                Id.create(1279, TransitStopFacility.class),
                Id.create(2340, TransitStopFacility.class),
                Id.create(2291, TransitStopFacility.class),
                Id.create(2113, TransitStopFacility.class));
        Random random = MatsimRandom.getRandom();
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile("\\\\wsbbrz0283\\mobi\\40_Projekte\\20230706_CJ_Jura_2040\\sim\\ref\\prepared\\plans-notourists.xml.gz");
        new TransitScheduleReader(scenario).readFile(transitScheduleFile);
        int z = 0;
        for (var entry : newDemand.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                scenario.getPopulation().addPerson(generatePerson(entry.getKey(), sources.get(random.nextInt(sources.size())), z, random.nextInt(8 * 3600), random.nextBoolean(), scenario.getPopulation().getFactory(), scenario.getTransitSchedule()));
                z++;
            }
        }
        new PopulationWriter(scenario.getPopulation()).write("c:\\devsbb\\plans.xml.gz");
    }

    private static Person generatePerson(Id<TransitStopFacility> destinationStop, Id<TransitStopFacility> startStop, int z, int time, boolean backwards, PopulationFactory factory, TransitSchedule schedule) {

        Person person = factory.createPerson(Id.createPersonId("tourist_cj_" + z + "_" + startStop.toString() + "_" + destinationStop.toString()));
        PopulationUtils.putSubpopulation(person, Variables.TOURISM_RAIL);
        Plan plan = factory.createPlan();
        person.addPlan(plan);
        Activity fromAct = factory.createActivityFromCoord("cbHome", schedule.getFacilities().get(startStop).getCoord());
        TransitStopFacility transitStopFacility = schedule.getFacilities().get(destinationStop);
        Activity toAct = factory.createActivityFromCoord("cbHome", transitStopFacility.getCoord());
        Leg leg = factory.createLeg(SBBModes.PT);
        int departureTime = 8 * 3600 + time;
        if (backwards) {
            plan.addActivity(toAct);
            toAct.setEndTime(departureTime);
            plan.addLeg(leg);
            plan.addActivity(fromAct);
        } else {
            plan.addActivity(fromAct);
            fromAct.setEndTime(departureTime);
            plan.addLeg(leg);
            plan.addActivity(toAct);
        }

        return person;

    }
}
