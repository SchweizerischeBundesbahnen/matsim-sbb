package ch.sbb.matsim.utils;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.ActivityImpl;
import org.matsim.core.population.PopulationReaderMatsimV5;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;

import ch.sbb.matsim.analysis.LocateAct;

public class ZonePerPerson {
    public static void main(final String[] args) {
        Logger log = Logger.getLogger(ZonePerPerson.class);
        final Config config = ConfigUtils.createConfig();
        final String planFile = args[0];
        final String shapeFile = args[1];
        final String attributeFileOut = args[2];
        config.plans().setInputFile(planFile);

        LocateAct locAct = new LocateAct(shapeFile, "GMDNR");

        Scenario scenario = ScenarioUtils.createScenario(config);

        new PopulationReaderMatsimV5(scenario).readFile(config.plans().getInputFile());

        for (Person person : scenario.getPopulation().getPersons().values()) {
            Plan firstPlan = person.getPlans().get(0);
            PlanElement firstPlanElement = firstPlan.getPlanElements().get(0);
            if (firstPlanElement instanceof ActivityImpl) {
                String type = ((ActivityImpl) firstPlanElement).getType();
                if (!type.equals("home")) { // hm, ... it seems that home is not defined as static string
                    log.info("first plan element of person " + person.getId().toString() +
                            " is not of type home");
                }
                Coord coord = ((ActivityImpl) firstPlanElement).getCoord();
                String zone = locAct.getNearestZoneAttribute(coord);
                if (zone.equals("undefined"))
                    log.info("no zone defined for person " + person.getId().toString());
                scenario.getPopulation().getPersonAttributes().putAttribute(person.getId().toString(), "zone", zone);
            } else
                throw new IllegalStateException("first planelement of person " +
                        person.getId().toString() + " cannot be not an activity");
        }
        new ObjectAttributesXmlWriter(scenario.getPopulation().getPersonAttributes()).writeFile(attributeFileOut);
    }
}
