package ch.sbb.matsim.projects.valais2050;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.preparation.slicer.PopulationSlicerByAttribute;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.contrib.common.util.WeightedRandomSelection;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.Random;

public class GenerateAdditionalTourists {
    private static final int ORIGIN_RADIUS = 1000;
    private static final int DESTINATION_RADIUS = 100;
    private final Random random = MatsimRandom.getRandom();
    private final WeightedRandomSelection<Coord> originSelector = new WeightedRandomSelection<>(random);
    private final Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
    private int agentNo = 0;

    public GenerateAdditionalTourists(String inputPopulation) {
        new PopulationReader(scenario).readFile(inputPopulation);
    }

    public static void main(String[] args) {
        GenerateAdditionalTourists additionalTourists = new GenerateAdditionalTourists("\\\\wsbbrz0283\\mobi\\40_Projekte\\20250124_Unterwallis_2050\\sim\\ref\\plans.xml.gz");
        additionalTourists.setWeights();
        additionalTourists.addTourists(new Coord(2588630.147, 1114324.669), 700); //Haute-Nendaz
        additionalTourists.addTourists(new Coord(2584119.065, 1110414.252), 100); //La Tzoumaz, Telecab
        additionalTourists.addTourists(new Coord(2584119.065, 1110414.252), 300);// Iserables
        additionalTourists.addTourists(new Coord(2579102.308, 1116877.334), 200);// Ovronnaz
        additionalTourists.writePopulation("C:\\devsbb\\tmp\\plans_with_additional_tourists.xml.gz");
    }

    private void writePopulation(String ouputFile) {
        new PopulationWriter(scenario.getPopulation()).write(ouputFile);
    }

    private void addTourists(Coord destinationCoord, int number) {
        PopulationFactory populationFactory = scenario.getPopulation().getFactory();
        for (int i = 0; i < number; i++) {
            boolean backward = i % 2 == 0;
            Coord tripStartCoord = backward ? shuffleCoord(destinationCoord, DESTINATION_RADIUS) : shuffleCoord(originSelector.select(), ORIGIN_RADIUS);
            Coord tripEndCoord = backward ? shuffleCoord(originSelector.select(), ORIGIN_RADIUS) : shuffleCoord(destinationCoord, DESTINATION_RADIUS);
            double startTime = 6 * 3600 + random.nextInt(8 * 3600);
            var personId = Id.createPersonId("vs_tourist_" + agentNo++);
            var person = populationFactory.createPerson(personId);
            scenario.getPopulation().addPerson(person);
            Plan plan = populationFactory.createPlan();
            person.addPlan(plan);
            Activity start = populationFactory.createActivityFromCoord("Exogenous", tripStartCoord);
            start.setEndTime(startTime);
            plan.addActivity(start);
            plan.addLeg(populationFactory.createLeg(SBBModes.PT));
            plan.addActivity(populationFactory.createActivityFromCoord("Exogeneous", tripEndCoord));
            PopulationUtils.putSubpopulation(person, Variables.TOURISM_RAIL);
            person.getAttributes().putAttribute(PopulationSlicerByAttribute.SLICE, agentNo % 200);
        }
    }

    private Coord shuffleCoord(Coord coord, int radius) {
        double x = coord.getX() - radius + random.nextInt(2 * radius);
        double y = coord.getY() - radius + random.nextInt(2 * radius);
        return new Coord(x, y);

    }

    private void setWeights() {
        originSelector.add(new Coord(2537875.376, 1152042.001), 0.2); //LS
        originSelector.add(new Coord(2593864.623, 1119533.937), 0.4); //SIO
        originSelector.add(new Coord(2499949.841, 1118489.306), 0.1); //GE
        originSelector.add(new Coord(2572202.646, 1106127.068), 0.2); //MA
        originSelector.add(new Coord(2600050.781, 1199824.101), 0.1); //BN


    }
}
