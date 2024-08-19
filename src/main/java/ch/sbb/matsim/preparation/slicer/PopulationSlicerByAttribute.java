package ch.sbb.matsim.preparation.slicer;

import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;

import java.util.HashSet;
import java.util.Set;

public class PopulationSlicerByAttribute {

    public static final String SLICE = "slice";


    /**
     * Filters agents by slice number
     *
     * @param population
     * @param sliceNo
     * @param sampleSize
     */
    public static void filterPopulationBySlice(Population population, int sliceNo, double sampleSize) {
        if (population.getPersons().isEmpty()) {
            LogManager.getLogger(PopulationSlicerByAttribute.class).warn("Population is empty.");
            return;
        }
        int maximumNumberOfSlices = 200;
        LogManager.getLogger(PopulationSlicerByAttribute.class).info("Assuming " + maximumNumberOfSlices + " number of slices.");

        int numberOfRequiredSlices = (int) (maximumNumberOfSlices * sampleSize);
        int startSlice = sliceNo * numberOfRequiredSlices;
        int endSlice = startSlice + numberOfRequiredSlices;
        LogManager.getLogger(PopulationSlicerByAttribute.class).warn("Only agents between slice " + startSlice + " and " + endSlice + " will be simulated.");
        Set<Id<Person>> personsToRemove = new HashSet<>();
        for (Person p : population.getPersons().values()) {
            Integer slice = (Integer) p.getAttributes().getAttribute(SLICE);
            if (slice == null) {
                LogManager.getLogger(PopulationSlicerByAttribute.class).warn("Person " + p.getId() + " has no slice attribute. Will not apply any slicing. ");
                return;
            }
            if (slice < startSlice || slice >= endSlice) {
                personsToRemove.add(p.getId());
            }
        }
        personsToRemove.forEach(personId -> population.removePerson(personId));
        if (population.getPersons().isEmpty()) {
            throw new RuntimeException("Resulting population is empty. Probably a non-useful slice has been selected.");
        }
        LogManager.getLogger(PopulationSlicerByAttribute.class).warn("Resulting population has " + population.getPersons().size() + " agents. " + personsToRemove.size() + " agents have been removed from input population.");

    }
}
