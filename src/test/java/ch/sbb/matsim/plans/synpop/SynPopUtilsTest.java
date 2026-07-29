package ch.sbb.matsim.plans.synpop;

import ch.sbb.matsim.plans.constants.SynpopConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.households.Household;
import org.matsim.households.HouseholdImpl;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SynPopUtilsTest {

    private Scenario scenario;

    @BeforeEach
    void setUp() {
        scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
    }

    @Test
    void setCarAvailableForPopulation_assigns15PercentOfAllStudentsWhenEnoughEligibleStudentsExist() {
        Set<Id<Person>> eligibleStudentIds = new HashSet<>();

        for (int i = 0; i < 10; i++) {
            Id<Person> id = addStudent(i, 1);
            eligibleStudentIds.add(id);
        }
        for (int i = 10; i < 20; i++) {
            addStudent(i, 0);
        }

        SynPopUtils.setCarAvailableForPopulation(scenario);

        long studentsWithCarAvailable = scenario.getPopulation().getPersons().values().stream()
                .filter(this::isStudent)
                .filter(this::hasCarAvailable)
                .count();

        assertEquals(4, studentsWithCarAvailable, "20% von 20 Studierenden sollten ein verfuegbares Auto haben.");

        scenario.getPopulation().getPersons().values().stream()
                .filter(this::hasCarAvailable)
                .forEach(person -> assertTrue(eligibleStudentIds.contains(person.getId()),
                        "Autoverfuegbarkeit darf nur bei Studierenden mit Auto im Haushalt gesetzt werden."));
    }

    @Test
    void setCarAvailableForPopulation_capsAtEligibleStudentsWhenNotEnoughHouseholdCarsExist() {
        for (int i = 0; i < 2; i++) {
            addStudent(i, 1);
        }
        for (int i = 2; i < 20; i++) {
            addStudent(i, 0);
        }

        SynPopUtils.setCarAvailableForPopulation(scenario);

        long studentsWithCarAvailable = scenario.getPopulation().getPersons().values().stream()
                .filter(this::isStudent)
                .filter(this::hasCarAvailable)
                .count();

        assertEquals(2, studentsWithCarAvailable,
                "Wenn nur 2 Studierende in Haushalten mit Auto leben, koennen maximal 2 verfuegbar sein.");
    }

    private Id<Person> addStudent(int index, int numberOfCarsInHousehold) {
        Id<Household> householdId = Id.create("hh_" + index, Household.class);
        Household household = new HouseholdImpl(householdId);
        household.getAttributes().putAttribute(SynpopConstants.NB_OF_CARS, numberOfCarsInHousehold);
        scenario.getHouseholds().getHouseholds().put(householdId, household);

        Person student = scenario.getPopulation().getFactory().createPerson(Id.createPersonId("student_" + index));
        student.getAttributes().putAttribute(SynpopConstants.CURRENT_EDU, 4);
        student.getAttributes().putAttribute(SynpopConstants.HOUSEHOLD, household);
        scenario.getPopulation().addPerson(student);

        return student.getId();
    }

    private boolean hasCarAvailable(Person person) {
        return Boolean.TRUE.equals(person.getAttributes().getAttribute(SynpopConstants.CAR_AVAILABLE));
    }

    private boolean isStudent(Person person) {
        return ((Integer) person.getAttributes().getAttribute(SynpopConstants.CURRENT_EDU)) == 4;
    }
}

