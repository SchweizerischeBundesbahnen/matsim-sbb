package ch.sbb.matsim.plans.models.utilitycomponents;

import ch.sbb.matsim.plans.models.resolver.AttributeResolver;
import ch.sbb.matsim.plans.models.resolver.NestedAttributeResolver;
import org.matsim.utils.objectattributes.attributable.Attributable;

import java.util.function.Predicate;

public class PredicateComponent implements UtilityComponent {

    private final Predicate<Object> condition;
    private final double beta;
    private final String attributeName;
    private final AttributeResolver resolver;

    public PredicateComponent(String attributeName, Predicate<Object> condition, double beta) {
        this(attributeName, condition, beta, new NestedAttributeResolver());
    }

    public PredicateComponent(String attributeName, Predicate<Object> condition, double beta, AttributeResolver resolver) {
        this.attributeName = attributeName;
        this.condition = condition;
        this.beta = beta;
        this.resolver = resolver;
    }

    @Override
    public double calculate(Attributable attributes) {
        Object value = attributes.getAttributes().getAttribute(attributeName);
        if (condition.test(value)) {
            return beta;
        } else {
            return 0.0;
        }
    }
}
