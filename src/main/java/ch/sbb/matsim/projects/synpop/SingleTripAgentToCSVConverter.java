package ch.sbb.matsim.projects.synpop;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVWriter;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.IOException;
import java.util.List;

public class SingleTripAgentToCSVConverter {

    public static void main(String[] args) throws IOException {
        String inputPlansFile = args[0];
        String outputCSVFile = args[1];

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(inputPlansFile);
        String tripPurpose = Variables.MOBiTripAttributes.PURPOSE;
        String direction = Variables.MOBiTripAttributes.DIRECTION;
        String toActivityY = "to_activity_y";
        String toActivityX = "to_activity_x";
        String toActivityType = "to_activity_type";
        String legMode = "leg_mode";
        String fromActivityEndTime = "from_activity_end_time";
        String fromActivityY = "from_activity_y";
        String fromActivityX = "from_activity_x";
        String fromActivityType = "from_activity_type";
        List<String> columns = List.of(Variables.PERSONID, Variables.SUBPOPULATION, fromActivityType, fromActivityX, fromActivityY, fromActivityEndTime, legMode, toActivityType, toActivityX, toActivityY, direction, tripPurpose);

        try (CSVWriter writer = new CSVWriter(columns, outputCSVFile)) {
            for (Person person : scenario.getPopulation().getPersons().values()) {
                Plan plan = person.getSelectedPlan();
                if (plan.getPlanElements().size() != 3) {
                    throw new RuntimeException("Some plans have more or less than three plan elements. This code is not designed to transform them. Agent: " + person.getId());
                }
                writer.set(Variables.PERSONID, person.getId().toString());
                writer.set(Variables.SUBPOPULATION, PopulationUtils.getSubpopulation(person));
                Activity start = (Activity) plan.getPlanElements().get(0);
                Leg leg = (Leg) plan.getPlanElements().get(1);
                Activity end = (Activity) plan.getPlanElements().get(2);
                writer.set(fromActivityType, start.getType());
                writer.set(fromActivityEndTime, String.valueOf((int) start.getEndTime().seconds()));
                writer.set(fromActivityX, String.valueOf(start.getCoord().getX()));
                writer.set(fromActivityY, String.valueOf(start.getCoord().getY()));
                writer.set(legMode, leg.getMode());
                writer.set(toActivityType, end.getType());
                writer.set(toActivityX, String.valueOf(end.getCoord().getX()));
                writer.set(toActivityY, String.valueOf(end.getCoord().getY()));
                Object purpose = start.getAttributes().getAttribute(tripPurpose);
                writer.set(tripPurpose, purpose == null ? "" : purpose.toString());
                writer.writeRow();
            }
        }

    }
}
