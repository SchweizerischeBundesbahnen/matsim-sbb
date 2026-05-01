package ch.sbb.matsim.projects.synpop;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.csv.CSVWriter;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class SingleTripAgentToCSVConverter {

    public static final String FROM_ACTIVITY_TYPE = "from_activity_type";
    public static final String FROM_ACTIVITY_X = "from_activity_x";
    public static final String FROM_ACTIVITY_Y = "from_activity_y";
    public static final String FROM_ACTIVITY_END_TIME = "from_activity_end_time";
    public static final String LEG_MODE = "leg_mode";
    public static final String TO_ACTIVITY_TYPE = "to_activity_type";
    public static final String TO_ACTIVITY_X = "to_activity_x";
    public static final String TO_ACTIVITY_Y = "to_activity_y";
    public static final String DIRECTION = Variables.MOBiTripAttributes.DIRECTION;
    public static final String TRIP_PURPOSE = Variables.MOBiTripAttributes.PURPOSE;
    public static final List<String> COLUMNS = List.of(Variables.PERSONID, Variables.SUBPOPULATION, FROM_ACTIVITY_TYPE, FROM_ACTIVITY_X, FROM_ACTIVITY_Y, FROM_ACTIVITY_END_TIME, LEG_MODE, TO_ACTIVITY_TYPE, TO_ACTIVITY_X, TO_ACTIVITY_Y, DIRECTION, TRIP_PURPOSE);

    public static void main(String[] args) throws IOException {
        String inputPlansFile = args[0];
        String outputCSVFile = args[1];
        String subpopulationToSet = "freight_road";

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(inputPlansFile);
        Population population = scenario.getPopulation();


        writeSingleTripsAsCSV(outputCSVFile, population, subpopulationToSet);

    }

    public static void writeSingleTripsAsCSV(String outputCSVFile, Population population) throws IOException {
        writeSingleTripsAsCSV(outputCSVFile, population, "");

    }

    public static void writeSingleTripsAsCSV(String outputCSVFile, Population population, String subpulationToSet) throws IOException {
        try (CSVWriter writer = new CSVWriter(COLUMNS, outputCSVFile)) {
            for (Person person : population.getPersons().values()) {

                Plan plan = person.getSelectedPlan();
                if (plan.getPlanElements().size() != 3) {
                    throw new RuntimeException("Some plans have more or less than three plan elements. This code is not designed to transform them. Agent: " + person.getId());
                }
                writer.set(Variables.PERSONID, person.getId().toString());
                String subpopulation = PopulationUtils.getSubpopulation(person) != null ? PopulationUtils.getSubpopulation(person) : subpulationToSet;
                writer.set(Variables.SUBPOPULATION, subpopulation);
                Activity start = (Activity) plan.getPlanElements().get(0);
                Leg leg = (Leg) plan.getPlanElements().get(1);
                Activity end = (Activity) plan.getPlanElements().get(2);
                writer.set(FROM_ACTIVITY_TYPE, start.getType());
                writer.set(FROM_ACTIVITY_END_TIME, String.valueOf((int) start.getEndTime().seconds()));
                writer.set(FROM_ACTIVITY_X, String.valueOf(start.getCoord().getX()));
                writer.set(FROM_ACTIVITY_Y, String.valueOf(start.getCoord().getY()));
                writer.set(LEG_MODE, leg.getMode());
                writer.set(TO_ACTIVITY_TYPE, end.getType());
                writer.set(TO_ACTIVITY_X, String.valueOf(end.getCoord().getX()));
                writer.set(TO_ACTIVITY_Y, String.valueOf(end.getCoord().getY()));
                Object purpose = start.getAttributes().getAttribute(TRIP_PURPOSE);
                writer.set(TRIP_PURPOSE, purpose == null ? "" : purpose.toString());
                writer.writeRow();
            }
        }
    }

    public static void readCSVAndGetPlans(String inputCSVFile, Population population) throws IOException {
        try (CSVReader reader = new CSVReader(inputCSVFile, ";")) {
            var nextLine = reader.readLine();
            while (nextLine != null) {
                String personId = nextLine.get(Variables.PERSONID);
                String subpopulation = nextLine.get(Variables.SUBPOPULATION);
                String fromActivityType = nextLine.get(FROM_ACTIVITY_TYPE);
                double fromActivityX = Double.parseDouble(nextLine.get(FROM_ACTIVITY_X));
                double fromActivityY = Double.parseDouble(nextLine.get(FROM_ACTIVITY_Y));
                double fromActivityEndTime = Double.parseDouble(nextLine.get(FROM_ACTIVITY_END_TIME));
                String legMode = nextLine.get(LEG_MODE);
                String toActivityType = nextLine.get(TO_ACTIVITY_TYPE);
                double toActivityX = Double.parseDouble(nextLine.get(TO_ACTIVITY_X));
                double toActivityY = Double.parseDouble(nextLine.get(TO_ACTIVITY_Y));

                Person person = population.getFactory().createPerson(Id.createPersonId(personId));
                if (!subpopulation.isEmpty()) {
                    PopulationUtils.putSubpopulation(person, subpopulation);
                }
                population.addPerson(person);

                Plan plan = population.getFactory().createPlan();
                Activity fromActivity = population.getFactory().createActivityFromCoord(fromActivityType, new Coord(fromActivityX, fromActivityY));
                fromActivity.setEndTime(fromActivityEndTime);
                Leg leg = population.getFactory().createLeg(legMode);
                Activity toActivity = population.getFactory().createActivityFromCoord(toActivityType, new Coord(toActivityX, toActivityY));
                plan.addActivity(fromActivity);
                plan.addLeg(leg);
                plan.addActivity(toActivity);
                person.addPlan(plan);
                nextLine = reader.readLine();
            }
        }
    }
}
