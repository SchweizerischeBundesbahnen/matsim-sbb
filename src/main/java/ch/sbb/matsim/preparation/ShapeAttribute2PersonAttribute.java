package ch.sbb.matsim.preparation;

import ch.sbb.matsim.analysis.LocateAct;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;

import java.util.Arrays;
import java.util.List;

/**
 * @author jlie/pmanser / SBB
 *
 * assigns the Raumtyp to each person as a custom attribute. The categorization is based on the shape file
 * containing the UVEK-zones
 *
 */


public class ShapeAttribute2PersonAttribute {

    private final static Logger log = Logger.getLogger(ShapeAttribute2PersonAttribute.class);

    public static void main(final String[] args) {
        final String planFile = args[0];
        final String attributeFileIn = args[1];
        final String shapeFile = args[2];
        final String shapeAttribute = args[3];
        final String personAttribute = args[4];
        final String attributeFileOut = args[5];

        int nbUndefined = 0;
        int nbNotHomeType = 0;
        String notDefinedLog = "\n";

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(planFile);
        new ObjectAttributesXmlReader(scenario.getPopulation().getPersonAttributes()).readFile(attributeFileIn);

        LocateAct locAct = new LocateAct(shapeFile, shapeAttribute);

        for (Person person : scenario.getPopulation().getPersons().values()) {

            Plan plan = person.getSelectedPlan();
            PlanElement firstPlanElement = plan.getPlanElements().get(0);

            if (firstPlanElement instanceof Activity) {
                String attribute = null;
                String type = ((Activity) firstPlanElement).getType();
                if (!type.equals("home")) {
                    log.info("first plan element of person " + person.getId().toString() +
                            " is not of type home");
                    nbNotHomeType += 1;
                } else {
                    Coord coord = ((Activity) firstPlanElement).getCoord();
                    String shapeValue = locAct.getZoneAttribute(coord);
                    if (shapeValue.equals(LocateAct.UNDEFINED)) {
                        log.info("no zone defined for person " + person.getId().toString());
                        List<String> l = Arrays.asList(person.getId().toString(), String.valueOf(coord.getX()), String.valueOf(coord.getY()));
                        notDefinedLog += String.join(";", l) + "\n";
                        nbUndefined += 1;
                    } else {
                        attribute = shapeValue;
                    }
                }
                if(attribute != null) {
                    scenario.getPopulation().getPersonAttributes().putAttribute(person.getId().toString(), personAttribute, (int) Double.parseDouble(attribute));
                }
            } else
                throw new IllegalStateException("first planelement of person " +
                        person.getId().toString() + " cannot be not an activity");
        }

        new ObjectAttributesXmlWriter(scenario.getPopulation().getPersonAttributes()).writeFile(attributeFileOut);
        log.info(notDefinedLog);
        log.info("nb persons with first activity not of type home " + nbNotHomeType);
        log.info("nb persons with undefined zone " + nbUndefined);
    }
}
