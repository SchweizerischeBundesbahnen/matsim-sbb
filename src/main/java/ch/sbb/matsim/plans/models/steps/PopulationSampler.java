package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.preparation.slicer.PopulationSlicerByAttribute;
import com.google.inject.Inject;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.households.Household;

import java.util.function.Predicate;

public class PopulationSampler implements ModelStep {

    private final double sampleSize;
    private final Scenario scenario;
    private final int lowerAgeBound = 6;


    @Inject
    public PopulationSampler(Scenario scenario, MOBiPlansConfig moBiPlansConfig) {
        this.scenario = scenario;
        this.sampleSize = moBiPlansConfig.models().sampleSize();
    }

    @Override
    public String getName() {
        return MOBiPlansRunner.POPULATION_SAMPLING;
    }

    @Override
    public void run() {
        int maximumSlice = (int) Math.ceil(200 * sampleSize);
        int slice = 0;
        for (Household household : scenario.getHouseholds().getHouseholds().values()) {
            int householdSlice = slice % 200;
            household.getMemberIds().stream().forEach(
                    personId ->
                            scenario.getPopulation().getPersons().get(personId).getAttributes().putAttribute(PopulationSlicerByAttribute.SLICE, householdSlice));
            household.getAttributes().putAttribute(PopulationSlicerByAttribute.SLICE, householdSlice);
            slice++;
        }
        scenario.getPopulation().getPersons().values().stream().forEach(person -> {
            int age = ((Number) person.getAttributes().getAttribute("age")).intValue();
            boolean active = true;
            if (age < lowerAgeBound || (int) person.getAttributes().getAttribute(PopulationSlicerByAttribute.SLICE) >= maximumSlice) {
              active = false;
            }
            person.getAttributes().putAttribute(SynpopConstants.ACTIVE_IN_MODEL, active);
        });
    }

    public static class ActivePersonPredicate implements Predicate<Person>{
        @Override
        public boolean test(Person person) {
            return (boolean) person.getAttributes().getAttribute(SynpopConstants.ACTIVE_IN_MODEL);
        }
    }
}

