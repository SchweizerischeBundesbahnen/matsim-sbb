package ch.sbb.matsim.plans.synpop;

import org.matsim.api.core.v01.population.Person;

public class SynPopUtils {

    public static void setAttribute(Person person, String attributeName, Object attribute) {
        person.getAttributes().putAttribute(attributeName, attribute);
    }
}
