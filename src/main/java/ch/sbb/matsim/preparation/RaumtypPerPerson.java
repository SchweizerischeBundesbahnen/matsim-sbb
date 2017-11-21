package ch.sbb.matsim.preparation;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Appender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
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
import ch.sbb.matsim.csv.CSVReader;

public class RaumtypPerPerson {

    public static String GEMEINDE_BFSNR = "gemeinde_bfsnr";
    public static String GEMEINDETYP = "gemeindetyp2000_9";
    public static String RAUMTYP = "raumtyp";
    public static String DEFALUT_RAUMTYP = "4";


    public static void main(final String[] args) {
        final Config config = ConfigUtils.createConfig();
        final String planFile = args[0];
        final String shapeFile = args[1];
        final String attributeFileOut = args[2];
        final String pathLog = args[3];
        final String pathBFSNrToGemeindetyp = args[4];


        Logger log = Logger.getLogger(RaumtypPerPerson.class);
        log.info("start");
        int nbUndefined = 0;
        int nbNotHomeType = 0;
        String notDefinedLog = "\n";
        try {
            Appender fileAppender = new FileAppender(new SimpleLayout(), pathLog);
            log.addAppender(fileAppender);
        } catch (IOException e) {
            log.info("no logging to file " + e.toString());
        }

        config.plans().setInputFile(planFile);

        LocateAct locAct = new LocateAct(shapeFile, "GMDNR");

        Scenario scenario = ScenarioUtils.createScenario(config);

        CSVReader csvReader = new CSVReader(new String[] { GEMEINDE_BFSNR, GEMEINDETYP, RAUMTYP });
        csvReader.read(pathBFSNrToGemeindetyp, ";");

        Map<String, String> raumtypProGemeinde = new HashMap<>();
        for (Map<String, String> entry : csvReader.data) {
            raumtypProGemeinde.put(entry.get(GEMEINDE_BFSNR), entry.get(RAUMTYP));
        }
        new PopulationReaderMatsimV5(scenario).readFile(config.plans().getInputFile());

        for (Person person : scenario.getPopulation().getPersons().values()) {
            Plan firstPlan = person.getPlans().get(0);
            PlanElement firstPlanElement = firstPlan.getPlanElements().get(0);
            if (firstPlanElement instanceof ActivityImpl) {
                String raumTyp = "";
                String type = ((ActivityImpl) firstPlanElement).getType();
                if (!type.equals("home")) {
                    raumTyp = DEFALUT_RAUMTYP;
                    log.info("first plan element of person " + person.getId().toString() +
                            " is not of type home");
                    nbNotHomeType += 1;
                } else {
                    Coord coord = ((ActivityImpl) firstPlanElement).getCoord();
                    String gemeindeNr = locAct.getNearestZoneAttribute(coord, 200.0);
                    if (gemeindeNr.equals(LocateAct.UNDEFINED)) {
                        log.info("no zone defined for person " + person.getId().toString());
                        List<String> l = Arrays.asList(person.getId().toString(), String.valueOf(coord.getX()), String.valueOf(coord.getY()));
                        notDefinedLog += String.join(";", l) + "\n";
                        nbUndefined += 1;
                        raumTyp = DEFALUT_RAUMTYP;
                    } else {
                        raumTyp = raumtypProGemeinde.get(gemeindeNr);
                    }
                    if (raumTyp == null) {
                        // it seems that even if shape-file and bfs-excel are from the same year, there are differences!
                        log.info("raumTyp == null. person: " + person.getId().toString() + " gemeindenr: " + gemeindeNr);
                        List<String> l = Arrays.asList(person.getId().toString(), String.valueOf(coord.getX()), String.valueOf(coord.getY()));
                        notDefinedLog += String.join(";", l) + "\n";
                        nbUndefined += 1;
                        raumTyp = DEFALUT_RAUMTYP;
                    }
                }
                scenario.getPopulation().getPersonAttributes().putAttribute(person.getId().toString(), RAUMTYP, raumTyp);
            } else
                throw new IllegalStateException("first planelement of person " +
                        person.getId().toString() + " cannot be not an activity");
        }
        new ObjectAttributesXmlWriter(scenario.getPopulation().getPersonAttributes()).writeFile(attributeFileOut);
        log.info("agents with undefined gemeinde:");
        log.info(notDefinedLog);
        log.info("nb persons with first activity not of type home " + nbNotHomeType);
        log.info("nb persons with undefined zone " + nbUndefined);
    }
}
