package ch.sbb.matsim.projects.basel;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;

import static ch.sbb.matsim.utils.ScalePlans.scalePopulation;

public class ScaleBaselCBRail {

    public static void main(String[] args) {

        String inputPlans = args[0];
        String outputPlans = args[1];
        Coord baselCenter = new Coord(2611283, 1267071);
        int radius = 15000;
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Scenario scenario2 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Scenario scenario3 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(inputPlans);
        final Population population = scenario.getPopulation();
        final Population filteredPopulation = scenario2.getPopulation();
        final Population outpopulation = scenario3.getPopulation();
        //scale only agents that have at least one activity somewhere near basel
        for (Person p : population.getPersons().values()) {
            if (p.getId().toString().contains("rail_GG_2040_uebrige")) {
                Activity start = (Activity) p.getSelectedPlan().getPlanElements().get(0);
                Activity end = (Activity) p.getSelectedPlan().getPlanElements().get(2);
                if ((CoordUtils.calcEuclideanDistance(start.getCoord(), baselCenter) < radius) || (CoordUtils.calcEuclideanDistance(end.getCoord(), baselCenter) < radius)) {
                    filteredPopulation.addPerson(p);
                }
            }
        }
        int desiredPersons = (int) Math.ceil(filteredPopulation.getPersons().size() * 1.15);
        System.out.println("Agents filtered" + filteredPopulation.getPersons().size());
        System.out.println("Base pop size" + population.getPersons().size());

        scalePopulation(desiredPersons, filteredPopulation, outpopulation);
        population.getPersons().values().stream().filter(p -> !outpopulation.getPersons().containsKey(p.getId())).forEach(outpopulation::addPerson);
        new PopulationWriter(outpopulation).write(outputPlans);
        System.out.println("New pop size" + outpopulation.getPersons().size());


    }
}
