package ch.sbb.matsim.preparation;

import ch.sbb.matsim.analysis.LocateAct;
import ch.sbb.matsim.csv.CSVWriter;
import org.apache.log4j.Appender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * @author jlie/pmanser / SBB
 *
 * assigns the Raumtyp to each person as a custom attribute. The categorization is based on the shape file
 * containing the UVEK-zones
 *
 */

// TODO: we should merge the new custom attribute directly into the Senozon population attributes and not write as a separate attribute file.
// Doing so, no more extra step is necessary after this process.

public class RaumtypPerPerson {

    public static String RAUMTYP = "raumtyp";
    // pmanser: I'm not sure if we actually need Typ 4, since this type just uses the standard scoring parameters.
    public static String DEFAULT_RAUMTYP = "4";

    public static void main(final String[] args) {
        final String planFile = args[0];
        final String shapeFile = args[1];
        final String outputLog = args[2];
        final String outputAttributes = args[3];

        Logger log = Logger.getLogger(RaumtypPerPerson.class);
        int nbUndefined = 0;
        int nbNotHomeType = 0;
        String notDefinedLog = "\n";
        try {
            Appender fileAppender = new FileAppender(new SimpleLayout(), outputLog);
            log.addAppender(fileAppender);
        } catch (IOException e) {
            log.info("no logging to file " + e.toString());
        }

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(planFile);

        LocateAct locAct = new LocateAct(shapeFile, "GT9");

        for (Person person : scenario.getPopulation().getPersons().values()) {
            Plan firstPlan = person.getPlans().get(0);
            PlanElement firstPlanElement = firstPlan.getPlanElements().get(0);
            if (firstPlanElement instanceof Activity) {
                String raumTyp;
                String type = ((Activity) firstPlanElement).getType();
                if (!type.equals("home")) {
                    raumTyp = DEFAULT_RAUMTYP;
                    log.info("first plan element of person " + person.getId().toString() +
                            " is not of type home");
                    nbNotHomeType += 1;
                } else {
                    Coord coord = ((Activity) firstPlanElement).getCoord();
                    String raumTyp9 = locAct.getNearestZoneAttribute(coord, 200.0);
                    if (raumTyp9.equals(LocateAct.UNDEFINED)) {
                        log.info("no zone defined for person " + person.getId().toString());
                        List<String> l = Arrays.asList(person.getId().toString(), String.valueOf(coord.getX()), String.valueOf(coord.getY()));
                        notDefinedLog += String.join(";", l) + "\n";
                        nbUndefined += 1;
                        raumTyp = DEFAULT_RAUMTYP;
                    } else {
                        raumTyp = raumTyp9.substring(0, 1);
                    }
                }
                scenario.getPopulation().getPersonAttributes().putAttribute(person.getId().toString(), RAUMTYP, raumTyp);
            } else
                throw new IllegalStateException("first planelement of person " +
                        person.getId().toString() + " cannot be not an activity");
        }
        new ObjectAttributesXmlWriter(scenario.getPopulation().getPersonAttributes()).writeFile(outputAttributes);
        log.info(notDefinedLog);
        log.info("nb persons with first activity not of type home " + nbNotHomeType);
        log.info("nb persons with undefined zone " + nbUndefined);
    }
}
