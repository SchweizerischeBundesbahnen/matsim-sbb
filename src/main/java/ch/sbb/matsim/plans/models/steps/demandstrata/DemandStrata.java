package ch.sbb.matsim.plans.models.steps.demandstrata;

import org.matsim.api.core.v01.population.Person;

import java.util.Objects;
import java.util.function.Predicate;

public class DemandStrata implements Predicate<Person> {

    public enum Type {
        PRIMARY_DESTINATION,
        SECONDARY_DESTINATION,
        ACCESSIBILITY,
        OTHER
    }

    private final String name;
    private final Type type;
    private final Predicate<Person> condition;

    public DemandStrata(String name, Type type, Predicate<Person> condition) {
        this.name = name;
        this.type = type;
        this.condition = condition != null ? condition : _ -> true; // Default to always true if no conditions
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    /**
     * Evaluates if the given person belongs to this stratum.
     */
    @Override
    public boolean test(Person person) {
        return condition.test(person);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DemandStrata that = (DemandStrata) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }
}