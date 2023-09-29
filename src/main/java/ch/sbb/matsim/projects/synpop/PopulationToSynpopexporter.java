package ch.sbb.matsim.projects.synpop;

import ch.sbb.matsim.csv.CSVWriter;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.households.Household;
import org.matsim.households.HouseholdImpl;
import org.matsim.households.Households;
import org.matsim.households.HouseholdsImpl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PopulationToSynpopexporter {

    public static final String ZONE_ID = "zone_id";
    public static final String HOME_COORD = "homeCoord";
    public static final String PERSON_ID = "person_id";
    public static final String HOUSEHOLD_ID = "household_id";
    private final Population population;
    private final Households households;
    private final Set<String> populationAttributes;

    public PopulationToSynpopexporter(Population population) {
        this.population = population;
        this.households = new HouseholdsImpl();
        this.populationAttributes = population.getPersons().values().stream().flatMap(person -> person.getAttributes().getAsMap().keySet().stream()).collect(Collectors.toSet());

    }

    public static void main(String[] args) {
        String inputPopulationFile = args[0];
        String outputPopulationCSVFile = args[1];
        String outputHouseholdCSVFile = args[2];
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(inputPopulationFile);
        PopulationToSynpopexporter exporter = new PopulationToSynpopexporter(scenario.getPopulation());
        exporter.prepareHouseholds();
        exporter.exportPopulation(outputPopulationCSVFile);
        exporter.exportHouseholds(outputHouseholdCSVFile);
    }

    private static Id<Household> getHouseholdId(Person p) {
        Object householdIdCandidate = p.getAttributes().getAttribute(HOUSEHOLD_ID);
        Id<Household> householdId = Id.create(householdIdCandidate != null ? householdIdCandidate.toString() : p.getId().toString(), Household.class);
        return householdId;
    }

    private void exportHouseholds(String outputHouseholdCSVFile) {
        String[] columns = new String[]{HOUSEHOLD_ID, ZONE_ID, "xcoord", "ycoord"};
        try (CSVWriter writer = new CSVWriter(null, columns, outputHouseholdCSVFile)) {

            for (Household household : this.households.getHouseholds().values()) {
                writer.set(HOUSEHOLD_ID, household.getId().toString());
                writer.set(ZONE_ID, household.getAttributes().getAttribute(ZONE_ID).toString());
                Coord homeCoord = (Coord) household.getAttributes().getAttribute(HOME_COORD);
                writer.set("xcoord", String.valueOf(homeCoord.getX()));
                writer.set("ycoord", String.valueOf(homeCoord.getY()));
                writer.writeRow();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void exportPopulation(String outputPopulationCSVFile) {
        List<String> header = new ArrayList<>();
        header.add(PERSON_ID);
        if (!populationAttributes.contains(HOUSEHOLD_ID)) {
            header.add(HOUSEHOLD_ID);
        }
        header.addAll(this.populationAttributes);
        String[] columns = new String[header.size()];
        header.toArray(columns);
        try (CSVWriter writer = new CSVWriter(null, columns, outputPopulationCSVFile)) {
            for (Person p : this.population.getPersons().values()) {
                writer.set(PERSON_ID, p.getId().toString());
                writer.set(HOUSEHOLD_ID, getHouseholdId(p).toString());
                for (String attribute : this.populationAttributes) {
                    if (!attribute.equals(HOUSEHOLD_ID)) {
                        Object value = p.getAttributes().getAttribute(attribute);
                        if (value != null) {
                            writer.set(attribute, value.toString());
                        }
                    }
                }
                writer.writeRow();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void prepareHouseholds() {
        for (Person p : population.getPersons().values()) {
            Coord homeCoord = ((Activity) p.getSelectedPlan().getPlanElements().get(0)).getCoord();
            Id<Household> householdId = getHouseholdId(p);
            HouseholdImpl household = (HouseholdImpl) this.households.getHouseholds().computeIfAbsent(householdId, householdId1 -> new HouseholdImpl(householdId1));
            if (household.getMemberIds() == null) {
                household.setMemberIds(new ArrayList<>());
            }

            household.getMemberIds().add(p.getId());
            household.getAttributes().putAttribute(HOME_COORD, homeCoord);
            household.getAttributes().putAttribute(ZONE_ID, p.getAttributes().getAttribute(ZONE_ID));

        }
    }
}
