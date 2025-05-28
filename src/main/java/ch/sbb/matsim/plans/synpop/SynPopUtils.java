package ch.sbb.matsim.plans.synpop;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.households.Household;

public class SynPopUtils {

    public static void setAttribute(Person person, String attributeName, Object attribute) {
        person.getAttributes().putAttribute(attributeName, attribute);
    }

    public static void linkHouseholdsAndPopulation(Scenario scenario) {
        for (Person person : scenario.getPopulation().getPersons().values()) {
            var householdId = Id.create((String) person.getAttributes().getAttribute(SynpopConstants.HOUSEHOLD_ID), Household.class);
            var household = scenario.getHouseholds().getHouseholds().get(householdId);
            person.getAttributes().putAttribute(SynpopConstants.HOUSEHOLD, household);
            household.addMemberId(person.getId());
            person.getAttributes().putAttribute(Variables.ZONE_ID,
                    household.getAttributes().getAttribute(Variables.ZONE_ID));
        }
    }

    public static void linkZonesToHouseHoldsAndPersons(Scenario scenario) {
        Zones zones = (Zones) scenario.getScenarioElement(Variables.ZONES);
        for (Household household : scenario.getHouseholds().getHouseholds().values()) {
            var zoneId = Id.create((String) household.getAttributes().getAttribute(SynpopConstants.ZONE_ID), Zone.class);
            var zone = zones.getZone(zoneId);
            household.getAttributes().putAttribute(SynpopConstants.ZONE, zone);
        }
        for (Person person : scenario.getPopulation().getPersons().values()) {
            var zoneId = Id.create((String) person.getAttributes().getAttribute(SynpopConstants.ZONE_ID), Zone.class);
            var zone = zones.getZone(zoneId);
            person.getAttributes().putAttribute(SynpopConstants.ZONE, zone);
        }
    }

    public static void calculateHouseHoldCompositions(Scenario scenario) {
        for (Household household : scenario.getHouseholds().getHouseholds().values()) {
            Integer numberOfAdults = 0;
            Integer numberOfAdultsAbove24 = 0;
            Integer numberOfChildren = 0;
            Integer numberOfChildrenUnder24 = 0;
            for (Id<Person> memberId : household.getMemberIds()) {
                Person person = scenario.getPopulation().getPersons().get(memberId);
                if (person.getAttributes().getAttribute(SynpopConstants.AGE) != null) {
                    int age = (int) person.getAttributes().getAttribute(SynpopConstants.AGE);
                    if (age >= 18) {
                        numberOfAdults++;
                    } else {
                        numberOfChildren++;
                    }
                    if (age >= 24) {
                        numberOfAdultsAbove24++;
                    } else {
                        numberOfChildrenUnder24++;
                    }
                }
            }
            //default: no couple
            Integer coupleType = 0;
            if (numberOfAdultsAbove24 >= 2 && numberOfAdultsAbove24 <= 3) {
                if (numberOfChildrenUnder24 > 0) {
                    coupleType = 1; // couple with children
                } else {
                    coupleType = 2; // couple without children
                }
            }
            household.getAttributes().putAttribute(SynpopConstants.NB_OF_KIDS, numberOfAdults);
            household.getAttributes().putAttribute(SynpopConstants.NB_OF_ADULTS, numberOfChildren);
            household.getAttributes().putAttribute(SynpopConstants.HAS_CHILD_IN_HH, numberOfChildren > 0);
            household.getAttributes().putAttribute(SynpopConstants.COUPLE_TYPE, coupleType);
        }

    }


}
