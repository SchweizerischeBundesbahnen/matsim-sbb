package ch.sbb.matsim.preparation;

import ch.sbb.matsim.utils.SBBPersonUtils;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import ch.sbb.matsim.zones.ZonesQueryCache;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.Arrays;
import java.util.List;

/**
 * @author pmanser / SBB
 *
 * based on RaumtypPerPerson
 *
 * assigns a shape file attribute to each person as a custom attribute accoring to the agent's home location.
 *
 */


public class ShapeAttribute2PersonAttribute {

    private final static Logger log = Logger.getLogger(ShapeAttribute2PersonAttribute.class);

    public static void main(final String[] args) {
        if (args.length != 5) {
            System.err.println("Wrong number of arguments.");
            return;
        }
        final String planFile = args[0];
        final String shapeFile = args[1];
        final String shapeAttribute = args[2];
        final String personAttribute = args[3];
        final String populationFileOut = args[4];

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(planFile);

        Zones zones = new ZonesQueryCache(ZonesLoader.loadZones("zones", shapeFile, null));

        for (Person person : scenario.getPopulation().getPersons().values()) {

            Activity homeAct = SBBPersonUtils.getHomeActivity(person);

            if (homeAct != null) {
                Coord coord = homeAct.getCoord();
                Zone z = zones.findZone(coord.getX(), coord.getY());
                Object attrVal = z == null ? null : z.getAttribute(shapeAttribute);
                String shapeValue = attrVal == null ? null : attrVal.toString();
                if (shapeValue == null) {
                    log.warn("no zone defined for person " + person.getId().toString());
                    List<String> l = Arrays.asList(person.getId().toString(), String.valueOf(coord.getX()), String.valueOf(coord.getY()));
                    log.info(l);
                } else {
                    person.getAttributes().putAttribute(personAttribute, (int) Double.parseDouble(shapeValue));
                }
            }
        }
        new PopulationWriter(scenario.getPopulation()).write(populationFileOut);
    }
}
