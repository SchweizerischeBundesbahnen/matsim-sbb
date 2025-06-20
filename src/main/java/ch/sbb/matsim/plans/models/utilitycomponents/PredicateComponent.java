package ch.sbb.matsim.plans.models.utilitycomponents;

import ch.sbb.matsim.plans.models.resolver.AttributeResolver;
import ch.sbb.matsim.plans.models.resolver.NestedAttributeResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.utils.objectattributes.attributable.Attributable;

import java.util.Map;
import java.util.function.Predicate;

public class PredicateComponent implements UtilityComponent {

    private static final Logger log = LogManager.getLogger(ShadowUtility.class);

    private final Map<String, Predicate<Object>> conditions;
    private final double beta;
    private final AttributeResolver resolver;

    public PredicateComponent(Map<String, Predicate<Object>> conditions, double beta) {
        this(conditions, beta, new NestedAttributeResolver());
    }

    public PredicateComponent(Map<String, Predicate<Object>> conditions, double beta, AttributeResolver resolver) {
        this.conditions = conditions;
        this.beta = beta;
        this.resolver = resolver;
    }

    @Override
    public double calculate(Attributable attributes) {
        for (Map.Entry<String, Predicate<Object>> entry : conditions.entrySet()) {
            Object value = resolver.resolve(attributes, entry.getKey());
            if (!entry.getValue().test(value)) {
                return 0.0;
            }
        }
        return beta;
    }
}
